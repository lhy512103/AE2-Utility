#!/usr/bin/env python3
"""Publish a validated GitHub release bundle using Python 3.12+ stdlib only.

Usage: PLATFORM_TOKEN=... python platform_publish.py --platform modrinth \
    --bundle bundle --output-receipt modrinth-receipt.json [--receipt existing.json]

The receipt is the immutable binding of platform/project/version/file/hash. Preserve
it as a GitHub release asset even if subsequent workflow steps fail. Never retry
an ambiguous POST automatically: inspect the platform and recover its file ID.

CurseForge's documented Upload API has no file-list endpoint. Public CFWidget
files are checked before first upload; unavailable/malformed data fails closed.
An existing filename/version needs a matching published hash or a manually
verified receipt; otherwise it fails closed. An absent match permits one upload.
CFWidget may cache for one hour and omit files: this is not exactly-once delivery.
Serialize workflow runs and preserve receipts. After any ambiguous upload failure,
manually reconcile CurseForge before rerunning; never invent a receipt.

API references:
https://docs.modrinth.com/api/operations/createversion/
https://docs.modrinth.com/api/operations/modifyversion/
https://docs.modrinth.com/api/operations/getprojectversions/
https://support.curseforge.com/en/support/solutions/articles/9000197321-curseforge-upload-api
https://cfwidget.com/#documentation:about
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import tomllib
from typing import Any
import urllib.error
import urllib.request
import uuid
import zipfile

from release import validate_runtime

MR_BASE = "https://api.modrinth.com/v2"
CF_BASE = "https://minecraft.curseforge.com/api"
PROJECT_IDS = {"modrinth": "kJXRtOkZ", "curseforge": "1521605"}
USER_AGENT = "lhy512103/AE2-Utility-release (github.com/lhy512103/AE2-Utility)"
# Forge 1.20.1 line. The NeoForge 1.21.1 line publishes to the same projects with its own constants.
MINECRAFT_VERSION = "1.20.1"
DESCRIPTOR = "META-INF/mods.toml"
MODRINTH_LOADER = "forge"
CURSEFORGE_GAME_VERSIONS = [MINECRAFT_VERSION, "Forge", "Java 17", "Client", "Server"]
# AE2 is required; JEI is optional on Forge 1.20.1 (EMI is supported too), matching mods.toml.
MODRINTH_DEPENDENCIES = [{"project_id": "XxWD5pD3", "dependency_type": "required"},
                         {"project_id": "u6dRKJwZ", "dependency_type": "optional"}]
CURSEFORGE_RELATIONS = [{"slug": "applied-energistics-2", "projectID": "223794", "type": "requiredDependency"},
                        {"slug": "jei", "projectID": "238222", "type": "optionalDependency"}]
TIMEOUT_SECONDS = 60


class PublishError(Exception):
    """A deliberately sanitized, safe-to-log publication failure."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PublishError(message)


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise PublishError("Cannot read required JSON file.") from None


