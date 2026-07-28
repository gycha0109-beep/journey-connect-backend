export function formatAdminDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function truncateText(value, length = 80) {
  const text = String(value || "").trim();
  return text.length > length ? `${text.slice(0, length)}…` : text || "-";
}
