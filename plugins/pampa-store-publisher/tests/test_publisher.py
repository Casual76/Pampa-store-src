import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "mcp"))

import publisher


class StoreManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.existing = {
            "apps": [
                {
                    "id": "keyvoice",
                    "repoOwner": "Casual76",
                    "repoName": "KeyVoice",
                    "manifestPath": "manifest.json",
                    "ref": "",
                }
            ],
            "storeReleases": {
                "stable": {"version": "1.3.2"},
                "beta": {"version": "1.3.2"},
            },
        }
        self.version_info = {
            "version": "1.4.0",
            "releaseDate": "2026-08-24",
            "changelog": "Android redesign",
            "releaseTag": "stable-pampa-store-v1.4.0",
            "apkAsset": "pampa-store-1.4.0.apk",
            "sizeBytes": 3_570_367,
        }

    def upsert(self, channel: str, version_info: dict) -> dict:
        return publisher.upsert_store_manifest(
            existing_manifest=self.existing,
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="pampa-store",
            repo_owner="Casual76",
            repo_name="Pampa-store-src",
            manifest_path="manifest.json",
            channel=channel,
            version_info=version_info,
        )

    def test_stable_store_release_updates_stable_and_beta(self) -> None:
        updated = self.upsert("stable", self.version_info)

        self.assertEqual(self.version_info, updated["storeReleases"]["stable"])
        self.assertEqual(self.version_info, updated["storeReleases"]["beta"])
        self.assertNotIn("pampa-store", [entry["id"] for entry in updated["apps"]])
        self.assertEqual("1.3.2", self.existing["storeReleases"]["stable"]["version"])

    def test_beta_store_release_preserves_stable(self) -> None:
        beta = dict(self.version_info)
        beta["version"] = "1.4.1-beta01"
        beta["releaseTag"] = "beta-pampa-store-v1.4.1-beta01"

        updated = self.upsert("beta", beta)

        self.assertEqual("1.3.2", updated["storeReleases"]["stable"]["version"])
        self.assertEqual(beta, updated["storeReleases"]["beta"])


if __name__ == "__main__":
    unittest.main()
