import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getAdminUser, suspendAdminUser, unsuspendAdminUser } from "../../services/adminApi";
import { normalizeAdminError } from "../../admin/adminErrors";
import { adminLabel } from "../../admin/adminPolicies";
import { formatAdminDate } from "../../admin/adminFormat";
import { useAdminContext } from "../../admin/AdminContext";
import AdminCommandDialog from "../../admin/AdminCommandDialog";
import { AdminError, AdminLoading, AdminPageHeader, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

export default function AdminUserDetailPage() {
  const { userId } = useParams();
  const { refreshDashboard } = useAdminContext();
  const [state, setState] = useState({ loading: true, item: null, error: null });
  const [command, setCommand] = useState(null);
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState("");
  const load = useCallback(async () => { setState((current) => ({ ...current, loading: true, error: null })); try { setState({ loading: false, item: await getAdminUser(userId), error: null }); } catch (error) { setState({ loading: false, item: null, error: normalizeAdminError(error) }); } }, [userId]);
  useEffect(() => { load(); }, [load]);
  const execute = async (reason) => { if (pending || !command) return; setPending(true); setNotice(""); try { const result = command === "suspend" ? await suspendAdminUser(userId, reason) : await unsuspendAdminUser(userId, reason); setNotice(result.changed ? "사용자 상태가 갱신되었습니다." : "이미 요청한 상태여서 추가 변경 없이 완료되었습니다."); setCommand(null); await Promise.all([load(), refreshDashboard()]); } catch (error) { setNotice(normalizeAdminError(error).message); } finally { setPending(false); } };
  if (state.loading) return <AdminLoading />;
  if (state.error) return <AdminError message={state.error.message} onRetry={load} />;
  const item = state.item;
  const canSuspend = item.accountStatus === "active";
  const canUnsuspend = item.accountStatus === "suspended";
  return <>
    <AdminPageHeader title={item.displayName || item.username || `사용자 #${item.userId}`} description="계정의 현재 상태와 기본 정보를 확인합니다." actions={<Link to="/admin/users" className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold">목록으로</Link>} />
    {notice && <div role="status" className="mb-5 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm">{notice}</div>}
    <AdminPanel><dl className="grid gap-x-8 gap-y-5 p-6 sm:grid-cols-2"><div><dt className="text-xs font-semibold text-slate-500">사용자 ID</dt><dd className="mt-1">#{item.userId}</dd></div><div><dt className="text-xs font-semibold text-slate-500">계정 상태</dt><dd className="mt-2"><AdminStatusBadge value={item.accountStatus} /></dd></div><div><dt className="text-xs font-semibold text-slate-500">이메일</dt><dd className="mt-1 break-all">{item.email || "-"}</dd></div><div><dt className="text-xs font-semibold text-slate-500">로그인 ID</dt><dd className="mt-1">{item.username || "-"}</dd></div><div><dt className="text-xs font-semibold text-slate-500">표시 이름</dt><dd className="mt-1">{item.displayName || "-"}</dd></div><div><dt className="text-xs font-semibold text-slate-500">역할</dt><dd className="mt-1">{adminLabel(item.role)}</dd></div><div><dt className="text-xs font-semibold text-slate-500">가입 시각</dt><dd className="mt-1">{formatAdminDate(item.createdAt)}</dd></div><div><dt className="text-xs font-semibold text-slate-500">수정 시각</dt><dd className="mt-1">{formatAdminDate(item.updatedAt)}</dd></div><div><dt className="text-xs font-semibold text-slate-500">정지 시각</dt><dd className="mt-1">{formatAdminDate(item.suspendedAt)}</dd></div></dl><div className="flex justify-end border-t border-slate-200 bg-slate-50 px-6 py-4">{canSuspend && <button type="button" onClick={() => setCommand("suspend")} className="rounded-lg bg-rose-700 px-4 py-2.5 text-sm font-semibold text-white">사용자 정지</button>}{canUnsuspend && <button type="button" onClick={() => setCommand("unsuspend")} className="rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">정지 해제</button>}{!canSuspend && !canUnsuspend && <p className="text-sm text-slate-500">현재 실행할 수 있는 작업이 없습니다.</p>}</div></AdminPanel>
    <AdminCommandDialog key={command || "closed"} open={Boolean(command)} title={command === "suspend" ? "사용자 정지" : "정지 해제"} description="상태 변경 사유를 입력해 주세요." confirmLabel={command === "suspend" ? "정지하기" : "해제하기"} pending={pending} onClose={() => !pending && setCommand(null)} onConfirm={execute} />
  </>;
}
