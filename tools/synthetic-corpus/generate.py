#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "post-pf5.synthetic-corpus.v1"
MEMBERSHIP_STATUSES = ("OWNER", "PENDING", "APPROVED", "REJECTED", "CANCELLED")
ACTIVE_MEMBERSHIP_STATUSES = ("OWNER", "APPROVED")
NON_CAPACITY_STATUSES = ("PENDING", "REJECTED", "CANCELLED")
MIN_CAPACITY = 2
MAX_CAPACITY = 20

_ADJECTIVES = (
    "calm", "bright", "curious", "steady", "local", "quiet", "mobile", "open",
)
_NOUNS = (
    "walker", "planner", "roamer", "scout", "maker", "traveler", "mapper", "visitor",
)


class CorpusValidationError(ValueError):
    pass


def _require_positive(name: str, value: int, minimum: int = 1) -> None:
    if value < minimum:
        raise ValueError(f"{name} must be >= {minimum}, got {value}")


def _user_id(index: int) -> str:
    return f"synthetic-user-{index:04d}"


def _crew_id(index: int) -> str:
    return f"synthetic-crew-{index:04d}"


def _membership_id(index: int) -> str:
    return f"synthetic-membership-{index:05d}"


def generate_users(rng: random.Random, user_count: int) -> list[dict[str, Any]]:
    _require_positive("user_count", user_count, 5)
    users: list[dict[str, Any]] = []
    for index in range(1, user_count + 1):
        users.append(
            {
                "id": _user_id(index),
                "email": f"synthetic.user.{index:04d}@journey-connect.invalid",
                "nickname": f"{rng.choice(_ADJECTIVES)}-{rng.choice(_NOUNS)}-{index:04d}",
            }
        )
    return users


