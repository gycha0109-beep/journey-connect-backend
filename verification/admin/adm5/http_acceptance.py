#!/usr/bin/env python3
"""ADM-5 live HTTP acceptance against an ephemeral canonical PostgreSQL/backend."""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

BASE = os.environ.get("ADM5_BACKEND_URL", "http://127.0.0.1:8080").rstrip("/")
DB_HOST = os.environ.get("PGHOST", "127.0.0.1")
DB_PORT = os.environ.get("PGPORT", "5432")
DB_NAME = os.environ.get("PGDATABASE", "journey_connect")
DB_USER = os.environ.get("PGUSER", "postgres")
JWT_SECRET = os.environ["JWT_SECRET"]
PASSWORD = os.environ.get("ADM5_FIXTURE_PASSWORD", "Adm5-local-only-Password-42!")
OUT = Path(os.environ.get("ADM5_HTTP_EVIDENCE", "verification/admin/adm5/evidence/adm5-http-acceptance.json"))
FIXTURE_OUT = Path(os.environ.get("ADM5_BROWSER_FIXTURE", "/tmp/adm5-browser-fixture.json"))

checks: dict[str, Any] = {}
fixture: dict[str, Any] = {"backend": BASE, "password": PASSWORD}


def request(method: str, path: str, token: str | None = None, body: Any | None = None) -> tuple[int, Any]:
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode()
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = {"unparsed": True}
        return exc.code, payload


def data(payload: Any) -> Any:
    return payload.get("data") if isinstance(payload, dict) and "data" in payload else payload


def expect(name: str, actual: Any, expected: Any) -> None:
    ok = actual == expected
    checks[name] = {"status": "PASS" if ok else "FAIL", "actual": actual, "expected": expected}
    if not ok:
        raise AssertionError(f"{name}: expected {expected!r}, got {actual!r}")


def expect_true(name: str, condition: bool, detail: Any = None) -> None:
    checks[name] = {"status": "PASS" if condition else "FAIL", "detail": detail}
    if not condition:
        raise AssertionError(f"{name}: {detail!r}")


def sql(statement: str) -> str:
    env = os.environ.copy()
    result = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1", "-At", "-h", DB_HOST, "-p", DB_PORT, "-U", DB_USER, "-d", DB_NAME, "-c", statement],
        check=True, text=True, capture_output=True, env=env,
    )
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    return lines[0].strip() if lines else ""


def signup(email: str, nickname: str) -> dict[str, Any]:
    status, payload = request("POST", "/api/v1/auth/signup", body={"email": email, "password": PASSWORD, "nickname": nickname})
    expect(f"signup_{nickname}", status, 201)
    return data(payload)


def login(email: str) -> dict[str, Any]:
    status, payload = request("POST", "/api/v1/auth/login", body={"email": email, "password": PASSWORD})
    expect(f"login_{email}", status, 200)
    return data(payload)


def user_id(email: str) -> int:
    return int(sql(f"select id from public.app_users where email = '{email}'"))


def set_user(email: str, *, role: str | None = None, status: str | None = None) -> None:
    assignments = []
    if role:
        assignments.append(f"role = '{role}'")
    if status:
        assignments.append(f"account_status = '{status}'")
    sql(f"update public.app_users set {', '.join(assignments)} where email = '{email}'")


