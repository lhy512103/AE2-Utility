"""Tag -> validated release bundle -> GitHub Release, using the authenticated gh CLI.

No platform tokens are read here. A published GitHub Release with a real, validated
main JAR is the only source of input for downstream platform publishing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
from urllib.parse import quote
import zipfile

VERSION = r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?"
TAG_PREFIX = "neoforge-26.1.2-v"
MAX_JAR_BYTES = 100 * 1024 * 1024


def version_from_tag(tag: str) -> str:
    if not re.fullmatch(re.escape(TAG_PREFIX) + VERSION, tag):
        raise ValueError(f"Expected a version tag such as neoforge-26.1.2-v1.8.1, got {tag!r}")
    return tag.removeprefix(TAG_PREFIX)


def release_type(version: str, prerelease: bool = False) -> str:
    suffix = version.split("+", 1)[0].partition("-")[2].lower()
    return "alpha" if "alpha" in suffix else "beta" if suffix or prerelease else "release"


def read_properties(path: Path) -> dict[str, str]:
    properties = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def require_notes(notes: str) -> str:
    if not any(line.strip() and not line.lstrip().startswith("#") for line in notes.splitlines()):
        raise ValueError("Release notes are empty or contain only headings")
    return notes.strip() + "\n"


def extract_changelog(text: str, version: str) -> str:
    """Select one exact level-two version section; never fall back to Unreleased."""
    sections = []
    lines = text.splitlines()
    fence = None
    for index, line in enumerate(lines):
        marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if marker:
            token = marker.group(1)
            if fence is None:
                fence = (token[0], len(token))
            elif token[0] == fence[0] and len(token) >= fence[1]:
                fence = None
            continue
        if fence is None and re.match(r"^##\s+", line):
            sections.append((index, line[3:].strip()))
    matches = []
    heading = re.compile(r"(?:\[(v?" + VERSION + r")\]|(v?" + VERSION + r"))(?:\s+-\s+.*)?")
    for number, (start, label) in enumerate(sections):
        found = heading.fullmatch(label)
        if found and (found.group(1) or found.group(2)).removeprefix("v") == version:
            end = sections[number + 1][0] if number + 1 < len(sections) else len(lines)
            matches.append("\n".join(lines[start + 1:end]))
    if len(matches) != 1:
        raise ValueError(f"CHANGELOG must contain exactly one '## {version}' section (found {len(matches)})")
    return require_notes(matches[0])


def config_metadata(config: dict, tag: str, prerelease: bool = False) -> dict:
    version = version_from_tag(tag)
    if config.get("minecraft_version") != "26.1.2":
        raise ValueError("Release configuration must target Minecraft 26.1.2")
    mod_id = config["mod_id"]
    if not re.fullmatch(r"[a-z][a-z0-9_]*", mod_id):
        raise ValueError("Invalid mod_id")
    return {**config, "tag": tag, "version": version,
            "version_type": release_type(version, prerelease),
            "jar_name": f"{mod_id}-neoforge-26.1.2-{version}.jar"}


def validate_jar(path: Path, metadata: dict) -> str:
    if path.name != metadata["jar_name"] or path.name != f"{metadata['mod_id']}-neoforge-26.1.2-{metadata['version']}.jar":
        raise ValueError("Not the exact versioned main JAR (sources/dev/javadoc are not publishable)")
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_JAR_BYTES:
        raise ValueError("Main JAR is missing, empty, or exceeds the release size limit")
    try:
        with zipfile.ZipFile(path) as jar:
            if sum(entry.file_size for entry in jar.infolist()) > 200 * 1024 * 1024:
                raise ValueError("JAR uncompressed size exceeds the release limit")
            descriptor = "META-INF/neoforge.mods.toml"
            if jar.namelist().count(descriptor) != 1 or jar.getinfo(descriptor).file_size > 1024 * 1024:
                raise ValueError("Main JAR must contain exactly one bounded NeoForge descriptor")
            if not any(name.endswith(".class") and not name.startswith("META-INF/") for name in jar.namelist()):
                raise ValueError("Main JAR contains no compiled mod classes")
            declaration = tomllib.loads(jar.read(descriptor).decode("utf-8-sig"))
            mods = declaration.get("mods", [])
            matches = [mod for mod in mods if isinstance(mod, dict) and mod.get("modId") == metadata["mod_id"]]
            if len(matches) != 1 or matches[0].get("version") != metadata["version"]:
                raise ValueError("JAR metadata does not match the release mod ID/version")
            dependencies = declaration.get("dependencies", {}).get(metadata["mod_id"], [])
            mc = [d for d in dependencies if d.get("modId") == "minecraft"]
            if len(mc) != 1 or mc[0].get("type") != "required" or mc[0].get("versionRange") != "[26.1.2]":
                raise ValueError("JAR must declare required Minecraft [26.1.2]")
            if jar.testzip() is not None:
                raise ValueError("Main JAR has corrupt entries")
    except (zipfile.BadZipFile, KeyError, tomllib.TOMLDecodeError, UnicodeDecodeError) as error:
        raise ValueError("Main JAR is not a valid NeoForge mod JAR") from error
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if metadata.get("sha256") and metadata["sha256"] != digest:
        raise ValueError("Main JAR SHA-256 does not match the bundle")
    return digest


def read_bundle(bundle: Path) -> tuple[dict, str, Path]:
    metadata = json.loads((bundle / "metadata.json").read_text(encoding="utf-8"))
    version = version_from_tag(metadata["tag"])
    if metadata["version"] != version or metadata["jar_name"] != f"{metadata['mod_id']}-neoforge-26.1.2-{version}.jar":
        raise ValueError("Invalid release bundle metadata")
    jar = bundle / metadata["jar_name"]
    validate_jar(jar, metadata)
    notes = require_notes((bundle / "release-notes.md").read_text(encoding="utf-8"))
    return metadata, notes, jar


def write_metadata(bundle: Path, metadata: dict) -> None:
    bundle.mkdir(parents=True, exist_ok=True)
    (bundle / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def prepare(source: Path, tag: str, config: dict, bundle: Path) -> dict:
    metadata = config_metadata(config, tag)
    properties = read_properties(source / "gradle.properties")
    if properties.get("mod_version") != metadata["version"]:
        raise ValueError("Tag version must equal gradle.properties mod_version")
    if properties.get("mod_id") != metadata["mod_id"] or properties.get("minecraft_version") != metadata["minecraft_version"]:
        raise ValueError("Source mod ID/Minecraft version does not match release-config.json")
    notes = extract_changelog((source / "CHANGELOG.md").read_text(encoding="utf-8-sig"), metadata["version"])
    write_metadata(bundle, metadata)
    (bundle / "release-notes.md").write_text(notes, encoding="utf-8")
    if os.getenv("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for key in ("tag", "version", "jar_name"):
                output.write(f"{key}={metadata[key]}\n")
    return metadata


def pack(bundle: Path, libs: Path) -> None:
    metadata = json.loads((bundle / "metadata.json").read_text(encoding="utf-8"))
    jar = libs / metadata["jar_name"]
    metadata["sha256"] = validate_jar(jar, metadata)
    shutil.copy2(jar, bundle / metadata["jar_name"])
    write_metadata(bundle, metadata)
    read_bundle(bundle)


class GitHub:
    def __init__(self, repository: str):
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository):
            raise ValueError("Invalid GitHub repository")
        self.repository = repository
        self.base = f"repos/{repository}"

    def api(self, endpoint: str, method: str = "GET", data=None, missing_ok: bool = False, pages: bool = False):
        command = ["gh", "api", endpoint, "--method", method]
        if pages:
            command += ["--paginate", "--slurp"]
        payload = None
        if data is not None:
            command += ["--input", "-"]
            payload = json.dumps(data)
        result = subprocess.run(command, input=payload, text=True, encoding="utf-8", capture_output=True, check=False)
        if result.returncode:
            if missing_ok and "(HTTP 404)" in result.stderr:
                return None
            raise RuntimeError(f"GitHub API {method} failed for {endpoint}; inspect the Actions run/authentication")
        return json.loads(result.stdout) if result.stdout.strip() else None

    def release(self, tag: str):
        return self.api(f"{self.base}/releases/tags/{quote(tag, safe='')}", missing_ok=True)

    def assets(self, release_id: int) -> list[dict]:
        pages = self.api(f"{self.base}/releases/{int(release_id)}/assets?per_page=100", pages=True)
        return [asset for page in pages for asset in page]

    def upload(self, tag: str, path: Path) -> None:
        result = subprocess.run(["gh", "release", "upload", tag, str(path), "--repo", self.repository, "--clobber"], check=False)
        if result.returncode:
            raise RuntimeError(f"Could not attach {path.name} to GitHub Release")

    def download(self, asset: dict, path: Path) -> None:
        if asset.get("state") != "uploaded" or not 0 < int(asset.get("size", 0)) <= MAX_JAR_BYTES:
            raise ValueError("Release asset is not fully uploaded or has an invalid size")
        with path.open("wb") as output:
            result = subprocess.run(["gh", "api", f"{self.base}/releases/assets/{int(asset['id'])}",
                                     "-H", "Accept: application/octet-stream"], stdout=output, check=False)
        if result.returncode or path.stat().st_size != asset["size"]:
            raise RuntimeError(f"Could not download the complete Release asset {path.name}")


def main_asset(assets: list[dict], metadata: dict) -> dict:
    matches = [asset for asset in assets if asset.get("name") == metadata["jar_name"]]
    if len(matches) != 1 or matches[0].get("state") != "uploaded" or matches[0].get("size", 0) <= 0:
        raise ValueError("Release must contain exactly one fully uploaded, non-empty versioned main JAR before publishing")
    return matches[0]


def github_release(client: GitHub, bundle: Path, commit: str) -> dict:
    metadata, notes, jar = read_bundle(bundle)
    release = client.release(metadata["tag"])
    if release is None:
        release = client.api(f"{client.base}/releases", "POST", {
            "tag_name": metadata["tag"], "target_commitish": commit,
            "name": f"{metadata['name']} {metadata['version']}", "body": notes,
            "draft": True, "prerelease": metadata["version_type"] != "release"})
    if not release.get("draft"):
        previous = [item for item in client.assets(release["id"]) if item.get("name") == metadata["jar_name"]]
        if previous:
            original = bundle / "previous-published-jar"
            original.mkdir(exist_ok=True)
            client.download(main_asset(previous, metadata), original / metadata["jar_name"])
            try:
                validate_jar(original / metadata["jar_name"], metadata)
            except ValueError as error:
                raise ValueError("A published version has a different JAR; increment mod_version and create a new tag") from error
    if release.get("draft") or not previous:
        client.upload(metadata["tag"], jar)
    asset = main_asset(client.assets(release["id"]), metadata)
    if asset["size"] != jar.stat().st_size:
        raise ValueError("Uploaded Release JAR size differs from the build output")
    # Do not rely on an upload command's exit status: read the actual remote JAR back.
    verification = bundle / "remote-verification"
    verification.mkdir(exist_ok=True)
    client.download(asset, verification / metadata["jar_name"])
    validate_jar(verification / metadata["jar_name"], metadata)
    return client.api(f"{client.base}/releases/{release['id']}", "PATCH", {
        "name": f"{metadata['name']} {metadata['version']}", "body": notes,
        "draft": False, "prerelease": metadata["version_type"] != "release", "make_latest": "legacy"})


def download_release(client: GitHub, tag: str, config: dict, bundle: Path) -> dict:
    release = client.release(tag)
    if release is None or release.get("draft"):
        raise ValueError("Only an existing, published GitHub Release can be sent to mod platforms")
    notes = require_notes(release.get("body") or "")
    metadata = config_metadata(config, tag, bool(release.get("prerelease")))
    metadata["name"] = release.get("name") or f"{config['name']} {metadata['version']}"
    assets = client.assets(release["id"])
    asset = main_asset(assets, metadata)
    bundle.mkdir(parents=True, exist_ok=True)
    jar = bundle / metadata["jar_name"]
    client.download(asset, jar)
    metadata["sha256"] = validate_jar(jar, metadata)
    metadata.update(github_release_id=release["id"], repository=client.repository)
    # Release text is always read from the live API, not an old workflow event payload.
    (bundle / "release-notes.md").write_text(notes, encoding="utf-8")
    for platform in ("modrinth", "curseforge"):
        receipts = [item for item in assets if item.get("name") == f"publish-{platform}.json"]
        if len(receipts) > 1:
            raise ValueError("Ambiguous platform receipts on GitHub Release")
        if receipts:
            if receipts[0].get("size", 0) > 65536:
                raise ValueError("Platform receipt is too large")
            client.download(receipts[0], bundle / f"publish-{platform}.json")
    write_metadata(bundle, metadata)
    return metadata


def save_receipt(client: GitHub, tag: str, platform: str, receipt: Path) -> None:
    data = json.loads(receipt.read_text(encoding="utf-8"))
    if data.get("platform") != platform or data.get("version") != version_from_tag(tag) or not data.get("file_id"):
        raise ValueError("Platform receipt does not match this release")
    # Upload with the stable, per-platform name; separate jobs never clobber each other's receipt.
    path = receipt.parent / f"publish-{platform}.json"
    if path != receipt:
        shutil.copy2(receipt, path)
    client.upload(tag, path)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "pack", "github-release", "download-release", "save-receipt"])
    parser.add_argument("--source", type=Path, default=Path("."))
    parser.add_argument("--config", type=Path, default=Path(".github/release-config.json"))
    parser.add_argument("--bundle", type=Path, default=Path("dist/release"))
    parser.add_argument("--libs", type=Path, default=Path("build/libs"))
    parser.add_argument("--tag", default=os.getenv("RELEASE_TAG", ""))
    parser.add_argument("--repository", default=os.getenv("GITHUB_REPOSITORY", ""))
    parser.add_argument("--commit", default=os.getenv("GITHUB_SHA", ""))
    parser.add_argument("--platform", choices=["modrinth", "curseforge"])
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            prepare(args.source, args.tag, json.loads(args.config.read_text(encoding="utf-8")), args.bundle)
        elif args.command == "pack":
            pack(args.bundle, args.libs)
        else:
            client = GitHub(args.repository)
            if args.command == "github-release":
                github_release(client, args.bundle, args.commit)
            elif args.command == "download-release":
                download_release(client, args.tag, json.loads(args.config.read_text(encoding="utf-8")), args.bundle)
            else:
                if not args.receipt or not args.platform:
                    raise ValueError("save-receipt requires --receipt and --platform")
                save_receipt(client, args.tag, args.platform, args.receipt)
    except (ValueError, KeyError, RuntimeError, OSError, json.JSONDecodeError) as error:
        print(f"Release blocked: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
