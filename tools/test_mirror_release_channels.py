import unittest

try:
    from tools.mirror_release_channels import index_entry, pick, pick_many
except ImportError:  # run from inside tools/
    from mirror_release_channels import index_entry, pick, pick_many


def release(tag, prerelease=False, draft=False, manifest=True):
    assets = [{"name": "aethermesh-heltec-v4-abc1234-usb.bin"}]
    if manifest:
        assets.append({"name": "manifest.json"})
    return {"tag_name": tag, "prerelease": prerelease, "draft": draft, "assets": assets}


class PickTest(unittest.TestCase):
    def test_each_channel_takes_only_its_own_kind(self):
        releases = [release("v1.3.5-beta.1", prerelease=True), release("v1.3.4")]
        self.assertEqual("v1.3.4", pick(releases, want_prerelease=False)["tag_name"])
        self.assertEqual("v1.3.5-beta.1", pick(releases, want_prerelease=True)["tag_name"])

    def test_an_empty_channel_stays_empty(self):
        # Only a beta exists: the release channel must not be filled with it.
        releases = [release("v1.3.4-beta.1", prerelease=True)]
        self.assertIsNone(pick(releases, want_prerelease=False))

    def test_unverifiable_and_draft_releases_are_skipped(self):
        # v1.3.1 predates manifests, so it cannot be verified and is passed over
        # for an older one that can be; drafts are never published at all.
        releases = [
            release("v1.4.0", draft=True),
            release("v1.3.1", manifest=False),
            release("v1.3.0"),
        ]
        self.assertEqual("v1.3.0", pick(releases, want_prerelease=False)["tag_name"])

    def test_newest_first_order_is_respected(self):
        releases = [release("v1.3.6-beta.2", prerelease=True), release("v1.3.6-beta.1", prerelease=True)]
        self.assertEqual("v1.3.6-beta.2", pick(releases, want_prerelease=True)["tag_name"])

    def test_several_builds_are_kept_for_rollback(self):
        releases = [release(f"v1.3.{n}-beta.1", prerelease=True) for n in (9, 8, 7, 6)]
        kept = [r["tag_name"] for r in pick_many(releases, want_prerelease=True, limit=3)]
        # Newest three, newest first, so a bad beta can be rolled back.
        self.assertEqual(["v1.3.9-beta.1", "v1.3.8-beta.1", "v1.3.7-beta.1"], kept)

    def test_rollback_list_still_skips_unverifiable_builds(self):
        releases = [release("v2", manifest=False), release("v1"), release("v0", draft=True)]
        self.assertEqual(["v1"], [r["tag_name"] for r in pick_many(releases, want_prerelease=False)])

    def test_notes_are_kept_but_bounded(self):
        entry = index_entry({"tag_name": "v1", "body": "x" * 10000, "name": "", "published_at": "2026-09-18"})
        self.assertEqual("v1", entry["name"])
        self.assertLess(len(entry["notes"]), 6200)
        self.assertIn("Truncated", entry["notes"])


if __name__ == "__main__":
    unittest.main()