class Bundle:
    """Read and validate every local input before any API mutation."""

    def __init__(self, directory: Path):
        directory = directory.resolve()
        self.metadata = m = load_json(directory / "metadata.json")
        require(isinstance(m, dict), "metadata.json must be an object.")
        for key in ("tag", "version", "name", "version_type", "minecraft_version",
                    "mod_id", "jar_name", "sha256", "repository",
                    "modrinth_project_id", "curseforge_project_id"):
            require(isinstance(m.get(key), str) and bool(m[key].strip()),
                    "Missing or invalid metadata field: " + key)
        require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,127}", m["version"]) is not None,
                "Unsafe version.")
        require(m["version"] not in (".", ".."), "Unsafe version.")
        require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+/-]{0,199}", m["tag"]) is not None
                and ".." not in m["tag"], "Unsafe tag.")
        require(not any(ord(c) < 32 or ord(c) == 127 for c in m["name"]), "Unsafe name.")
        require(m["repository"].lower() == "lhy512103/ae2-utility", "Unexpected repository.")
        require(m["minecraft_version"] == MINECRAFT_VERSION, "Unexpected Minecraft version.")
        require(m["mod_id"] == "ae2utility", "Unexpected mod ID.")
        require(m["version_type"] in ("release", "beta", "alpha"), "Invalid version_type.")
        require(type(m.get("github_release_id")) is int and m["github_release_id"] > 0,
                "github_release_id must be a positive integer.")
        for platform, project in PROJECT_IDS.items():
            require(m[platform + "_project_id"] == project, "Unexpected " + platform + " project.")
        filename = m["jar_name"]
        require(filename == "ae2utility-" + m["version"] + ".jar"
                and "/" not in filename and "\\" not in filename
                and re.fullmatch(r"[A-Za-z0-9._+-]+\.jar", filename) is not None,
                "Unsafe or unexpected Jar filename.")
        require(re.search(r"(?:^|[-_.])(sources?|javadoc|dev)(?:[-_.]|$)", filename, re.I) is None,
                "Refusing sources/javadoc/dev Jar.")
        require(re.fullmatch(r"[0-9a-fA-F]{64}", m["sha256"]) is not None, "Invalid SHA-256.")
        m["sha256"] = m["sha256"].lower()
        try:
            notes_path = directory / "release-notes.md"
            require(notes_path.resolve().parent == directory, "Release notes escape bundle.")
            self.notes = notes_path.read_text(encoding="utf-8")
            jar_path = directory / filename
            require(jar_path.resolve().parent == directory, "Jar escapes bundle.")
            self.jar = jar_path.read_bytes()
        except (OSError, UnicodeError):
            raise PublishError("Bundle requires readable Jar and release-notes.md.") from None
        require(bool(self.notes.strip()), "GitHub release body must not be empty.")
        require(bool(self.jar), "Jar must not be empty.")
        self.sha256 = hashlib.sha256(self.jar).hexdigest()
        self.sha1 = hashlib.sha1(self.jar).hexdigest()
        require(self.sha256 == m["sha256"], "Jar SHA-256 does not match metadata.")
        try:
            with zipfile.ZipFile(io.BytesIO(self.jar)) as jar:
                require(sum(entry.file_size for entry in jar.infolist()) <= 512 * 1024 * 1024,
                        "Jar uncompressed size exceeds safety limit.")
                require(jar.testzip() is None, "Jar CRC validation failed.")
                descriptor = DESCRIPTOR
                require(jar.namelist().count(descriptor) == 1, "Jar requires one Forge descriptor.")
                require(jar.getinfo(descriptor).file_size <= 1024 * 1024, "Jar descriptor is too large.")
                info = tomllib.loads(jar.read(descriptor).decode("utf-8"))
                validate_runtime(jar, info)
                mods = info.get("mods", [])
                require(isinstance(mods, list), "Invalid Forge mods table.")
                matching = [mod for mod in mods if isinstance(mod, dict) and mod.get("modId") == m["mod_id"]]
                require(len(matching) == 1 and matching[0].get("version") == m["version"],
                        "Jar modId/version does not match metadata.")
                require(any(n.endswith(".class") and not n.startswith("META-INF/") for n in jar.namelist()),
                        "Jar contains no compiled mod classes.")
        except (zipfile.BadZipFile, UnicodeError, ValueError, KeyError, RuntimeError, NotImplementedError):
            raise PublishError("Invalid Jar or Forge descriptor.") from None


def file_id(platform: str, value: Any) -> str | int:
    if platform == "modrinth":
        require(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9]{1,64}", value) is not None,
                "Invalid Modrinth file ID.")
    else:
        require(type(value) is int and value > 0, "CurseForge file_id must be a positive integer.")
    return value


def validate_receipt(platform: str, bundle: Bundle, path: Path | None) -> dict | None:
    if path is None or not path.exists():
        return None
    receipt = load_json(path)
    require(isinstance(receipt, dict), "Receipt must be an object.")
    expected = {"platform": platform, "project_id": PROJECT_IDS[platform],
                "version": bundle.metadata["version"], "sha256": bundle.sha256}
    for key, value in expected.items():
        require(receipt.get(key) == value, "Receipt " + key + " mismatch; binary replacement is forbidden.")
    file_id(platform, receipt.get("file_id"))
    return receipt


