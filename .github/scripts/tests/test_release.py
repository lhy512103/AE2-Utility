import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release

CONFIG = {"mod_id": "ae2utility", "name": "AE2 Utility", "minecraft_version": "1.21.1",
          "modrinth_project_id": "kJXRtOkZ", "curseforge_project_id": "1521605"}
NOTES = "### English\n\n1. Fixed: Example.\n\n### 中文\n\n1. 修复：示例。\n"


def jar_bytes(version="1.8.1", mod_id="ae2utility", extra=b"new"):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as jar:
        jar.writestr("META-INF/neoforge.mods.toml", f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
        jar.writestr("com/lhy/ae2utility/Example.class", b"\xca\xfe\xba\xbe" + extra)
    return stream.getvalue()


class FakeGitHub:
    base = "repos/lhy512103/AE2-Utility"
    repository = "lhy512103/AE2-Utility"

    def __init__(self, existing=None, binary=None):
        self.existing = existing
        self.binary = binary
        self.writes = []
        self.upload_error = False
        self.asset_list = []
        if binary is not None:
            self.asset_list.append({"id": 9, "name": "ae2utility-1.8.1.jar", "size": len(binary), "state": "uploaded"})

    def release(self, tag):
        return self.existing

    def assets(self, identity):
        return self.asset_list

    def api(self, endpoint, method="GET", data=None):
        self.writes.append((method, data))
        if method == "POST":
            self.existing = {"id": 1, **data}
        elif method == "PATCH":
            self.existing.update(data)
        return self.existing

    def upload(self, tag, path):
        self.writes.append(("UPLOAD", path.name))
        if self.upload_error:
            raise RuntimeError("Simulated upload failure")
        self.binary = path.read_bytes()
        self.asset_list = [{"id": 9, "name": path.name, "size": len(self.binary), "state": "uploaded"}]

    def download(self, asset, path):
        path.write_bytes(self.binary)


class VersionAndChangelogTest(unittest.TestCase):
    def test_tag_normalization_and_types(self):
        self.assertEqual(release.version_from_tag("v1.8.1"), "1.8.1")
        self.assertEqual(release.version_from_tag("1.8.1-beta.2+mc1.21.1"), "1.8.1-beta.2+mc1.21.1")
        self.assertEqual(release.release_type("1.8.1"), "release")
        self.assertEqual(release.release_type("1.8.1-rc.1"), "beta")
        self.assertEqual(release.release_type("1.8.1-alpha.2"), "alpha")
        self.assertEqual(release.release_type("1.8.1", True), "beta")

    def test_unsafe_or_non_version_tags_are_rejected(self):
        for tag in ("", "main", "../v1.8.1", "v1.8.1;echo bad", "v1.8.1\nnotes=x", "-v1.8.1", "v1.8"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.version_from_tag(tag)

    def test_extracts_only_exact_version_and_preserves_both_languages(self):
        text = "# Changelog\n\n## Unreleased\n\nDo not publish this.\n\n## 1.8.1\n\n" + NOTES + "\n## 1.8.0\n\nOld notes.\n"
        self.assertEqual(release.extract_changelog(text, "1.8.1"), NOTES)

    def test_bracketed_and_v_prefixed_headings(self):
        self.assertEqual(release.extract_changelog("## [v1.8.1] - 2026-11-08\n" + NOTES, "1.8.1"), NOTES)

    def test_fake_heading_inside_code_fence_does_not_end_section(self):
        notes = NOTES + "\n```markdown\n## 9.9.9\n```\n"
        self.assertEqual(release.extract_changelog("## 1.8.1\n" + notes, "1.8.1"), notes)

    def test_duplicate_or_missing_sections_do_not_fall_back_to_unreleased(self):
        for text in ("## Unreleased\nFresh notes", "## 1.8.10\nOther notes", "## 1.8.1\nA\n## 1.8.1\nB"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.extract_changelog(text, "1.8.1")

    def test_empty_or_headings_only_notes_are_rejected(self):
        for notes in ("", " \n", "### English\n\n### 中文\n"):
            with self.subTest(notes=notes), self.assertRaises(ValueError):
                release.require_notes(notes)


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.bundle = self.root / "bundle"
        self.libs = self.root / "libs"
        self.libs.mkdir()
        self.source = self.root / "source"
        self.source.mkdir()
        (self.source / "gradle.properties").write_text("mod_id=ae2utility\nmod_version=1.8.1\nminecraft_version=1.21.1\n", encoding="utf-8")
        (self.source / "CHANGELOG.md").write_text("## Unreleased\nNot yet.\n\n## 1.8.1\n" + NOTES, encoding="utf-8")
        release.prepare(self.source, "v1.8.1", CONFIG, self.bundle)
        (self.libs / "ae2utility-1.8.1.jar").write_bytes(jar_bytes())

    def tearDown(self):
        self.temporary.cleanup()

    def packed(self):
        release.pack(self.bundle, self.libs)
        return json.loads((self.bundle / "metadata.json").read_text(encoding="utf-8"))

    def test_pack_selects_main_jar_and_adds_its_hash(self):
        (self.libs / "ae2utility-1.8.1-sources.jar").write_bytes(b"source")
        metadata = self.packed()
        self.assertEqual(metadata["sha256"], hashlib.sha256(jar_bytes()).hexdigest())
        self.assertEqual(len(list(self.bundle.glob("*.jar"))), 1)

    def test_tag_mismatch_fails_before_a_release_bundle_is_written(self):
        with self.assertRaisesRegex(ValueError, "mod_version"):
            release.prepare(self.source, "v1.8.2", CONFIG, self.root / "invalid")
        self.assertFalse((self.root / "invalid").exists())

    def test_only_sources_or_no_main_jar_is_not_publishable(self):
        main = self.libs / "ae2utility-1.8.1.jar"
        main.unlink()
        (self.libs / "ae2utility-1.8.1-sources.jar").write_bytes(jar_bytes())
        with self.assertRaises(ValueError):
            self.packed()

    def test_empty_fake_or_wrong_version_main_jar_is_rejected(self):
        for content in (b"", b"text is not a Jar", jar_bytes("1.8.0"), jar_bytes(mod_id="othermod")):
            (self.libs / "ae2utility-1.8.1.jar").write_bytes(content)
            with self.subTest(content=content[:30]), self.assertRaises(ValueError):
                self.packed()

    def test_modified_bundle_jar_is_rejected(self):
        self.packed()
        (self.bundle / "ae2utility-1.8.1.jar").write_bytes(jar_bytes(extra=b"different"))
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            release.read_bundle(self.bundle)

    def test_new_release_is_draft_until_jar_has_been_verified(self):
        self.packed()
        github = FakeGitHub()
        result = release.github_release(github, self.bundle, "a" * 40)
        self.assertEqual([operation[0] for operation in github.writes], ["POST", "UPLOAD", "PATCH"])
        self.assertTrue(github.writes[0][1]["draft"])
        self.assertFalse(result["draft"])
        self.assertEqual(result["body"], NOTES)

    def test_failed_upload_never_publishes_release(self):
        self.packed()
        github = FakeGitHub()
        github.upload_error = True
        with self.assertRaises(RuntimeError):
            release.github_release(github, self.bundle, "a" * 40)
        self.assertTrue(github.existing["draft"])
        self.assertNotIn("PATCH", [operation[0] for operation in github.writes])

    def test_published_version_with_different_binary_is_not_overwritten(self):
        self.packed()
        github = FakeGitHub({"id": 1, "draft": False}, jar_bytes(extra=b"original"))
        with self.assertRaisesRegex(ValueError, "increment mod_version"):
            release.github_release(github, self.bundle, "a" * 40)
        self.assertEqual(github.writes, [])

    def test_published_release_without_jar_can_be_repaired(self):
        self.packed()
        github = FakeGitHub({"id": 1, "draft": False})
        release.github_release(github, self.bundle, "a" * 40)
        self.assertEqual([operation[0] for operation in github.writes], ["UPLOAD", "PATCH"])

    def test_snapshot_requires_actual_jar_not_merely_release_text(self):
        github = FakeGitHub({"id": 1, "draft": False, "body": NOTES})
        with self.assertRaisesRegex(ValueError, "main JAR"):
            release.download_release(github, "v1.8.1", CONFIG, self.root / "snapshot")
        self.assertEqual(github.writes, [])

    def test_draft_or_missing_release_cannot_reach_platforms(self):
        for existing in (None, {"id": 1, "draft": True, "body": NOTES}):
            with self.subTest(existing=existing), self.assertRaises(ValueError):
                release.download_release(FakeGitHub(existing, jar_bytes()), "v1.8.1", CONFIG, self.root / "snapshot")

    def test_snapshot_uses_live_body_title_and_downloaded_binary(self):
        github = FakeGitHub({"id": 7, "draft": False, "body": NOTES, "name": "AE2 Utility 1.8.1"}, jar_bytes())
        metadata = release.download_release(github, "v1.8.1", CONFIG, self.root / "snapshot")
        self.assertEqual(metadata["name"], "AE2 Utility 1.8.1")
        self.assertEqual(metadata["github_release_id"], 7)
        self.assertEqual((self.root / "snapshot/release-notes.md").read_text(encoding="utf-8"), NOTES)
        self.assertEqual(github.writes, [])

    def test_unfinished_or_empty_release_asset_is_rejected(self):
        for changes in ({"state": "new"}, {"size": 0}, {"name": "ae2utility-1.8.1-sources.jar"}):
            github = FakeGitHub({"id": 1, "draft": False, "body": NOTES}, jar_bytes())
            github.asset_list[0].update(changes)
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                release.download_release(github, "v1.8.1", CONFIG, self.root / "snapshot")


if __name__ == "__main__":
    unittest.main()
