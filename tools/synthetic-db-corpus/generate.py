#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
from datetime import datetime
from pathlib import Path

import legacy_generate as legacy

TARGET_REPOSITORY = "YTAK99/Journey-Connect"
TARGET_BRANCH = "develop"
TARGET_SCHEMA_COMMIT = "e05e6167de59c0eaf93fa466451c3fde0d43f14e"
DEFAULT_ANCHOR = datetime(2026, 9, 1, 10, 0, 0)
CURRENT_CREW_STATUSES = {"OWNER", "PENDING", "APPROVED", "REJECTED", "CANCELLED"}


def _nickname_suffix(batch_id: str) -> str:
    return hashlib.sha1(batch_id.encode("utf-8")).hexdigest()[:4]


def _make_batch_safe(users: list[dict], batch_id: str) -> None:
    suffix = _nickname_suffix(batch_id)
    for user in users:
        user["nickname"] = f"{user['nickname'][:35]}-{suffix}"[:40]


def _destination(code: str):
    return next(destination for destination in legacy.DS if destination.code == code)


def _places_for_post(post: dict) -> list[dict]:
    destination = _destination(post["region_code"])
    stops = post.get("route_stops") or []
    if not stops:
        return [
            {
                "place": post["place"],
                "lat": destination.lat,
                "lon": destination.lon,
            }
        ]
    return [
        {
            "place": stop["place"],
            "lat": stop["lat"],
            "lon": stop["lon"],
        }
        for stop in stops
    ]


def post_place_count(posts: list[dict]) -> int:
    return sum(len(_places_for_post(post)) for post in posts)


def _render_v19_post_places(posts: list[dict]) -> str:
    out = ["-- V19 compatibility: materialize post_place and bind post_image.place_id."]
    for post in posts:
        post_key = legacy.q(post["key"])
        region_code = legacy.q(post["region_code"])
        places = _places_for_post(post)
        for sort_order, place in enumerate(places):
            out.append(
                "INSERT INTO post_place(post_id,region_id,place_name,latitude,longitude,content,sort_order) "
                f"SELECT s.id,r.id,{legacy.q(place['place'])},{place['lat']},{place['lon']},"
                f"{legacy.q(place['place'] + ' synthetic route stop')},{sort_order} "
                f"FROM _sp s JOIN region r ON r.code={region_code} WHERE s.k={post_key};"
            )
        out.append(
            "UPDATE post_image i SET place_id=pp.id FROM _sp s, post_place pp "
            f"WHERE s.k={post_key} AND i.post_id=s.id AND pp.post_id=s.id "
            f"AND pp.sort_order=(i.sort_order % {len(places)});"
        )
    return "\n".join(out) + "\n"


def render_sql(batch_id: str, users: list[dict], posts: list[dict], crews: list[dict], anchor: datetime) -> str:
    base = legacy.render_sql(batch_id, users, posts, crews, anchor)
    marker = "COMMIT;\n"
    if not base.endswith(marker):
        raise ValueError("legacy SQL no longer ends in COMMIT")
    return base[: -len(marker)] + _render_v19_post_places(posts) + marker


def validate_current(users: list[dict], posts: list[dict], crews: list[dict]) -> None:
    legacy.validate(users, posts, crews)
    if len({user["nickname"] for user in users}) != len(users):
        raise ValueError("batch-safe nicknames must remain unique")
    for crew in crews:
        statuses = {"OWNER", *(member["status"] for member in crew["members"])}
        if not statuses <= CURRENT_CREW_STATUSES:
            raise ValueError(f"unsupported Crew status: {statuses - CURRENT_CREW_STATUSES}")
    if any(not _places_for_post(post) for post in posts):
        raise ValueError("every post must materialize at least one post_place")


def generate_data(
    *,
    seed: int = 20260825,
    batch_id: str = "demo-v2",
    users: int = 180,
    posts: int = 1800,
    crews: int = 120,
    anchor: datetime = DEFAULT_ANCHOR,
):
    rng = random.Random(seed)
    user_rows = legacy.users(rng, batch_id, users)
    _make_batch_safe(user_rows, batch_id)
    post_rows = legacy.posts(rng, batch_id, user_rows, posts, anchor)
    crew_rows = legacy.crews(rng, batch_id, user_rows, crews, anchor)
    validate_current(user_rows, post_rows, crew_rows)
    return user_rows, post_rows, crew_rows


def write_outputs(
    output_dir: Path,
    *,
    seed: int,
    batch_id: str,
    users: list[dict],
    posts: list[dict],
    crews: list[dict],
    anchor: datetime,
) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    seed_sql = render_sql(batch_id, users, posts, crews, anchor)
    purge_sql = legacy.purge(batch_id)
    manifest = {
        "schemaVersion": 2,
        "batchId": batch_id,
        "seed": seed,
        "anchor": anchor.isoformat(),
        "target": {
            "repository": TARGET_REPOSITORY,
            "branch": TARGET_BRANCH,
            "schemaCommit": TARGET_SCHEMA_COMMIT,
            "migrationRange": "V1..V19",
        },
        "counts": {
            "users": len(users),
            "posts": len(posts),
            "postImages": sum(len(post["images"]) for post in posts),
            "postPlaces": post_place_count(posts),
            "crews": len(crews),
        },
        "destinations": [legacy.asdict(destination) for destination in legacy.DS],
        "users": users,
        "posts": posts,
        "crews": crews,
    }
    (output_dir / "seed.sql").write_text(seed_sql, encoding="utf-8")
    (output_dir / "purge.sql").write_text(purge_sql, encoding="utf-8")
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate the restored Journey Connect synthetic DB corpus aligned to team develop V1..V19."
    )
    parser.add_argument("--seed", type=int, default=20260825)
    parser.add_argument("--batch-id", default="demo-v2")
    parser.add_argument("--users", type=int, default=180)
    parser.add_argument("--posts", type=int, default=1800)
    parser.add_argument("--crews", type=int, default=120)
    parser.add_argument("--anchor", default=DEFAULT_ANCHOR.isoformat())
    parser.add_argument("--output-dir", default="build/synthetic-db-corpus")
    args = parser.parse_args()
    if args.users < 10 or args.posts < 1 or args.crews < 0:
        parser.error("users>=10, posts>=1, crews>=0 required")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,30}", args.batch_id):
        parser.error("invalid batch-id")

    anchor = datetime.fromisoformat(args.anchor)
    users, posts, crews = generate_data(
        seed=args.seed,
        batch_id=args.batch_id,
        users=args.users,
        posts=args.posts,
        crews=args.crews,
        anchor=anchor,
    )
    manifest = write_outputs(
        Path(args.output_dir),
        seed=args.seed,
        batch_id=args.batch_id,
        users=users,
        posts=posts,
        crews=crews,
        anchor=anchor,
    )
    print(f"generated {manifest['counts']} -> {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