def make_receipt(platform: str, bundle: Bundle, identity: Any) -> dict:
    return {"platform": platform, "project_id": PROJECT_IDS[platform],
            "version": bundle.metadata["version"], "file_id": file_id(platform, identity),
            "sha256": bundle.sha256}


def save_receipt(path: Path, receipt: dict) -> None:
    """Atomically replace receipt immediately after an acknowledged mutation."""
    temporary = None
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=".receipt-", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(receipt, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except OSError:
        raise PublishError("Remote write succeeded but receipt persistence failed; recover the remote ID manually before retrying.") from None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Fail closed for ALL redirects; never forward authentication to another URL."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise PublishError("API redirect refused; no credentials were forwarded.")


class Transport:
    def __init__(self):
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, method: str, url: str, headers: dict, body: bytes | None = None) -> Any:
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=TIMEOUT_SECONDS) as response:
                data = response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as error:
            # Only bounded JSON validation messages for 400s are useful diagnostics.
            # Never print URLs, headers, raw response bodies or authentication failures.
            detail = ""
            if error.code == 400:
                try:
                    response = json.loads(error.read(8192))
                    if isinstance(response, dict):
                        message = response.get("errorMessage") or response.get("message")
                        if isinstance(message, str):
                            for key, value in headers.items():
                                if key.lower() in ("authorization", "x-api-token") and value:
                                    message = message.replace(value, "[REDACTED]")
                            detail = ": " + re.sub(r"[\x00-\x1f\x7f]+", " ", message)[:400]
                except (ValueError, UnicodeError, OSError):
                    pass
            raise PublishError("Platform API " + method + " HTTP " + str(error.code) + detail
                               + "; request was not retried.") from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise PublishError("Platform network failure; request was not retried. A write may have succeeded; reconcile before rerunning.") from None
        except (ValueError, UnicodeError):
            raise PublishError("Platform returned invalid JSON; reconcile any write before rerunning.") from None


def multipart(field: str, metadata: dict, bundle: Bundle | None = None) -> tuple[bytes, str]:
    boundary = "ae2utility-" + uuid.uuid4().hex
    parts = [b"--" + boundary.encode(),
             ('Content-Disposition: form-data; name="' + field + '"').encode(),
             b"Content-Type: application/json", b"", json.dumps(metadata, ensure_ascii=False).encode("utf-8")]
    if bundle is not None:
        parts += [b"--" + boundary.encode(),
                  ('Content-Disposition: form-data; name="file"; filename="' + bundle.metadata["jar_name"] + '"').encode(),
                  b"Content-Type: application/java-archive", b"", bundle.jar]
    parts += [b"--" + boundary.encode() + b"--", b""]
    return b"\r\n".join(parts), "multipart/form-data; boundary=" + boundary


