import sys
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch


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

        self.assertEqual(
            {"repoOwner": "Casual76", "repoName": "Pampa-store"},
            updated["storeReleases"]["repository"],
        )
        self.assertEqual(self.version_info, updated["storeReleases"]["stable"])
        self.assertEqual(self.version_info, updated["storeReleases"]["beta"])
        self.assertNotIn("pampa-store", [entry["id"] for entry in updated["apps"]])
        self.assertEqual("1.3.2", self.existing["storeReleases"]["stable"]["version"])

    def test_beta_store_release_preserves_stable(self) -> None:
        beta = dict(self.version_info)
        beta["version"] = "1.4.1-beta01"
        beta["releaseTag"] = "beta-pampa-store-v1.4.1-beta01"

        updated = self.upsert("beta", beta)

        self.assertEqual("Pampa-store", updated["storeReleases"]["repository"]["repoName"])
        self.assertEqual("1.3.2", updated["storeReleases"]["stable"]["version"])
        self.assertEqual(beta, updated["storeReleases"]["beta"])

    def test_store_release_repository_can_be_overridden_without_changing_source(self) -> None:
        updated = publisher.upsert_store_manifest(
            existing_manifest=self.existing,
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="pampa-store",
            repo_owner="Casual76",
            repo_name="Pampa-store-src",
            manifest_path="manifest.json",
            channel="stable",
            version_info=self.version_info,
            release_owner="PampaDistribution",
            release_repo="android-downloads",
        )

        self.assertEqual(
            {"repoOwner": "PampaDistribution", "repoName": "android-downloads"},
            updated["storeReleases"]["repository"],
        )


class ReleaseRepositoryTests(unittest.TestCase):
    def test_pampa_store_defaults_to_the_public_store_repository(self) -> None:
        resolved = publisher.resolve_release_repository(
            source_repository={"repoOwner": "Casual76", "repoName": "Pampa-store-src"},
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="pampa-store",
            package_name="com.pampa.store",
        )

        self.assertEqual({"repoOwner": "Casual76", "repoName": "Pampa-store"}, resolved)

    def test_other_apps_keep_their_source_repository_by_default(self) -> None:
        resolved = publisher.resolve_release_repository(
            source_repository={"repoOwner": "Casual76", "repoName": "KeyVoice"},
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="keyvoice",
            package_name="com.casual.keyvoice",
        )

        self.assertEqual({"repoOwner": "Casual76", "repoName": "KeyVoice"}, resolved)

    def test_any_app_can_override_its_distribution_repository(self) -> None:
        resolved = publisher.resolve_release_repository(
            source_repository={"repoOwner": "Casual76", "repoName": "KeyVoice"},
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="keyvoice",
            package_name="com.casual.keyvoice",
            release_owner="PampaDistribution",
            release_repo="android-downloads",
        )

        self.assertEqual(
            {"repoOwner": "PampaDistribution", "repoName": "android-downloads"},
            resolved,
        )

    def test_app_manifest_uses_distribution_while_store_index_keeps_source(self) -> None:
        version_info = {
            "version": "2.0.0",
            "releaseTag": "stable-keyvoice-v2.0.0",
            "apkAsset": "keyvoice-2.0.0.apk",
        }
        app_manifest = publisher.upsert_app_manifest(
            existing_manifest=None,
            repo_owner="Casual76",
            repo_name="KeyVoice",
            manifest_path="manifest.json",
            app_id="keyvoice",
            app_name="KeyVoice",
            description="Voice app",
            package_name="com.casual.keyvoice",
            developer="Pampa",
            category="Utility",
            channel="stable",
            version_info=version_info,
            release_owner="PampaDistribution",
            release_repo="android-downloads",
        )
        store_manifest = publisher.upsert_store_manifest(
            existing_manifest={"apps": [], "storeReleases": {}},
            store_owner="Casual76",
            store_repo="Pampa-store",
            app_id="keyvoice",
            repo_owner="Casual76",
            repo_name="KeyVoice",
            manifest_path="manifest.json",
            channel="stable",
            version_info=version_info,
            release_owner="PampaDistribution",
            release_repo="android-downloads",
        )

        distribution = app_manifest["app"]["repository"]
        self.assertEqual(
            {
                "repoOwner": "PampaDistribution",
                "repoName": "android-downloads",
                "manifestPath": "manifest.json",
                "ref": "",
            },
            distribution,
        )
        release = app_manifest["app"]["stable"]
        asset_url = (
            f"https://github.com/{distribution['repoOwner']}/{distribution['repoName']}/releases/download/"
            f"{release['releaseTag']}/{release['apkAsset']}"
        )
        self.assertEqual(
            "https://github.com/PampaDistribution/android-downloads/releases/download/"
            "stable-keyvoice-v2.0.0/keyvoice-2.0.0.apk",
            asset_url,
        )
        self.assertEqual("KeyVoice", store_manifest["apps"][0]["repoName"])


