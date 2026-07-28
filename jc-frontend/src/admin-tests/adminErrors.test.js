import test from "node:test";
import assert from "node:assert/strict";
import { normalizeAdminError } from "../admin/adminErrors.js";

const cases = [
  [401, "AUTHENTICATION_REQUIRED", "로그인이 만료되었거나 인증이 필요합니다."],
  [403, "ADMIN_ACCESS_DENIED", "관리자 권한이 없습니다."],
  [404, "ADMIN_TARGET_NOT_FOUND", "대상이 없거나 더 이상 조회할 수 없습니다."],
  [409, "ADMIN_STATE_CONFLICT", "현재 상태가 변경되어 요청을 처리할 수 없습니다."],
  [400, "INVALID_ADMIN_COMMAND", "입력 내용을 확인해 주세요."],
  [500, "ADMIN_OPERATION_FAILED", "요청 처리 중 오류가 발생했습니다."],
];

for (const [status, code, message] of cases) {
  test(`${status}_is_handled`, () => {
    assert.deepEqual(normalizeAdminError({ response: { status, data: { code, message: "raw SQLSTATE 42501" } } }), { status, code, message });
  });
}

test("500_does_not_expose_internal_error", () => {
  const result = normalizeAdminError({ response: { status: 500, data: { message: "relation app_users missing", code: "UNKNOWN" } }, message: "stack trace" });
  assert.equal(result.message, "요청 처리 중 오류가 발생했습니다.");
  assert.doesNotMatch(result.message, /relation|stack|SQLSTATE/i);
});