def generate_crews_and_memberships(
    rng: random.Random,
    users: list[dict[str, Any]],
    crew_count: int,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    _require_positive("crew_count", crew_count)
    if not users:
        raise ValueError("users must not be empty")

    user_ids = [user["id"] for user in users]
    crews: list[dict[str, Any]] = []
    memberships: list[dict[str, Any]] = []
    membership_index = 1

    for crew_index in range(1, crew_count + 1):
        crew_id = _crew_id(crew_index)
        owner_user_id = user_ids[(crew_index - 1) % len(user_ids)]
        capacity = rng.randint(MIN_CAPACITY, min(MAX_CAPACITY, max(MIN_CAPACITY, len(users))))
        crews.append(
            {
                "id": crew_id,
                "owner_user_id": owner_user_id,
                "capacity": capacity,
            }
        )

        memberships.append(
            {
                "id": _membership_id(membership_index),
                "crew_id": crew_id,
                "user_id": owner_user_id,
                "status": "OWNER",
            }
        )
        membership_index += 1

        candidates = [user_id for user_id in user_ids if user_id != owner_user_id]
        rng.shuffle(candidates)

        approved_count = min(capacity - 1, max(1, rng.randint(1, max(1, capacity - 1))))
        approved_ids = candidates[:approved_count]
        cursor = approved_count

        for user_id in approved_ids:
            memberships.append(
                {
                    "id": _membership_id(membership_index),
                    "crew_id": crew_id,
                    "user_id": user_id,
                    "status": "APPROVED",
                }
            )
            membership_index += 1

        # Non-capacity statuses are deliberately represented. They never reduce
        # available capacity and are kept distinct from ACTIVE_MEMBERSHIP_STATUSES.
        for status in NON_CAPACITY_STATUSES:
            if cursor >= len(candidates):
                break
            memberships.append(
                {
                    "id": _membership_id(membership_index),
                    "crew_id": crew_id,
                    "user_id": candidates[cursor],
                    "status": status,
                }
            )
            membership_index += 1
            cursor += 1

    return crews, memberships


def generate_social_edges(
    rng: random.Random,
    users: list[dict[str, Any]],
    social_edge_count: int,
) -> list[dict[str, str]]:
    if social_edge_count < 0:
        raise ValueError(f"social_edge_count must be >= 0, got {social_edge_count}")

    user_ids = sorted(user["id"] for user in users)
    pairs = [(left, right) for i, left in enumerate(user_ids) for right in user_ids[i + 1 :]]
    rng.shuffle(pairs)
    selected = pairs[: min(social_edge_count, len(pairs))]
    selected.sort()
    return [
        {"from_user_id": left, "to_user_id": right}
        for left, right in selected
    ]


def generate_corpus(
    seed: int = 20260827,
    user_count: int = 64,
    crew_count: int = 12,
    social_edge_count: int = 120,
) -> dict[str, Any]:
    rng = random.Random(seed)
    users = generate_users(rng, user_count)
    crews, memberships = generate_crews_and_memberships(rng, users, crew_count)
    social_edges = generate_social_edges(rng, users, social_edge_count)

    corpus = {
        "metadata": {
            "schema_version": SCHEMA_VERSION,
            "seed": seed,
            "crew_contract": {
                "membership_statuses": list(MEMBERSHIP_STATUSES),
                "active_capacity_statuses": list(ACTIVE_MEMBERSHIP_STATUSES),
                "non_capacity_statuses": list(NON_CAPACITY_STATUSES),
                "capacity_range": [MIN_CAPACITY, MAX_CAPACITY],
            },
            "allocation_boundary": {
                "pf6": "UNALLOCATED",
                "sql63_plus": "UNALLOCATED",
            },
        },
        "users": users,
        "crews": crews,
        "memberships": memberships,
        "social_edges": social_edges,
    }
    assert_valid(corpus)
    return corpus


def validate_corpus(corpus: dict[str, Any]) -> list[str]:
    errors: list[str] = []

    metadata = corpus.get("metadata")
    users = corpus.get("users")
    crews = corpus.get("crews")
    memberships = corpus.get("memberships")
    social_edges = corpus.get("social_edges")

    if not isinstance(metadata, dict):
        errors.append("metadata must be an object")
        metadata = {}
    if metadata.get("schema_version") != SCHEMA_VERSION:
        errors.append(f"metadata.schema_version must be {SCHEMA_VERSION}")

    contract = metadata.get("crew_contract") if isinstance(metadata, dict) else None
    expected_contract = {
        "membership_statuses": list(MEMBERSHIP_STATUSES),
        "active_capacity_statuses": list(ACTIVE_MEMBERSHIP_STATUSES),
        "non_capacity_statuses": list(NON_CAPACITY_STATUSES),
        "capacity_range": [MIN_CAPACITY, MAX_CAPACITY],
    }
    if contract != expected_contract:
        errors.append("metadata.crew_contract does not match the post-PF5 Crew contract")

    boundary = metadata.get("allocation_boundary") if isinstance(metadata, dict) else None
    if boundary != {"pf6": "UNALLOCATED", "sql63_plus": "UNALLOCATED"}:
        errors.append("metadata.allocation_boundary must keep PF6 and SQL63+ unallocated")

    if not isinstance(users, list):
        errors.append("users must be an array")
        users = []
    if not isinstance(crews, list):
        errors.append("crews must be an array")
        crews = []
    if not isinstance(memberships, list):
        errors.append("memberships must be an array")
        memberships = []
    if not isinstance(social_edges, list):
        errors.append("social_edges must be an array")
        social_edges = []

    user_ids = [user.get("id") for user in users if isinstance(user, dict)]
    user_id_set = set(user_ids)
    if len(user_ids) != len(user_id_set):
        errors.append("user ids must be unique")
    if any(not isinstance(user_id, str) or not user_id.startswith("synthetic-user-") for user_id in user_ids):
        errors.append("all user ids must use the synthetic-user-* identity boundary")

    emails = [user.get("email") for user in users if isinstance(user, dict)]
    if len(emails) != len(set(emails)):
        errors.append("user emails must be unique")
    if any(not isinstance(email, str) or not email.endswith("@journey-connect.invalid") for email in emails):
        errors.append("all user emails must use the non-routable journey-connect.invalid domain")

    crew_ids = [crew.get("id") for crew in crews if isinstance(crew, dict)]
    crew_id_set = set(crew_ids)
    if len(crew_ids) != len(crew_id_set):
        errors.append("crew ids must be unique")

    crews_by_id: dict[str, dict[str, Any]] = {}
    for crew in crews:
        if not isinstance(crew, dict):
            errors.append("each crew must be an object")
            continue
        crew_id = crew.get("id")
        if not isinstance(crew_id, str):
            errors.append("each crew must have a string id")
            continue
        crews_by_id[crew_id] = crew
        owner_user_id = crew.get("owner_user_id")
        if owner_user_id not in user_id_set:
            errors.append(f"crew {crew_id} references missing owner {owner_user_id}")
        capacity = crew.get("capacity")
        if not isinstance(capacity, int) or not MIN_CAPACITY <= capacity <= MAX_CAPACITY:
            errors.append(
                f"crew {crew_id} capacity must be between {MIN_CAPACITY} and {MAX_CAPACITY}"
            )

    membership_ids: set[str] = set()
    membership_pairs: set[tuple[str, str]] = set()
    memberships_by_crew: dict[str, list[dict[str, Any]]] = defaultdict(list)

    for membership in memberships:
        if not isinstance(membership, dict):
            errors.append("each membership must be an object")
            continue
        membership_id = membership.get("id")
        crew_id = membership.get("crew_id")
        user_id = membership.get("user_id")
        status = membership.get("status")

        if not isinstance(membership_id, str):
            errors.append("each membership must have a string id")
        elif membership_id in membership_ids:
            errors.append(f"duplicate membership id: {membership_id}")
        else:
            membership_ids.add(membership_id)

        if crew_id not in crew_id_set:
            errors.append(f"membership {membership_id} references missing crew {crew_id}")
        if user_id not in user_id_set:
            errors.append(f"membership {membership_id} references missing user {user_id}")
        if status not in MEMBERSHIP_STATUSES:
            errors.append(f"membership {membership_id} has invalid status {status}")

        if isinstance(crew_id, str) and isinstance(user_id, str):
            pair = (crew_id, user_id)
            if pair in membership_pairs:
                errors.append(f"duplicate crew/user membership: {crew_id}/{user_id}")
            else:
                membership_pairs.add(pair)
        if isinstance(crew_id, str):
            memberships_by_crew[crew_id].append(membership)

    for crew_id, crew in crews_by_id.items():
        crew_memberships = memberships_by_crew.get(crew_id, [])
        owner_user_id = crew.get("owner_user_id")
        owner_memberships = [
            membership
            for membership in crew_memberships
            if membership.get("status") == "OWNER"
        ]
        if len(owner_memberships) != 1:
            errors.append(f"crew {crew_id} must have exactly one OWNER membership")
        elif owner_memberships[0].get("user_id") != owner_user_id:
            errors.append(f"crew {crew_id} OWNER membership must match owner_user_id")

        active_count = sum(
            1
            for membership in crew_memberships
            if membership.get("status") in ACTIVE_MEMBERSHIP_STATUSES
        )
        capacity = crew.get("capacity")
        if isinstance(capacity, int) and active_count > capacity:
            errors.append(
                f"crew {crew_id} active membership count {active_count} exceeds capacity {capacity}"
            )

    edge_keys: set[tuple[str, str]] = set()
    for edge in social_edges:
        if not isinstance(edge, dict):
            errors.append("each social edge must be an object")
            continue
        left = edge.get("from_user_id")
        right = edge.get("to_user_id")
        if left not in user_id_set:
            errors.append(f"social edge references missing user {left}")
        if right not in user_id_set:
            errors.append(f"social edge references missing user {right}")
        if left == right:
            errors.append(f"social edge must not be self-referential: {left}")
        if isinstance(left, str) and isinstance(right, str):
            if left >= right:
                errors.append(f"social edge must be canonical (from < to): {left}/{right}")
            key = (left, right)
            if key in edge_keys:
                errors.append(f"duplicate social edge: {left}/{right}")
            else:
                edge_keys.add(key)

    return errors


def assert_valid(corpus: dict[str, Any]) -> None:
    errors = validate_corpus(corpus)
    if errors:
        raise CorpusValidationError("\n".join(errors))


def canonical_bytes(corpus: dict[str, Any]) -> bytes:
    assert_valid(corpus)
    return (
        json.dumps(corpus, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def write_corpus(path: Path, corpus: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_bytes(corpus))


def load_corpus(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise CorpusValidationError("corpus root must be an object")
    return value


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate or validate a deterministic post-PF5 synthetic corpus."
    )
    parser.add_argument("--seed", type=int, default=20260827)
    parser.add_argument("--users", type=int, default=64)
    parser.add_argument("--crews", type=int, default=12)
    parser.add_argument("--social-edges", type=int, default=120)
    parser.add_argument("--output", type=Path, default=Path("build/synthetic-corpus/corpus.json"))
    parser.add_argument(
        "--validate-only",
        type=Path,
        help="Validate an existing corpus instead of generating a new one.",
    )
    return parser


def main() -> int:
    args = _parser().parse_args()
    if args.validate_only is not None:
        corpus = load_corpus(args.validate_only)
        assert_valid(corpus)
        print(f"valid: {args.validate_only}")
        return 0

    corpus = generate_corpus(
        seed=args.seed,
        user_count=args.users,
        crew_count=args.crews,
        social_edge_count=args.social_edges,
    )
    write_corpus(args.output, corpus)
    counts = Counter(membership["status"] for membership in corpus["memberships"])
    print(
        f"generated: {args.output} users={len(corpus['users'])} crews={len(corpus['crews'])} "
        f"memberships={len(corpus['memberships'])} edges={len(corpus['social_edges'])} "
        f"statuses={dict(sorted(counts.items()))}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