def jwt_payload(token: str) -> dict[str, Any]:
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def expired_token(subject: int) -> str:
    def enc(value: bytes) -> str:
        return base64.urlsafe_b64encode(value).rstrip(b"=").decode()
    header = enc(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    now = int(time.time())
    payload = enc(json.dumps({"iss": "journey-connect", "sub": str(subject), "role": "admin", "iat": now - 7200, "exp": now - 3600, "jti": "adm5-expired"}, separators=(",", ":")).encode())
    signing = f"{header}.{payload}".encode()
    signature = enc(hmac.new(JWT_SECRET.encode(), signing, hashlib.sha256).digest())
    return f"{header}.{payload}.{signature}"


def command(path: str, token: str, reason: str) -> tuple[int, Any]:
    return request("POST", path, token, {"reason": reason})


def safe_code(payload: Any) -> str | None:
    return payload.get("code") if isinstance(payload, dict) else None


def wait_backend() -> None:
    for _ in range(120):
        status, _ = request("GET", "/api/v1/regions")
        if status in (200, 401, 403):
            return
        time.sleep(1)
    raise RuntimeError("backend did not become ready")


def main() -> None:
    wait_backend()
    emails = {
        "admin": "adm5.admin@example.test",
        "normal": "adm5.normal@example.test",
        "target": "adm5.target@example.test",
        "suspended_admin": "adm5.suspended.admin@example.test",
        "withdrawn_admin": "adm5.withdrawn.admin@example.test",
        "demoted": "adm5.demoted@example.test",
        "promoted_stale": "adm5.promoted.stale@example.test",
        "missing": "adm5.missing@example.test",
    }
    for key, email in emails.items():
        signup(email, f"adm5_{key}")

    # Role transition requires a new token: retain the pre-promotion user token.
    stale_user_token = login(emails["admin"])["accessToken"]
    set_user(emails["admin"], role="admin", status="active")
    admin_login = login(emails["admin"])
    admin_token = admin_login["accessToken"]
    admin_refresh = admin_login["refreshToken"]
    admin_id = user_id(emails["admin"])
    expect_true("admin_token_contains_admin_role", jwt_payload(admin_token).get("role") == "admin")
    expect("stale_pre_promotion_token_forbidden", request("GET", "/api/admin/dashboard", stale_user_token)[0], 403)
    expect("active_admin_allowed", request("GET", "/api/admin/dashboard", admin_token)[0], 200)

    normal_login = login(emails["normal"])
    normal_token = normal_login["accessToken"]
    normal_id = user_id(emails["normal"])
    demoted_id = user_id(emails["demoted"])
    target_id = user_id(emails["target"])

    # Authorization matrix.
    for route in ("/api/admin/dashboard", "/api/admin/reports", "/api/admin/posts", "/api/admin/users"):
        expect(f"anonymous_{route}", request("GET", route)[0], 401)
        expect(f"normal_user_{route}", request("GET", route, normal_token)[0], 403)

    set_user(emails["suspended_admin"], role="admin", status="active")
    suspended_token = login(emails["suspended_admin"])["accessToken"]
    set_user(emails["suspended_admin"], status="suspended")
    expect("suspended_admin_denied", request("GET", "/api/admin/dashboard", suspended_token)[0], 403)

    set_user(emails["withdrawn_admin"], role="admin", status="active")
    withdrawn_token = login(emails["withdrawn_admin"])["accessToken"]
    set_user(emails["withdrawn_admin"], status="withdrawn")
    expect("withdrawn_admin_denied", request("GET", "/api/admin/dashboard", withdrawn_token)[0], 403)

    set_user(emails["demoted"], role="admin", status="active")
    demoted_token = login(emails["demoted"])["accessToken"]
    set_user(emails["demoted"], role="user")
    expect("jwt_admin_db_user_denied", request("GET", "/api/admin/dashboard", demoted_token)[0], 403)

    stale_promoted_token = login(emails["promoted_stale"])["accessToken"]
    set_user(emails["promoted_stale"], role="admin", status="active")
    expect("jwt_user_db_admin_denied", request("GET", "/api/admin/dashboard", stale_promoted_token)[0], 403)

    set_user(emails["missing"], role="admin", status="active")
    missing_token = login(emails["missing"])["accessToken"]
    sql(f"delete from public.refresh_tokens where user_id = {user_id(emails['missing'])}; delete from public.app_users where email = '{emails['missing']}'")
    expect("jwt_admin_missing_db_user_denied", request("GET", "/api/admin/dashboard", missing_token)[0], 403)
    expect("expired_token_handled", request("GET", "/api/admin/dashboard", expired_token(admin_id))[0], 401)

    # Reproducible local-only content fixtures.
    region_id = int(sql("select id from public.regions where slug = 'kr-seoul'"))
    place_id = int(sql(
        f"insert into public.places(region_id,name_local,name_ko,name_en,address,latitude,longitude,category,is_active) "
        f"values ({region_id},'ADM5 Demo Place','ADM5 시연 장소','ADM5 Demo Place','Local-only fixture',37.566500,126.978000,'demo',true) returning id"
    ))
    post_id = int(sql(
        f"insert into public.posts(author_id, main_region_id, title, content, visibility, status) values ({normal_id}, {region_id}, 'ADM5 visible post', 'ADM5 integration content', 'public', 'draft') returning id"
    ))
    sql(f"insert into public.post_places(post_id, place_id, sort_order) values ({post_id}, {place_id}, 0); update public.posts set status='published' where id={post_id}")
    report1 = int(sql(
        f"insert into public.reports(reporter_id,target_type,target_entity_id,target_post_id,target_snapshot,reason_category,reason_detail) values ({normal_id},'post',{post_id},{post_id},jsonb_build_object('type','post','id','{post_id}'),'spam','ADM5 resolve fixture') returning id"
    ))
    report2 = int(sql(
        f"insert into public.reports(reporter_id,target_type,target_entity_id,target_post_id,target_snapshot,reason_category,reason_detail) values ({target_id},'post',{post_id},{post_id},jsonb_build_object('type','post','id','{post_id}'),'other','ADM5 dismiss fixture') returning id"
    ))
    report3 = int(sql(
        f"insert into public.reports(reporter_id,target_type,target_entity_id,target_post_id,target_snapshot,reason_category,reason_detail) values ({demoted_id},'post',{post_id},{post_id},jsonb_build_object('type','post','id','{post_id}'),'privacy','ADM5 browser fixture') returning id"
    ))

    dash_before = data(request("GET", "/api/admin/dashboard", admin_token)[1])
    expect_true("dashboard_initial_counts", dash_before["pendingReportCount"] >= 3 and dash_before["activePostCount"] >= 1, dash_before)

    # All 13 endpoints.
    endpoint_reads = [
        "/api/admin/dashboard", "/api/admin/reports", f"/api/admin/reports/{report1}",
        "/api/admin/posts", f"/api/admin/posts/{post_id}", "/api/admin/users", f"/api/admin/users/{target_id}",
    ]
    for path in endpoint_reads:
        expect(f"endpoint_{path}", request("GET", path, admin_token)[0], 200)

    # Report resolve/dismiss, idempotency, terminal conflicts.
    status, payload = command(f"/api/admin/reports/{report1}/resolve", admin_token, "  resolved in ADM5  ")
    expect("report_resolve_status", status, 200); expect_true("report_resolve_changed", data(payload)["changed"] is True)
    status, payload = command(f"/api/admin/reports/{report1}/resolve", admin_token, "same state")
    expect("report_resolve_changed_false_status", status, 200); expect_true("report_resolve_changed_false", data(payload)["changed"] is False)
    status, payload = command(f"/api/admin/reports/{report1}/dismiss", admin_token, "opposite terminal")
    expect("resolved_report_dismiss_conflict", status, 409); expect("resolved_report_dismiss_code", safe_code(payload), "ADMIN_STATE_CONFLICT")

    status, payload = command(f"/api/admin/reports/{report2}/dismiss", admin_token, "dismissed in ADM5")
    expect("report_dismiss_status", status, 200); expect_true("report_dismiss_changed", data(payload)["changed"] is True)
    status, payload = command(f"/api/admin/reports/{report2}/dismiss", admin_token, "same state")
    expect("report_dismiss_changed_false_status", status, 200); expect_true("report_dismiss_changed_false", data(payload)["changed"] is False)
    status, payload = command(f"/api/admin/reports/{report2}/resolve", admin_token, "opposite terminal")
    expect("rejected_report_resolve_conflict", status, 409); expect("rejected_report_resolve_code", safe_code(payload), "ADMIN_STATE_CONFLICT")

    # Post hide/restore and trimmed reason persisted in audit.
    status, payload = command(f"/api/admin/posts/{post_id}/hide", admin_token, "  ADM5 padded reason  ")
    expect("post_hide_status", status, 200); expect_true("post_hide_changed", data(payload)["changed"] is True)
    expect("post_hidden_detail", data(request("GET", f"/api/admin/posts/{post_id}", admin_token)[1])["moderationStatus"], "hidden")
    audit_reason = sql(f"select reason from public.admin_actions where target_type='post' and target_entity_id={post_id} order by id desc limit 1")
    expect("reason_trimmed", audit_reason, "ADM5 padded reason")
    status, payload = command(f"/api/admin/posts/{post_id}/hide", admin_token, "same state")
    expect("post_hide_changed_false_status", status, 200); expect_true("post_hide_changed_false", data(payload)["changed"] is False)
    status, payload = command(f"/api/admin/posts/{post_id}/restore", admin_token, "restore")
    expect("post_restore_status", status, 200); expect_true("post_restore_changed", data(payload)["changed"] is True)
    status, payload = command(f"/api/admin/posts/{post_id}/restore", admin_token, "same state")
    expect("post_restore_changed_false_status", status, 200); expect_true("post_restore_changed_false", data(payload)["changed"] is False)

    # User suspend/unsuspend, state/list/dashboard refresh contract.
    suspended_before = dash_before["suspendedUserCount"]
    status, payload = command(f"/api/admin/users/{target_id}/suspend", admin_token, "suspend target")
    expect("user_suspend_status", status, 200); expect_true("user_suspend_changed", data(payload)["changed"] is True)
    expect("user_suspended_detail", data(request("GET", f"/api/admin/users/{target_id}", admin_token)[1])["accountStatus"], "suspended")
    dash_suspended = data(request("GET", "/api/admin/dashboard", admin_token)[1])
    expect("dashboard_suspended_increment", dash_suspended["suspendedUserCount"], suspended_before + 1)
    status, payload = command(f"/api/admin/users/{target_id}/suspend", admin_token, "same state")
    expect("user_suspend_changed_false_status", status, 200); expect_true("user_suspend_changed_false", data(payload)["changed"] is False)
    status, payload = command(f"/api/admin/users/{target_id}/unsuspend", admin_token, "restore target")
    expect("user_unsuspend_status", status, 200); expect_true("user_unsuspend_changed", data(payload)["changed"] is True)
    status, payload = command(f"/api/admin/users/{target_id}/unsuspend", admin_token, "same state")
    expect("user_unsuspend_changed_false_status", status, 200); expect_true("user_unsuspend_changed_false", data(payload)["changed"] is False)
    dash_after = data(request("GET", "/api/admin/dashboard", admin_token)[1])
    expect("dashboard_pending_decrement", dash_after["pendingReportCount"], dash_before["pendingReportCount"] - 2)
    expect("dashboard_suspend_restored", dash_after["suspendedUserCount"], suspended_before)

    # Error matrix and safety.
    status, payload = command(f"/api/admin/posts/{post_id}/hide", admin_token, "   ")
    expect("error_400_blank", status, 400); expect("error_400_code", safe_code(payload), "INVALID_ADMIN_COMMAND")
    status, payload = command(f"/api/admin/posts/{post_id}/hide", admin_token, "x" * 1001)
    expect("error_400_too_long", status, 400)
    status, payload = request("GET", "/api/admin/posts/922337203685477000", admin_token)
    expect("error_404", status, 404); expect("error_404_code", safe_code(payload), "ADMIN_TARGET_NOT_FOUND")
    status, payload = command(f"/api/admin/users/{admin_id}/suspend", admin_token, "self")
    expect("self_suspend_conflict", status, 409); expect("self_suspend_code", safe_code(payload), "ADMIN_STATE_CONFLICT")
    withdrawn_id = user_id(emails["withdrawn_admin"])
    status, payload = command(f"/api/admin/users/{withdrawn_id}/unsuspend", admin_token, "withdrawn")
    expect("withdrawn_restore_conflict", status, 409)
    serialized = json.dumps(payload, ensure_ascii=False)
    expect_true("error_response_no_internal_material", not any(term.lower() in serialized.lower() for term in ("sqlstate", "stack trace", "jc_admin", "admin_restore_user", "app_users", "authorization", "bearer ")), serialized)

    # Logout and refresh-token revocation.
    expect("logout_status", request("POST", "/api/v1/auth/logout", body={"refreshToken": admin_refresh})[0], 204)
    refresh_status, _ = request("POST", "/api/v1/auth/refresh", body={"refreshToken": admin_refresh})
    expect("logout_revokes_refresh", refresh_status, 401)

    fixture.update({
        "adminEmail": emails["admin"], "normalEmail": emails["normal"],
        "postId": post_id, "reportId": report3, "targetUserId": target_id,
    })
    FIXTURE_OUT.write_text(json.dumps(fixture), encoding="utf-8")
    evidence = {
        "schemaVersion": "adm5-http-acceptance-v1",
        "status": "PASS" if all(v["status"] == "PASS" for v in checks.values()) else "FAIL",
        "endpointCoverage": "13/13",
        "commandCoverage": "6/6",
        "checks": checks,
        "secretsRecorded": False,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": evidence["status"], "checks": len(checks), "endpointCoverage": "13/13", "commandCoverage": "6/6"}))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(json.dumps({"schemaVersion": "adm5-http-acceptance-v1", "status": "FAIL", "failure": type(exc).__name__, "message": str(exc), "checks": checks}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise
