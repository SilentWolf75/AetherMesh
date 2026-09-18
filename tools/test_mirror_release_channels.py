import unittest

try:
    from tools.mirror_release_channels import pick
except ImportError:  # run from inside tools/
    from mirror_release_channels import pick


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


if __name__ == "__main__":
    unittest.main()
