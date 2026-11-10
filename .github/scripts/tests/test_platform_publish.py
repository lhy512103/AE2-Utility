"""Offline tests: python -m unittest discover -s .github/scripts/tests -v"""
import contextlib
import copy
from email import policy
from email.parser import BytesParser
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
SPEC = importlib.util.spec_from_file_location("platform_publish", Path(__file__).resolve().parents[1] / "platform_publish.py")
pub = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pub)


class FakeTransport:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    def request(self, method, url, headers, body=None):
        self.calls.append((method, url, headers, body))
        if not self.responses:
            raise AssertionError("Unexpected request (tests never use the network)")
        result = self.responses.pop(0)
        if isinstance(result, Exception):
            raise result
        return result

    @property
    def writes(self):
        return [call for call in self.calls if call[0] != "GET"]


def parse_multipart(call):
    content_type = call[2]["Content-Type"]
    message = BytesParser(policy=policy.default).parsebytes(
        b"Content-Type: " + content_type.encode() + b"\r\nMIME-Version: 1.0\r\n\r\n" + call[3])
    return {part.get_param("name", header="content-disposition"): part for part in message.iter_parts()}


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        self.output = self.root / "output" / "receipt.json"
        self.receipt = self.root / "existing.json"
        self.meta = {"tag": "v1.20.1.4", "version": "1.20.1.4", "name": "AE2 Utility 1.20.1.4",
                     "version_type": "release", "minecraft_version": "1.20.1", "mod_id": "ae2utility",
                     "jar_name": "ae2utility-1.20.1.4.jar", "sha256": "", "github_release_id": 123,
                     "repository": "lhy512103/AE2-Utility", "modrinth_project_id": "kJXRtOkZ",
                     "curseforge_project_id": "1521605"}
        self.notes = "## Release\n\n1. 新增：真实的发布正文。 / Added: release body.\n"
        (self.bundle / "release-notes.md").write_text(self.notes, encoding="utf-8")
        self.write_jar()
        self.env = patch.dict(os.environ, {"PLATFORM_TOKEN": "mock-secret-token"})
        self.env.start()
        self.addCleanup(self.env.stop)
        # Fail any accidentally introduced direct real-network request in tests.
        self.network = patch("urllib.request.OpenerDirector.open", side_effect=AssertionError("Network forbidden"))
        self.network.start()
        self.addCleanup(self.network.stop)

    def write_meta(self):
        (self.bundle / "metadata.json").write_text(json.dumps(self.meta), encoding="utf-8")

    def write_jar(self, mod_id="ae2utility", version="1.20.1.4", compiled=True, descriptor=True):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            if descriptor:
                archive.writestr("META-INF/mods.toml", 'modLoader="javafml"\n[[mods]]\nmodId="' + mod_id + '"\nversion="' + version + '"\n[[dependencies.ae2utility]]\nmodId="minecraft"\nversionRange="[1.20.1,1.21)"\n')
            if compiled:
                archive.writestr("dev/ae2utility/Utility.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x3dcompiled-test-fixture")
        self.jar = stream.getvalue()
        (self.bundle / self.meta["jar_name"]).write_bytes(self.jar)
        self.meta["sha256"] = hashlib.sha256(self.jar).hexdigest()
        self.write_meta()

    def existing(self, platform="modrinth", **changes):
        receipt = {"platform": platform, "project_id": pub.PROJECT_IDS[platform],
                   "version": self.meta["version"], "file_id": "AbCd1234" if platform == "modrinth" else 8973751,
                   "sha256": self.meta["sha256"]}
        receipt.update(changes)
        self.receipt.write_text(json.dumps(receipt), encoding="utf-8")
        return receipt

    def publish(self, transport, platform="modrinth", receipt=None):
        return pub.publish(platform, self.bundle, receipt, self.output, transport)

    def mr_version(self, **changes):
        version = {"id": "Remote12", "project_id": "kJXRtOkZ", "version_number": "1.20.1.4",
                   "loaders": ["forge"], "game_versions": ["1.20.1"],
                   "files": [{"filename": self.meta["jar_name"], "primary": True,
                              "hashes": {"sha1": hashlib.sha1(self.jar).hexdigest()}}]}
        version.update(changes)
        return version

    def assert_invalid_no_network(self, platform="modrinth", receipt=None):
        transport = FakeTransport()
        with self.assertRaises(pub.PublishError):
            self.publish(transport, platform, receipt)
        self.assertEqual(transport.calls, [])
        self.assertFalse(self.output.exists())

    def test_mr_first_upload_jar_and_release_body_in_one_multipart(self):
        transport = FakeTransport([], {"id": "New12345"})
        result = self.publish(transport)
        self.assertEqual([c[0] for c in transport.calls], ["GET", "POST"])
        call = transport.writes[0]
        self.assertEqual(call[1], "https://api.modrinth.com/v2/version")
        self.assertEqual(call[2]["Authorization"], "mock-secret-token")
        self.assertTrue(call[2]["User-Agent"])
        parts = parse_multipart(call)
        self.assertEqual(set(parts), {"data", "file"})
        self.assertEqual(parts["file"].get_payload(decode=True), self.jar)
        self.assertEqual(parts["file"].get_filename(), self.meta["jar_name"])
        data = json.loads(parts["data"].get_payload(decode=True))
        self.assertEqual(data["changelog"], self.notes)
        self.assertEqual(data["loaders"], ["forge"])
        self.assertEqual(data["game_versions"], ["1.20.1"])
        self.assertEqual(data["file_parts"], ["file"])
        self.assertEqual(data["primary_file"], "file")
        self.assertEqual(data["project_id"], "kJXRtOkZ")
        self.assertEqual(data["version_number"], "1.20.1.4")
        self.assertEqual(data["dependencies"], [{"project_id": "XxWD5pD3", "dependency_type": "required"},
                                                {"project_id": "u6dRKJwZ", "dependency_type": "optional"}])
        self.assertEqual(json.loads(self.output.read_text()), result)
        self.assertEqual(result["file_id"], "New12345")

    def test_cf_first_upload_jar_body_versions_and_dependencies(self):
        transport = FakeTransport({"id": 1521605, "files": []}, {"id": 9000000})
        result = self.publish(transport, "curseforge")
        self.assertEqual(result["file_id"], 9000000)
        read, write = transport.calls
        self.assertEqual(read[1], "https://api.cfwidget.com/1521605")
        self.assertNotIn("X-Api-Token", read[2])
        self.assertNotIn("Authorization", read[2])
        self.assertEqual(write[1], "https://minecraft.curseforge.com/api/projects/1521605/upload-file")
        self.assertEqual(write[2]["X-Api-Token"], "mock-secret-token")
        self.assertNotIn("?", write[1])
        parts = parse_multipart(write)
        self.assertEqual(set(parts), {"metadata", "file"})
        self.assertEqual(parts["file"].get_payload(decode=True), self.jar)
        data = json.loads(parts["metadata"].get_payload(decode=True))
        self.assertEqual(data["changelog"], self.notes)
        self.assertEqual(data["changelogType"], "markdown")
        self.assertEqual(data["gameVersionNames"], ["1.20.1", "Forge", "Java 17", "Client", "Server"])
        self.assertEqual(data["relations"]["projects"], [
            {"slug": "applied-energistics-2", "projectID": "223794", "type": "requiredDependency"},
            {"slug": "jei", "projectID": "238222", "type": "optionalDependency"}])

    def test_mr_same_receipt_updates_only_text(self):
        expected = self.existing()
        transport = FakeTransport(None)
        result = self.publish(transport, receipt=self.receipt)
        self.assertEqual(result, expected)
        self.assertEqual(len(transport.calls), 1)
        method, url, headers, data = transport.calls[0]
        self.assertEqual(method, "PATCH")
        self.assertEqual(url, "https://api.modrinth.com/v2/version/AbCd1234")
        self.assertEqual(json.loads(data), {"changelog": self.notes, "name": self.meta["name"], "version_type": "release"})
        self.assertEqual(headers["Content-Type"], "application/json")

    def test_cf_same_receipt_updates_metadata_without_file(self):
        expected = self.existing("curseforge")
        transport = FakeTransport({"id": expected["file_id"]})
        self.publish(transport, "curseforge", self.receipt)
        self.assertEqual(len(transport.calls), 1)
        call = transport.calls[0]
        self.assertEqual(call[:2], ("POST", "https://minecraft.curseforge.com/api/projects/1521605/update-file"))
        parts = parse_multipart(call)
        self.assertEqual(set(parts), {"metadata"})
        self.assertEqual(json.loads(parts["metadata"].get_payload(decode=True)),
                         {"fileID": 8973751, "changelog": self.notes, "changelogType": "markdown",
                          "displayName": self.meta["name"], "releaseType": "release"})

    def test_receipt_missing_file_is_treated_as_first_attempt(self):
        self.publish(FakeTransport([], {"id": "New12345"}), receipt=self.receipt)
        self.assertTrue(self.output.exists())

    def test_missing_jar_even_with_receipt_never_writes(self):
        for platform in pub.PROJECT_IDS:
            with self.subTest(platform=platform):
                self.existing(platform)
                jar = self.bundle / self.meta["jar_name"]
                jar.unlink(missing_ok=True)
                self.assert_invalid_no_network(platform, self.receipt)

    def test_empty_and_fake_jars_rejected(self):
        for content in (b"", b"not a jar", b"PK\x03\x04fake"):
            with self.subTest(content=content):
                (self.bundle / self.meta["jar_name"]).write_bytes(content)
                self.meta["sha256"] = hashlib.sha256(content).hexdigest()
                self.write_meta()
                self.assert_invalid_no_network()

    def test_mismatched_descriptor_rejected(self):
        for options in ({"mod_id": "other"}, {"version": "9.0.0"}, {"compiled": False}, {"descriptor": False}):
            with self.subTest(options=options):
                self.write_jar(**options)
                self.assert_invalid_no_network()

    def test_sha_mismatch_rejected(self):
        self.meta["sha256"] = "0" * 64
        self.write_meta()
        self.assert_invalid_no_network()

    def test_missing_empty_body_rejected(self):
        notes = self.bundle / "release-notes.md"
        for content in ("", " \n\t", None):
            with self.subTest(content=content):
                if content is None:
                    notes.unlink()
                else:
                    notes.write_text(content, encoding="utf-8")
                self.assert_invalid_no_network()

    def test_unsafe_metadata_rejected_before_any_request(self):
        original = copy.deepcopy(self.meta)
        for key, value in [("version", "../evil"), ("version", "1.20.1.4\r\nsecret"),
                           ("jar_name", "../ae2utility-1.20.1.4.jar"), ("jar_name", "C:\\evil.jar"),
                           ("jar_name", 'bad"name.jar'), ("jar_name", "ae2utility-1.20.1.4-sources.jar"),
                           ("jar_name", "ae2utility-1.20.1.4-javadoc.jar"), ("jar_name", "ae2utility-1.20.1.4-dev.jar"),
                           ("modrinth_project_id", "evil/project"), ("curseforge_project_id", "123"),
                           ("curseforge_project_id", 1521605), ("repository", "evil/repo"),
                           ("mod_id", "other"), ("minecraft_version", "1.21.1"), ("version_type", "snapshot"),
                           ("github_release_id", True), ("github_release_id", "123"),
                           ("name", "bad\r\nname"), ("tag", "../bad")]:
            with self.subTest(key=key, value=value):
                self.meta = copy.deepcopy(original)
                self.meta[key] = value
                self.write_meta()
                self.assert_invalid_no_network()

    def test_receipt_mismatch_or_unsafe_id_is_hard_failure(self):
        for changes in ({"platform": "curseforge"}, {"project_id": "other"}, {"version": "1.0.0"},
                        {"sha256": "0" * 64}, {"file_id": "../evil"}, {"file_id": 42}):
            with self.subTest(changes=changes):
                self.existing(**changes)
                self.assert_invalid_no_network(receipt=self.receipt)
        self.existing("curseforge", file_id="8973751")
        self.assert_invalid_no_network("curseforge", self.receipt)

    def test_mr_discover_sha1_reuses_and_patches(self):
        transport = FakeTransport([self.mr_version()], None)
        result = self.publish(transport)
        self.assertEqual([call[0] for call in transport.calls], ["GET", "PATCH"])
        self.assertEqual(result["file_id"], "Remote12")

    def test_mr_discover_sha256_reuses(self):
        version = self.mr_version()
        version["files"][0]["hashes"] = {"sha256": self.meta["sha256"]}
        self.publish(FakeTransport([version], None))
        self.assertTrue(self.output.exists())

    def test_mr_different_or_missing_hash_refuses_mutation(self):
        for hashes in ({}, {"sha1": "0" * 40}, {"sha1": hashlib.sha1(self.jar).hexdigest(), "sha256": "0" * 64}):
            with self.subTest(hashes=hashes):
                version = self.mr_version()
                version["files"][0]["hashes"] = hashes
                transport = FakeTransport([version])
                with self.assertRaises(pub.PublishError):
                    self.publish(transport)
                self.assertEqual(transport.writes, [])

    def test_mr_ambiguous_existing_versions_rejected(self):
        transport = FakeTransport([self.mr_version(), self.mr_version(id="Another1")])
        with self.assertRaises(pub.PublishError):
            self.publish(transport)
        self.assertEqual(transport.writes, [])

    def test_mr_other_loader_or_mc_does_not_match(self):
        for changes in ({"loaders": ["neoforge"]}, {"loaders": ["fabric"]}, {"game_versions": ["1.21.1"]}):
            with self.subTest(changes=changes):
                transport = FakeTransport([self.mr_version(**changes)], {"id": "New12345"})
                self.publish(transport)
                self.assertEqual(transport.writes[0][0], "POST")

    def test_cf_existing_filename_without_hash_refuses_upload(self):
        transport = FakeTransport({"id": 1521605, "files": [{"id": 8973751, "name": self.meta["jar_name"]}]})
        with self.assertRaisesRegex(pub.PublishError, "verified"):
            self.publish(transport, "curseforge")
        self.assertEqual(transport.writes, [])

    def test_cf_existing_same_hash_adopts_and_updates(self):
        transport = FakeTransport({"id": 1521605, "files": [{"id": 8973751, "name": self.meta["jar_name"],
                                                                          "hashes": {"sha256": self.meta["sha256"]}}]}, {"id": 8973751})
        self.publish(transport, "curseforge")
        self.assertTrue(transport.writes[0][1].endswith("/update-file"))

    def test_cf_existing_different_hash_refuses_upload(self):
        transport = FakeTransport({"id": 1521605, "files": [{"id": 8973751, "name": self.meta["jar_name"],
                                                                          "hashes": {"sha256": "0" * 64}}]})
        with self.assertRaisesRegex(pub.PublishError, "differs"):
            self.publish(transport, "curseforge")
        self.assertEqual(transport.writes, [])

    def test_cf_missing_broken_or_wrong_project_inventory_is_fail_closed(self):
        for response in ({}, {"id": 1521605}, {"id": 42, "files": []},
                         {"id": 1521605, "files": [{}]}, {"id": 1521605, "files": "wrong"},
                         pub.PublishError("read failed")):
            with self.subTest(response=response):
                transport = FakeTransport(response)
                with self.assertRaises(pub.PublishError):
                    self.publish(transport, "curseforge")
                self.assertEqual(transport.writes, [])

    def test_malformed_existing_receipt_refuses_network(self):
        self.receipt.write_text("not json", encoding="utf-8")
        self.assert_invalid_no_network(receipt=self.receipt)

    def test_receipt_cannot_overwrite_bundle_input(self):
        self.output = self.bundle / self.meta["jar_name"]
        transport = FakeTransport()
        with self.assertRaises(pub.PublishError):
            self.publish(transport)
        self.assertEqual(transport.calls, [])
        self.assertEqual(self.output.read_bytes(), self.jar)

    def test_post_failure_is_not_retried_and_creates_no_receipt(self):
        transport = FakeTransport([], pub.PublishError("HTTP 500"))
        with self.assertRaises(pub.PublishError):
            self.publish(transport)
        self.assertEqual(len(transport.writes), 1)
        self.assertFalse(self.output.exists())

    def test_receipt_written_immediately_after_successful_mutation(self):
        transport = FakeTransport([], {"id": "New12345"})
        with patch.object(pub, "save_receipt", wraps=pub.save_receipt) as save:
            self.publish(transport)
        save.assert_called_once()
        self.assertEqual(len(transport.calls), 2)
        self.assertTrue(self.output.exists())

    def test_missing_token_refuses_network(self):
        with patch.dict(os.environ, {"PLATFORM_TOKEN": ""}):
            self.assert_invalid_no_network()

    def test_validation_error_reports_stage_and_redacts_secret(self):
        secret = "mock-secret-token"
        error = urllib.error.HTTPError("https://host/", 400, "ignored", {},
                io.BytesIO(json.dumps({"errorMessage": "Unknown game version; token=" + secret}).encode()))
        transport = pub.Transport()
        with patch.object(transport.opener, "open", side_effect=error):
            with self.assertRaises(pub.PublishError) as result:
                transport.request("POST", pub.CF_BASE + "/projects/1521605/upload-file",
                                  {"X-Api-Token": secret}, b"data")
        self.assertIn("POST HTTP 400", str(result.exception))
        self.assertIn("Unknown game version", str(result.exception))
        self.assertNotIn(secret, str(result.exception))

    def test_transport_http_error_never_leaks_token_or_body(self):
        secret = "mock-secret-token"
        error = urllib.error.HTTPError("https://host/?token=" + secret, 401, secret,
                                       {"X-Api-Token": secret}, io.BytesIO(secret.encode()))
        transport = pub.Transport()
        with patch.object(transport.opener, "open", side_effect=error) as request:
            with self.assertRaises(pub.PublishError) as result:
                transport.request("POST", pub.CF_BASE + "/upload-file", {"X-Api-Token": secret}, b"content")
        self.assertNotIn(secret, str(result.exception))
        self.assertIn("401", str(result.exception))
        request.assert_called_once()
        self.assertEqual(request.call_args.kwargs["timeout"], 60)

    def test_transport_network_error_sanitized_without_retry(self):
        transport = pub.Transport()
        with patch.object(transport.opener, "open", side_effect=urllib.error.URLError("mock-secret-token")) as request:
            with self.assertRaises(pub.PublishError) as result:
                transport.request("POST", pub.MR_BASE + "/version", {"Authorization": "mock-secret-token"}, b"data")
        self.assertNotIn("mock-secret-token", str(result.exception))
        request.assert_called_once()

    def test_redirects_refused_without_forwarding_auth(self):
        for method in ("GET", "POST", "PATCH"):
            for status in (301, 302, 303, 307, 308):
                with self.subTest(method=method, status=status):
                    req = pub.urllib.request.Request(pub.MR_BASE, method=method, headers={"Authorization": "mock-secret-token"})
                    with self.assertRaises(pub.PublishError) as result:
                        pub.NoRedirect().redirect_request(req, None, status, "mock-secret-token", {}, "https://evil.invalid/")
                    self.assertNotIn("mock-secret-token", str(result.exception))

    def test_cli_sanitized_failure(self):
        err = io.StringIO()
        with patch.object(pub, "publish", side_effect=pub.PublishError("Platform API HTTP 401; request was not retried.")), contextlib.redirect_stderr(err):
            result = pub.main(["--platform", "modrinth", "--bundle", str(self.bundle), "--output-receipt", str(self.output)])
        self.assertEqual(result, 1)
        self.assertIn("401", err.getvalue())
        self.assertNotIn("mock-secret-token", err.getvalue())


if __name__ == "__main__":
    unittest.main()
