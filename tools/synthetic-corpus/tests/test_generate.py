import copy
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "generate.py"
SPEC = importlib.util.spec_from_file_location("post_pf5_synthetic_generate", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class SyntheticCorpusConformanceTest(unittest.TestCase):
    def build(self, seed=20260827):
        return MODULE.generate_corpus(
            seed=seed,
            user_count=30,
            crew_count=6,
            social_edge_count=40,
        )

    def test_same_seed_is_byte_deterministic(self):
        left = MODULE.canonical_bytes(self.build())
        right = MODULE.canonical_bytes(self.build())
        self.assertEqual(left, right)

    def test_different_seed_changes_corpus(self):
        self.assertNotEqual(
            MODULE.canonical_bytes(self.build(20260827)),
            MODULE.canonical_bytes(self.build(20260828)),
        )

    def test_current_crew_membership_contract_is_exact(self):
        self.assertEqual(
            MODULE.MEMBERSHIP_STATUSES,
            ("OWNER", "PENDING", "APPROVED", "REJECTED", "CANCELLED"),
        )
        self.assertEqual(MODULE.ACTIVE_MEMBERSHIP_STATUSES, ("OWNER", "APPROVED"))
        self.assertEqual(MODULE.NON_CAPACITY_STATUSES, ("PENDING", "REJECTED", "CANCELLED"))
        self.assertEqual((MODULE.MIN_CAPACITY, MODULE.MAX_CAPACITY), (2, 20))

        generated_statuses = {item["status"] for item in self.build()["memberships"]}
        self.assertEqual(generated_statuses, set(MODULE.MEMBERSHIP_STATUSES))

    def test_pending_does_not_consume_capacity(self):
        corpus = self.build()
        crew = corpus["crews"][0]
        crew_memberships = [
            membership
            for membership in corpus["memberships"]
            if membership["crew_id"] == crew["id"]
        ]
        active_count = sum(
            membership["status"] in MODULE.ACTIVE_MEMBERSHIP_STATUSES
            for membership in crew_memberships
        )
        crew["capacity"] = active_count
        self.assertEqual(MODULE.validate_corpus(corpus), [])

        pending = next(
            membership for membership in crew_memberships if membership["status"] == "PENDING"
        )
        pending["status"] = "APPROVED"
        self.assertTrue(
            any("exceeds capacity" in error for error in MODULE.validate_corpus(corpus))
        )

    def test_every_crew_respects_active_capacity(self):
        corpus = self.build()
        for crew in corpus["crews"]:
            active_count = sum(
                membership["crew_id"] == crew["id"]
                and membership["status"] in MODULE.ACTIVE_MEMBERSHIP_STATUSES
                for membership in corpus["memberships"]
            )
            self.assertLessEqual(active_count, crew["capacity"])

    def test_social_graph_has_canonical_unique_resolved_edges(self):
        corpus = self.build()
        user_ids = {user["id"] for user in corpus["users"]}
        seen = set()
        for edge in corpus["social_edges"]:
            left = edge["from_user_id"]
            right = edge["to_user_id"]
            self.assertIn(left, user_ids)
            self.assertIn(right, user_ids)
            self.assertLess(left, right)
            self.assertNotIn((left, right), seen)
            seen.add((left, right))

    def test_validator_rejects_invalid_status(self):
        corpus = self.build()
        corpus["memberships"][0]["status"] = "WAITING"
        errors = MODULE.validate_corpus(corpus)
        self.assertTrue(any("invalid status WAITING" in error for error in errors))

    def test_validator_rejects_duplicate_edge_and_missing_reference(self):
        corpus = self.build()
        duplicate = copy.deepcopy(corpus["social_edges"][0])
        corpus["social_edges"].append(duplicate)
        errors = MODULE.validate_corpus(corpus)
        self.assertTrue(any("duplicate social edge" in error for error in errors))

        corpus = self.build()
        corpus["social_edges"][0]["from_user_id"] = "synthetic-user-missing"
        errors = MODULE.validate_corpus(corpus)
        self.assertTrue(any("missing user synthetic-user-missing" in error for error in errors))

    def test_validator_rejects_owner_contract_break(self):
        corpus = self.build()
        first_crew = corpus["crews"][0]
        owner_membership = next(
            membership
            for membership in corpus["memberships"]
            if membership["crew_id"] == first_crew["id"] and membership["status"] == "OWNER"
        )
        owner_membership["status"] = "APPROVED"
        errors = MODULE.validate_corpus(corpus)
        self.assertTrue(any("exactly one OWNER" in error for error in errors))

    def test_cli_generate_then_validate(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "corpus.json"
            command = [
                sys.executable,
                str(MODULE_PATH),
                "--seed",
                "42",
                "--users",
                "20",
                "--crews",
                "4",
                "--social-edges",
                "20",
                "--output",
                str(output),
            ]
            first = subprocess.run(command, check=True, capture_output=True, text=True)
            first_bytes = output.read_bytes()
            second = subprocess.run(command, check=True, capture_output=True, text=True)
            second_bytes = output.read_bytes()
            self.assertEqual(first_bytes, second_bytes)
            self.assertIn("generated:", first.stdout)
            self.assertIn("generated:", second.stdout)

            validate = subprocess.run(
                [sys.executable, str(MODULE_PATH), "--validate-only", str(output)],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertIn("valid:", validate.stdout)
            parsed = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(parsed["metadata"]["allocation_boundary"]["pf6"], "UNALLOCATED")
            self.assertEqual(parsed["metadata"]["allocation_boundary"]["sql63_plus"], "UNALLOCATED")


if __name__ == "__main__":
    unittest.main()