class Modrinth:
    platform = "modrinth"

    def __init__(self, transport: Transport, token: str):
        self.transport = transport
        self.headers = {"Authorization": token, "User-Agent": USER_AGENT, "Accept": "application/json"}

    def discover(self, bundle: Bundle) -> str | None:
        versions = self.transport.request("GET", MR_BASE + "/project/" + PROJECT_IDS[self.platform] + "/version", self.headers)
        require(isinstance(versions, list), "Invalid Modrinth version inventory; refusing upload.")
        require(all(isinstance(v, dict) and isinstance(v.get("version_number"), str)
                    and isinstance(v.get("loaders"), list) and isinstance(v.get("game_versions"), list)
                    for v in versions), "Malformed Modrinth version inventory; refusing upload.")
        matches = [v for v in versions if isinstance(v, dict)
                   and v.get("version_number") == bundle.metadata["version"]
                   and MODRINTH_LOADER in v.get("loaders", [])
                   and bundle.metadata["minecraft_version"] in v.get("game_versions", [])]
        require(len(matches) <= 1, "Ambiguous existing Modrinth versions; refusing write.")
        if not matches:
            return None
        version = matches[0]
        require(version.get("project_id") == PROJECT_IDS[self.platform], "Modrinth inventory project mismatch.")
        files = version.get("files", [])
        require(isinstance(files, list) and bool(files), "Existing Modrinth version has no verifiable file.")
        # Verify the main binary, not an arbitrary attachment whose hash happens to match.
        main = [f for f in files if isinstance(f, dict) and f.get("primary") is True]
        if not main:
            main = [f for f in files if isinstance(f, dict) and f.get("filename") == bundle.metadata["jar_name"]]
        require(len(main) == 1, "Existing Modrinth binary is ambiguous.")
        hashes = main[0].get("hashes", {})
        require(isinstance(hashes, dict), "Existing Modrinth binary has invalid hashes.")
        supported = {"sha256": bundle.sha256, "sha1": bundle.sha1}
        present = [(key, value) for key, value in supported.items() if key in hashes]
        require(bool(present) and all(isinstance(hashes[k], str) and hashes[k].lower() == v for k, v in present),
                "Existing Modrinth version has a different or unverifiable binary; refusing replacement.")
        return file_id(self.platform, version.get("id"))

    def update(self, bundle: Bundle, identity: str) -> None:
        metadata = {"changelog": bundle.notes, "name": bundle.metadata["name"],
                    "version_type": bundle.metadata["version_type"]}
        headers = {**self.headers, "Content-Type": "application/json"}
        self.transport.request("PATCH", MR_BASE + "/version/" + identity, headers,
                               json.dumps(metadata, ensure_ascii=False).encode("utf-8"))

    def create(self, bundle: Bundle) -> Any:
        m = bundle.metadata
        metadata = {"project_id": PROJECT_IDS[self.platform], "name": m["name"],
                    "version_number": m["version"], "version_type": m["version_type"],
                    "changelog": bundle.notes, "loaders": [MODRINTH_LOADER],
                    "game_versions": [m["minecraft_version"]], "file_parts": ["file"],
                    "primary_file": "file", "featured": False,
                    "dependencies": MODRINTH_DEPENDENCIES}
        body, content_type = multipart("data", metadata, bundle)
        result = self.transport.request("POST", MR_BASE + "/version", {**self.headers, "Content-Type": content_type}, body)
        require(isinstance(result, dict), "Upload response missing version ID; reconcile before rerunning.")
        return file_id(self.platform, result.get("id"))


