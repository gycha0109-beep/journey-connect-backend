import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { dismissAdminReport, getAdminReport, resolveAdminReport } from "../../services/adminApi";
import { normalizeAdminError } from "../../admin/adminErrors";
import { adminLabel } from "../../admin/adminPolicies";
import { formatAdminDate } from "../../admin/adminFormat";
import { useAdminContext } from "../../admin/AdminContext";
import AdminCommandDialog from "../../admin/AdminCommandDialog";
import { AdminError, AdminLoading, AdminPageHeader, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

export default function AdminReportDetailPage() {
  const { reportId } = useParams();
  const { refreshDashboard } = useAdminContext();
  const [state, setState] = useState({ loading: true, item: null, error: null });
  const [command, setCommand] = useState(null);
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try { setState({ loading: false, item: await getAdminReport(reportId), error: null }); }
    catch (error) { setState({ loading: false, item: null, error: normalizeAdminError(error) }); }
  }, [reportId]);
  useEffect(() => { load(); }, [load]);

  const execute = async (reason) => {
    if (pending || !command) return;
    setPending(true); setNotice("");
    try {
      const result = command === "resolve" ? await resolveAdminReport(reportId, reason) : await dismissAdminReport(reportId, reason);
      setNotice(result.changed ? "신고 상태가 갱신되었습니다." : "이미 요청한 상태여서 추가 변경 없이 완료되었습니다.");
      setCommand(null);
      await Promise.all([load(), refreshDashboard()]);
    } catch (error) { setNotice(normalizeAdminError(error).message); }
    finally { setPending(false); }
  };

  if (state.loading) return <AdminLoading />;
  if (state.error) return <AdminError message={state.error.message} onRetry={load} />;
  const item = state.item;
  return <>
    <AdminPageHeader title={`신고 #${item.reportId}`} description="신고 정보와 현재 처리 가능 상태를 확인합니다." actions={<Link to="/admin/reports" className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold">목록으로</Link>} />
    {notice && <div role="status" className="mb-5 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm">{notice}</div>}
    <AdminPanel><dl className="grid gap-x-8 gap-y-5 p-6 sm:grid-cols-2"><div><dt className="text-xs font-semibold text-slate-500">상태</dt><dd className="mt-2"><AdminStatusBadge value={item.status} /></dd></div><div><dt className="text-xs font-semibold text-slate-500">대상</dt><dd className="mt-1 font-medium">{adminLabel(item.targetType)} #{item.targetId}</dd></div><div><dt className="text-xs font-semibold text-slate-500">신고자</dt><dd className="mt-1">{item.reporterDisplayName || item.reporterUsername || "비공개"}</dd></div><div><dt className="text-xs font-semibold text-slate-500">현재 콘텐츠 상태</dt><dd className="mt-1">{adminLabel(item.currentTargetState)}</dd></div><div className="sm:col-span-2"><dt className="text-xs font-semibold text-slate-500">신고 이유</dt><dd className="mt-1 whitespace-pre-wrap break-words">{item.reasonCategory || "-"}<br /><span className="text-sm text-slate-600">{item.reasonDetail || "상세 사유 없음"}</span></dd></div><div><dt className="text-xs font-semibold text-slate-500">생성 시각</dt><dd className="mt-1">{formatAdminDate(item.createdAt)}</dd></div><div><dt className="text-xs font-semibold text-slate-500">처리 시각</dt><dd className="mt-1">{formatAdminDate(item.handledAt)}</dd></div>{item.resolutionNote && <div className="sm:col-span-2"><dt className="text-xs font-semibold text-slate-500">처리 메모</dt><dd className="mt-1 whitespace-pre-wrap">{item.resolutionNote}</dd></div>}</dl><div className="flex flex-wrap justify-end gap-3 border-t border-slate-200 bg-slate-50 px-6 py-4">{item.canDismiss && <button type="button" onClick={() => setCommand("dismiss")} className="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold">신고 기각</button>}{item.canResolve && <button type="button" onClick={() => setCommand("resolve")} className="rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">신고 처리</button>}{!item.canDismiss && !item.canResolve && <p className="text-sm text-slate-500">현재 실행할 수 있는 작업이 없습니다.</p>}</div></AdminPanel>
    <AdminCommandDialog key={command || "closed"} open={Boolean(command)} title={command === "resolve" ? "신고 처리" : "신고 기각"} description="처리 사유는 서버 기록에 사용됩니다." confirmLabel={command === "resolve" ? "처리하기" : "기각하기"} pending={pending} onClose={() => !pending && setCommand(null)} onConfirm={execute} />
  </>;
}
