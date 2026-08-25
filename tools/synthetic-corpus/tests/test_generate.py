import importlib.util
import sys
import unittest
from datetime import datetime
from pathlib import Path

P = Path(__file__).resolve().parents[1] / 'generate.py'
spec = importlib.util.spec_from_file_location('synthetic_generate', P)
mod = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = mod
spec.loader.exec_module(mod)


class SyntheticCorpusTest(unittest.TestCase):
    def build(self, seed=20260825):
        rng = mod.random.Random(seed)
        users = mod.users(rng, 'test', 20)
        posts = mod.posts(rng, 'test', users, 60, datetime(2026, 8, 25, 10))
        crews = mod.crews(rng, 'test', users, 12, datetime(2026, 8, 25, 10))
        mod.validate(users, posts, crews)
        return users, posts, crews

    def test_deterministic(self):
        self.assertEqual(self.build(), self.build())

    def test_identity_boundary(self):
        users, _, _ = self.build()
        self.assertEqual(len(users), len({item['email'] for item in users}))
        self.assertTrue(all(item['email'].startswith('synthetic.test.') for item in users))

    def test_destinations(self):
        _, posts, _ = self.build()
        codes = {destination.code for destination in mod.DS}
        self.assertTrue({item['region_code'] for item in posts}.issubset(codes))
        self.assertIn('KR-SEOUL', codes)
        self.assertIn('TH-BKK', codes)
        self.assertIn('GU-GUM', codes)
        self.assertIn('ES-BCN', codes)
        self.assertIn('IT-ROM', codes)

    def test_route_manifest(self):
        _, posts, _ = self.build()
        routed = [item for item in posts if item['route_stops']]
        self.assertTrue(routed)
        self.assertTrue(all(2 <= len(item['route_stops']) <= 6 for item in routed))

    def test_sql_contract(self):
        users, posts, crews = self.build()
        self.assertTrue(any(item['bookmark_user_emails'] for item in posts))
        sql = mod.render_sql('test', users, posts, crews[:2], datetime(2026, 8, 25, 10))
        for table in [
            'user_account',
            'journey_post',
            'post_image',
            'post_tag',
            'post_like',
            'bookmark',
            'post_comment',
            'crew',
            'crew_member',
            'crew_tag',
        ]:
            self.assertIn(table, sql)
        self.assertIn('synthetic.test.%@journey-connect.local', sql)


if __name__ == '__main__':
    unittest.main()