class CurseForge:
    platform = "curseforge"

    def __init__(self, transport: Transport, token: str):
        self.transport = transport
        self.headers = {"X-Api-Token": token, "User-Agent": USER_AGENT, "Accept": "application/json"}

    def discover(self, bundle: Bundle) -> int | None:
        # This is a documented public read-only service, not the separately keyed
        # CurseForge Core API. NEVER send the upload token to this host.
        inventory = self.transport.request("GET", "https://api.cfwidget.com/" + PROJECT_IDS[self.platform],
                                           {"User-Agent": USER_AGENT, "Accept": "application/json"})
        require(isinstance(inventory, dict) and inventory.get("id") == int(PROJECT_IDS[self.platform])
                and isinstance(inventory.get("files"), list),
                "CurseForge public inventory unavailable or malformed; refusing upload.")
        files = inventory["files"]
        require(all(isinstance(f, dict) and isinstance(f.get("name"), str)
                    and type(f.get("id")) is int and f["id"] > 0 for f in files),
                "CurseForge public file inventory is incomplete; refusing upload.")
        candidates = [f for f in files if f["name"] == bundle.metadata["jar_name"]
                      or f.get("display") in (bundle.metadata["version"], bundle.metadata["name"],
                                               "v" + bundle.metadata["version"])]
        require(len(candidates) <= 1, "Ambiguous existing CurseForge files; supply a verified receipt.")
        if not candidates:
            return None
        hashes = candidates[0].get("hashes", {})
        require(isinstance(hashes, dict), "Invalid existing CurseForge file hashes.")
        supported = {"sha256": bundle.sha256, "sha1": bundle.sha1}
        present = [(k, v) for k, v in supported.items() if k in hashes]
        require(bool(present), "CurseForge file already exists but public inventory has no verifiable hash. Supply --receipt for the manually verified file (platform/project_id/version/file_id/sha256); do not upload it again.")
        require(all(isinstance(hashes[k], str) and hashes[k].lower() == v for k, v in present),
                "Existing CurseForge binary differs; use a new version, never overwrite it.")
        return file_id(self.platform, candidates[0]["id"])

    def _metadata(self, bundle: Bundle) -> dict:
        return {"changelog": bundle.notes, "changelogType": "markdown",
                "displayName": bundle.metadata["name"], "releaseType": bundle.metadata["version_type"]}

    def update(self, bundle: Bundle, identity: int) -> None:
        metadata = {**self._metadata(bundle), "fileID": identity}
        body, content_type = multipart("metadata", metadata)
        self.transport.request("POST", CF_BASE + "/projects/" + PROJECT_IDS[self.platform] + "/update-file",
                               {**self.headers, "Content-Type": content_type}, body)

    def create(self, bundle: Bundle) -> Any:
        """Real documented Upload API, called once after inventory validation."""
        metadata = {**self._metadata(bundle),
                    "gameVersionNames": CURSEFORGE_GAME_VERSIONS,
                    "relations": {"projects": CURSEFORGE_RELATIONS}}
        body, content_type = multipart("metadata", metadata, bundle)
        result = self.transport.request("POST", CF_BASE + "/projects/" + PROJECT_IDS[self.platform] + "/upload-file",
                                        {**self.headers, "Content-Type": content_type}, body)
        require(isinstance(result, dict), "Upload response missing file ID; reconcile before rerunning.")
        return file_id(self.platform, result.get("id"))


def publish(platform: str, bundle_dir: Path, receipt_path: Path | None,
            output_receipt: Path, transport: Transport | None = None) -> dict:
    require(platform in PROJECT_IDS, "Unknown platform.")
    bundle = Bundle(bundle_dir)
    receipt = validate_receipt(platform, bundle, receipt_path)
    # Validate destination shape before touching the remote. Parent directories may
    # be created locally, but failure must happen before any remote write.
    output_receipt = output_receipt.resolve()
    require(not output_receipt.exists() or output_receipt.is_file(), "Invalid output receipt path.")
    protected = [(bundle_dir / name).resolve() for name in ("metadata.json", "release-notes.md", bundle.metadata["jar_name"])]
    require(output_receipt not in protected, "Output receipt cannot overwrite a bundle input.")
    try:
        output_receipt.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryFile(dir=output_receipt.parent):
            pass
    except OSError:
        raise PublishError("Output receipt directory is not writable.") from None
    token = os.environ.get("PLATFORM_TOKEN", "")
    require(bool(token.strip()) and "\r" not in token and "\n" not in token, "PLATFORM_TOKEN is required and must be a single line.")
    client = (Modrinth if platform == "modrinth" else CurseForge)(transport or Transport(), token)
    identity = receipt["file_id"] if receipt else client.discover(bundle)
    if identity is None:
        identity = client.create(bundle)
    else:
        client.update(bundle, identity)
    result = make_receipt(platform, bundle, identity)
    save_receipt(output_receipt, result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--platform", required=True, choices=tuple(PROJECT_IDS))
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--receipt", type=Path, help="Existing, verified immutable publication receipt")
    parser.add_argument("--output-receipt", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        publish(args.platform, args.bundle, args.receipt, args.output_receipt)
    except PublishError as error:
        print("Publication failed: " + str(error), file=sys.stderr)
        return 1
    # Do not log credentials, request bodies, remote responses, or metadata.
    print("Publication acknowledged; output receipt saved.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
