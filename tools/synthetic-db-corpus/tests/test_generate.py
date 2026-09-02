from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import generate


class SyntheticDbCorpusTest(unittest.TestCase):
    def rows(self, batch_id="test", seed=123):
        return generate.generate_data(
            seed=seed,
            batch_id=batch_id,
            users=20,
            posts=50,
            crews=10,
        )

    def test_deterministic_generation(self):
        self.assertEqual(self.rows(), self.rows())
        self.assertNotEqual(self.rows(seed=123), self.rows(seed=124))

    def test_target_and_destinations(self):
        self.assertEqual(
            generate.TARGET_SCHEMA_COMMIT,
            "e05e6167de59c0eaf93fa466451c3fde0d43f14e",
        )
        self.assertEqual(len(generate.legacy.DS), 24)
        self.assertIn("US-HNL", {row.code for row in generate.legacy.DS})

    def test_team_v19_place_materialization(self):
        users, posts, crews = self.rows()
        self.assertGreaterEqual(generate.post_place_count(posts), len(posts))
        sql = generate.render_sql(
            "test",
            users,
            posts,
            crews,
            generate.DEFAULT_ANCHOR,
            schema_profile=generate.SCHEMA_PROFILE_TEAM_V19,
        )
        self.assertIn("INSERT INTO post_place", sql)
        self.assertIn("UPDATE post_image i SET place_id=pp.id", sql)
        self.assertIn("INSERT INTO crew_tag", sql)
        self.assertNotIn("INSERT INTO recommendation_", sql)

    def test_local_pre_v19_omits_v19_only_sql(self):
        users, posts, crews = self.rows()
        sql = generate.render_sql(
            "test",
            users,
            posts,
            crews,
            generate.DEFAULT_ANCHOR,
            schema_profile=generate.SCHEMA_PROFILE_LOCAL_PRE_V19,
        )
        self.assertNotIn("INSERT INTO post_place", sql)
        self.assertNotIn("UPDATE post_image i SET place_id=pp.id", sql)
        self.assertIn("INSERT INTO crew_tag", sql)
        self.assertTrue(sql.endswith("COMMIT;\n"))

    def test_local_manifest_records_profile_and_zero_materialized_places(self):
        users, posts, crews = self.rows()
        with tempfile.TemporaryDirectory() as tmp:
            manifest = generate.write_outputs(
                Path(tmp),
                seed=123,
                batch_id="test",
                users=users,
                posts=posts,
                crews=crews,
                anchor=generate.DEFAULT_ANCHOR,
                schema_profile=generate.SCHEMA_PROFILE_LOCAL_PRE_V19,
            )
            self.assertEqual(
                manifest["schemaProfile"], generate.SCHEMA_PROFILE_LOCAL_PRE_V19
            )
            self.assertEqual(manifest["counts"]["postPlaces"], 0)
            self.assertFalse(manifest["target"]["postPlaceMaterialization"])
            saved = json.loads((Path(tmp) / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(saved["schemaProfile"], "local-pre-v19")

    def test_batch_safe_nicknames(self):
        alpha, _, _ = self.rows(batch_id="alpha")
        beta, _, _ = self.rows(batch_id="beta")
        self.assertTrue(
            {user["nickname"] for user in alpha}.isdisjoint(
                {user["nickname"] for user in beta}
            )
        )

    def test_crew_statuses_are_current(self):
        _, _, crews = self.rows()
        for crew in crews:
            for member in crew["members"]:
                self.assertIn(member["status"], generate.CURRENT_CREW_STATUSES)


if __name__ == "__main__":
    unittest.main()