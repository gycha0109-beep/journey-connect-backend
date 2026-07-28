export const ADMIN_ROUTES = Object.freeze({
  dashboard: "/admin",
  reports: "/admin/reports",
  posts: "/admin/posts",
  users: "/admin/users",
});

export const ADMIN_PAGE_SIZE = 20;
export const ADMIN_MAX_PAGE_SIZE = 100;
export const ADMIN_MAX_SEARCH_LENGTH = 100;
export const ADMIN_MAX_REASON_LENGTH = 1000;

export const REPORT_STATUSES = ["pending", "in_review", "resolved", "rejected"];
export const REPORT_TARGET_TYPES = ["user", "post", "comment"];
export const POST_MODERATION_STATUSES = ["visible", "hidden"];
export const POST_VISIBILITIES = ["public", "followers", "private"];
export const USER_ROLES = ["user", "moderator", "admin"];
export const USER_ACCOUNT_STATUSES = ["active", "suspended", "withdrawn"];

const LABELS = Object.freeze({
  pending: "대기",
  in_review: "검토 중",
  resolved: "처리 완료",
  rejected: "기각",
  visible: "표시 중",
  hidden: "숨김",
  public: "공개",
  followers: "팔로워 공개",
  private: "비공개",
  active: "활성",
  suspended: "정지",
  withdrawn: "탈퇴",
  user: "사용자",
  moderator: "운영자",
  admin: "관리자",
  post: "게시물",
  comment: "댓글",
});

export function adminLabel(value) {
  return LABELS[value] || value || "-";
}