class ProvenanceAndPreflightTests(unittest.TestCase):
    def test_clean_pushed_source_passes_preflight(self) -> None:
        responses = [
            CompletedProcess([], 0, "abc123\n", ""),
            CompletedProcess([], 0, "", ""),
            CompletedProcess([], 0, "origin/main\n", ""),
            CompletedProcess([], 0, "", ""),
        ]
        with patch.object(publisher, "run_command", side_effect=responses):
            preflight = publisher.inspect_source_preflight(Path("."))

        self.assertTrue(preflight["ready"])
        self.assertTrue(preflight["worktreeClean"])
        self.assertTrue(preflight["headIncludedInUpstream"])
        self.assertEqual("abc123", preflight["commitSha"])

    def test_dirty_unpushed_source_is_reported_and_blocks_live_publish(self) -> None:
        responses = [
            CompletedProcess([], 0, "abc123\n", ""),
            CompletedProcess([], 0, " M app/file.kt\n", ""),
            CompletedProcess([], 0, "origin/main\n", ""),
            CompletedProcess([], 1, "", ""),
        ]
        with patch.object(publisher, "run_command", side_effect=responses):
            preflight = publisher.inspect_source_preflight(Path("."))

        self.assertFalse(preflight["ready"])
        self.assertIn("source worktree is not clean", preflight["issues"])
        self.assertIn("source HEAD is not included in its upstream", preflight["issues"])
        with self.assertRaises(publisher.SkillError):
            publisher.require_source_publish_preflight(preflight)

    def test_release_body_records_source_repository_and_sha(self) -> None:
        body = publisher.build_release_body(
            changelog="Fluid Glass",
            source_repository={"repoOwner": "Casual76", "repoName": "Pampa-store-src"},
            source_commit_sha="abc123",
        )

        self.assertIn("Fluid Glass", body)
        self.assertIn("Source: Casual76/Pampa-store-src@abc123", body)


class ReleaseTargetTests(unittest.TestCase):
    def test_source_release_targets_the_exact_source_sha(self) -> None:
        repository = {"repoOwner": "Casual76", "repoName": "KeyVoice"}
        with patch.object(publisher, "get_repository_default_branch") as get_default_branch:
            target = publisher.resolve_release_target_commitish(
                source_repository=repository,
                release_repository=repository,
                source_commit_sha="abc123",
                token="token",
            )

        self.assertEqual("abc123", target)
        get_default_branch.assert_not_called()

    def test_distribution_release_targets_its_default_branch(self) -> None:
        with patch.object(publisher, "get_repository_default_branch", return_value="main") as get_default_branch:
            target = publisher.resolve_release_target_commitish(
                source_repository={"repoOwner": "Casual76", "repoName": "Pampa-store-src"},
                release_repository={"repoOwner": "Casual76", "repoName": "Pampa-store"},
                source_commit_sha="abc123",
                token="token",
            )

        self.assertEqual("main", target)
        get_default_branch.assert_called_once_with(owner="Casual76", repo="Pampa-store", token="token")

    def test_create_release_sends_target_commitish(self) -> None:
        with patch.object(publisher, "github_request", return_value={}) as request:
            publisher.create_release(
                owner="Casual76",
                repo="Pampa-store",
                token="token",
                tag="stable-pampa-store-v1.5.0",
                name="Pampa Store 1.5.0",
                body="Release body",
                prerelease=False,
                target_commitish="main",
            )

        self.assertEqual("main", request.call_args.kwargs["payload"]["target_commitish"])


class McpPublishSchemaTests(unittest.TestCase):
    def test_schema_exposes_dry_run_with_additional_properties_disabled(self) -> None:
        schema = publisher.PUBLISH_TOOL_INPUT_SCHEMA

        self.assertFalse(schema["additionalProperties"])
        self.assertEqual("boolean", schema["properties"]["dryRun"]["type"])

    def test_publish_handler_propagates_dry_run(self) -> None:
        with patch.object(
            publisher,
            "publish",
            side_effect=lambda args: {"dryRun": args.dry_run},
        ):
            result = publisher.publish_from_params({"channel": "stable", "dryRun": True})

        self.assertTrue(result["dryRun"])


if __name__ == "__main__":
    unittest.main()
