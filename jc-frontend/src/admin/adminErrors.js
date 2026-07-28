const SAFE_MESSAGES = Object.freeze({
  AUTHENTICATION_REQUIRED: "로그인이 만료되었거나 인증이 필요합니다.",
  ADMIN_ACCESS_DENIED: "관리자 권한이 없습니다.",
  ADMIN_TARGET_NOT_FOUND: "대상이 없거나 더 이상 조회할 수 없습니다.",
  ADMIN_STATE_CONFLICT: "현재 상태가 변경되어 요청을 처리할 수 없습니다.",
  INVALID_ADMIN_COMMAND: "입력 내용을 확인해 주세요.",
  ADMIN_OPERATION_FAILED: "요청 처리 중 오류가 발생했습니다.",
});

export function normalizeAdminError(error) {
  const status = Number(error?.response?.status || 0);
  const code = error?.response?.data?.code;

  if (code && SAFE_MESSAGES[code]) {
    return { status, code, message: SAFE_MESSAGES[code] };
  }
  if (status === 401) return { status, code: "AUTHENTICATION_REQUIRED", message: SAFE_MESSAGES.AUTHENTICATION_REQUIRED };
  if (status === 403) return { status, code: "ADMIN_ACCESS_DENIED", message: SAFE_MESSAGES.ADMIN_ACCESS_DENIED };
  if (status === 404) return { status, code: "ADMIN_TARGET_NOT_FOUND", message: SAFE_MESSAGES.ADMIN_TARGET_NOT_FOUND };
  if (status === 409) return { status, code: "ADMIN_STATE_CONFLICT", message: SAFE_MESSAGES.ADMIN_STATE_CONFLICT };
  if (status === 400) return { status, code: "INVALID_ADMIN_COMMAND", message: SAFE_MESSAGES.INVALID_ADMIN_COMMAND };
  return { status, code: "ADMIN_OPERATION_FAILED", message: SAFE_MESSAGES.ADMIN_OPERATION_FAILED };
}
