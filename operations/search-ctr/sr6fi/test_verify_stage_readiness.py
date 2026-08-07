#!/usr/bin/env python3

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from render_sr6fh_binding import render
from verify_stage_readiness import VerificationError, source_db_package_sha256, verify

SECRET_NAMES = """SR6FH_STAGE_ADMIN_DATABASE_URL
SR6FH_STAGE_ADMIN_USERNAME
SR6FH_STAGE_ADMIN_PASSWORD
SR6FH_STAGE_BACKEND_JDBC_URL
SR6FH_STAGE_BACKEND_USERNAME
SR6FH_STAGE_BACKEND_PASSWORD
SR6FH_STAGE_JWT_SECRET
"""

TEMPLATE = (HERE / "stage-readiness-manifest.env.example").read_text(encoding="utf-8")


class StageReadinessVerifierTest(unittest.TestCase):
    def make_repo(self, ready_matrix: bool = False) -> Path:
        root = Path(tempfile.mkdtemp(prefix="sr6fi-test-"))
        (root / "jc-backend").mkdir(parents=True)
        (root / "jc-backend" / "build.gradle.kts").write_text("// test\n", encoding="utf-8")
        sr6fi = root / "operations" / "search-ctr" / "sr6fi"
        sr6fi.mkdir(parents=True)
        (sr6fi / "required-secret-names.txt").write_text(SECRET_NAMES, encoding="utf-8")
        sr6fh = root / "operations" / "search-ctr" / "sr6fh"
        sr6fh.mkdir(parents=True)
        (sr6fh / "stage-execution-contract.env").write_text(
            "SR6FH_EXECUTION_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS\n"
            "SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=UNASSIGNED\n"
            "SR6FH_FINALITY_WRITE=DISABLED\n",
            encoding="utf-8",
        )
        package = root / "database" / "journey-connect-db-v2.8"
        package.mkdir(parents=True)
        (package / "README.md").write_text("test package\n", encoding="utf-8")
        (package / "11_search_ctr_reliability_role_noinherit_smoke_test.sql").write_text(
            "SELECT 1;\n", encoding="utf-8"
        )
        matrix_path = root / "verification" / "sc-next-track" / "op3-entry"
        matrix_path.mkdir(parents=True)
        if ready_matrix:
            matrix = {
                "decision_status": "APPROVED_FOR_BOUNDED_NONPRODUCTION_EXECUTION",
                "final_deployment_platform": "AWS_ECS_FARGATE",
                "deployment_implementation": "AUTHORIZED",
                "cloud_resource_creation_authorized": True,
                "billing_spend_authorized": True,
                "iam_mutation_authorized": True,
            }
        else:
            matrix = {
                "decision_status": "DEFERRED_PLATFORM_UNDECIDED",
                "final_deployment_platform": "UNDECIDED",
                "deployment_implementation": "DEFERRED",
                "cloud_resource_creation_authorized": False,
                "billing_spend_authorized": False,
                "iam_mutation_authorized": False,
            }
        (matrix_path / "sc-op3-required-input-decision-matrix.json").write_text(
            json.dumps(matrix), encoding="utf-8"
        )
        return root

    def write_template(self, root: Path, text: str = TEMPLATE) -> Path:
        path = root / "stage-readiness.env"
        path.write_text(text, encoding="utf-8")
        return path

    def ready_manifest(self, root: Path) -> str:
        digest = source_db_package_sha256(root)
        replacements = {
            "SR6FI_READINESS_STATUS=BLOCKED_PLATFORM_UNDECIDED":
                "SR6FI_READINESS_STATUS=READY_FOR_SR6FH_BINDING",
            "SR6FI_FINAL_DEPLOYMENT_PLATFORM=UNDECIDED":
                "SR6FI_FINAL_DEPLOYMENT_PLATFORM=AWS_ECS_FARGATE",
            "SR6FI_PLATFORM_DECISION_STATUS=DEFERRED_PLATFORM_UNDECIDED":
                "SR6FI_PLATFORM_DECISION_STATUS=APPROVED_FOR_BOUNDED_NONPRODUCTION_EXECUTION",
            "SR6FI_RESOURCE_CREATION_AUTHORIZED=NO": "SR6FI_RESOURCE_CREATION_AUTHORIZED=YES",
            "SR6FI_BILLING_SPEND_AUTHORIZED=NO": "SR6FI_BILLING_SPEND_AUTHORIZED=YES",
            "SR6FI_IAM_MUTATION_AUTHORIZED=NO": "SR6FI_IAM_MUTATION_AUTHORIZED=YES",
            "SR6FI_DEPLOYMENT_SOURCE_SHA=UNASSIGNED":
                "SR6FI_DEPLOYMENT_SOURCE_SHA=" + "a" * 40,
            "SR6FI_DEPLOYMENT_ARTIFACT_DIGEST=UNASSIGNED":
                "SR6FI_DEPLOYMENT_ARTIFACT_DIGEST=sha256:" + "b" * 64,
            "SR6FI_DEPLOYMENT_RESOURCE_ID=UNASSIGNED":
                "SR6FI_DEPLOYMENT_RESOURCE_ID=journey-connect-stage-candidate",
            "SR6FI_STAGE_DATABASE_PLATFORM=UNASSIGNED":
                "SR6FI_STAGE_DATABASE_PLATFORM=AWS_RDS_POSTGRESQL",
            "SR6FI_STAGE_DATABASE_RESOURCE_ID=UNASSIGNED":
                "SR6FI_STAGE_DATABASE_RESOURCE_ID=journey-connect-stage-db",
            "SR6FI_STAGE_ENDPOINT_SHA256=UNASSIGNED":
                "SR6FI_STAGE_ENDPOINT_SHA256=" + "c" * 64,
            "SR6FI_DB_PACKAGE_SHA256=UNASSIGNED":
                "SR6FI_DB_PACKAGE_SHA256=" + digest,
            "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256=UNASSIGNED":
                "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256=" + "d" * 64,
            "SR6FI_GITHUB_ENVIRONMENT_PROTECTION=UNVERIFIED":
                "SR6FI_GITHUB_ENVIRONMENT_PROTECTION=VERIFIED",
            "SR6FI_SECRET_INVENTORY_STATUS=UNVERIFIED":
                "SR6FI_SECRET_INVENTORY_STATUS=VERIFIED_NAMES_ONLY",
            "SR6FI_EXECUTION_OPERATOR=UNASSIGNED": "SR6FI_EXECUTION_OPERATOR=operator-01",
            "SR6FI_REVOKE_OPERATOR=UNASSIGNED": "SR6FI_REVOKE_OPERATOR=revoke-operator-01",
            "SR6FI_INDEPENDENT_APPROVER=UNASSIGNED":
                "SR6FI_INDEPENDENT_APPROVER=approver-01",
            "SR6FI_INCIDENT_OWNER=UNASSIGNED": "SR6FI_INCIDENT_OWNER=incident-owner-01",
            "SR6FI_COST_OWNER=UNASSIGNED": "SR6FI_COST_OWNER=cost-owner-01",
            "SR6FI_TEARDOWN_OPERATOR=UNASSIGNED":
                "SR6FI_TEARDOWN_OPERATOR=teardown-operator-01",
            "SR6FI_TEARDOWN_DEADLINE_UTC=UNASSIGNED":
                "SR6FI_TEARDOWN_DEADLINE_UTC=2026-08-08T09:00:00Z",
            "SR6FI_EVIDENCE_STORE_TYPE=UNASSIGNED":
                "SR6FI_EVIDENCE_STORE_TYPE=IMMUTABLE_OBJECT_STORE",
            "SR6FI_EVIDENCE_STORE_RESOURCE_ID=UNASSIGNED":
                "SR6FI_EVIDENCE_STORE_RESOURCE_ID=search-ctr-evidence-store-01",
            "SR6FI_EVIDENCE_RETENTION_DAYS=UNASSIGNED":
                "SR6FI_EVIDENCE_RETENTION_DAYS=30",
        }
        text = TEMPLATE
        for old, new in replacements.items():
            self.assertIn(old, text)
            text = text.replace(old, new)
        return text

    def test_current_template_is_valid_and_blocked(self) -> None:
        root = self.make_repo()
        manifest = self.write_template(root)
        result = verify(manifest, "template", root)
        self.assertEqual("BLOCKED_PLATFORM_UNDECIDED", result.readiness_status)
        self.assertIn("FINAL_DEPLOYMENT_PLATFORM_UNDECIDED", result.blockers)
        self.assertEqual("UNASSIGNED", result.safe_binding["stageEndpointSha256"])

    def test_ready_mode_refuses_current_undecided_matrix(self) -> None:
        root = self.make_repo()
        manifest = self.write_template(root)
        with self.assertRaisesRegex(VerificationError, "authoritative platform matrix still blocks"):
            verify(manifest, "ready", root)

    def test_manifest_rejects_raw_endpoint_material(self) -> None:
        root = self.make_repo()
        text = TEMPLATE.replace(
            "SR6FI_DEPLOYMENT_RESOURCE_ID=UNASSIGNED",
            "SR6FI_DEPLOYMENT_RESOURCE_ID=https://stage.example.test/service",
        )
        manifest = self.write_template(root, text)
        with self.assertRaisesRegex(VerificationError, "endpoint or URI material"):
            verify(manifest, "template", root)

    def test_authorized_platform_can_render_review_only_binding(self) -> None:
        root = self.make_repo(ready_matrix=True)
        manifest = self.write_template(root, self.ready_manifest(root))
        result = verify(manifest, "ready", root)
        self.assertEqual("READY_FOR_SR6FH_BINDING", result.readiness_status)
        output = render(manifest, root / "safe-output")
        rendered = output.read_text(encoding="utf-8")
        self.assertIn("SR6FH_EXECUTION_STATUS=READY_FOR_ONE_SHOT", rendered)
        self.assertIn("SR6FH_BINDING_REVIEW_STATUS=PROPOSED_NOT_AUTHORIZED", rendered)
        self.assertIn("SR6FH_FINALITY_WRITE=DISABLED", rendered)
        self.assertNotIn("jdbc:postgresql://", rendered)
        self.assertNotIn("password", rendered.lower())


if __name__ == "__main__":
    unittest.main()
