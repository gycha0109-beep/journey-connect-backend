import { MessageSquareWarning, Newspaper, UserRoundCheck, Users } from "lucide-react";
import { Link } from "react-router-dom";
import { useAdminContext } from "../../admin/AdminContext";
import { formatAdminDate } from "../../admin/adminFormat";
import { AdminEmpty, AdminPageHeader, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

export default function AdminDashboardPage() {
  const { dashboard } = useAdminContext();
  const cards = [
    ["전체 사용자", dashboard?.totalUsers ?? 0, Users],
    ["활성 게시물", dashboard?.activePostCount ?? 0, Newspaper],
    ["대기 신고", dashboard?.pendingReportCount ?? 0, MessageSquareWarning],
    ["정지 사용자", dashboard?.suspendedUserCount ?? 0, UserRoundCheck],
  ];
  const reports = (dashboard?.recentReports || []).slice(0, 5);
  const actions = (dashboard?.recentAdminActions || []).slice(0, 5);

  return <>
    <AdminPageHeader title="Dashboard" description="관리 상태를 한 화면에서 간단히 확인합니다." />
    <section aria-label="관리자 요약" className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map(([label, value, Icon]) => <div key={label} className="flex items-center rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="grid h-12 w-12 place-items-center rounded-xl bg-teal-50 text-teal-700"><Icon size={23} /></div><div className="ml-4"><p className="text-sm text-slate-500">{label}</p><p className="mt-1 text-2xl font-bold text-slate-950">{Number(value).toLocaleString()}</p></div></div>)}</section>
    <div className="grid gap-6 xl:grid-cols-2">
      <AdminPanel><div className="flex items-center justify-between border-b border-slate-200 px-5 py-4"><h2 className="font-bold text-slate-900">최근 신고</h2><Link to="/admin/reports" className="text-sm font-semibold text-teal-700">전체 보기</Link></div>{reports.length === 0 ? <div className="p-5"><AdminEmpty title="최근 신고가 없습니다." /></div> : <ul className="divide-y divide-slate-100">{reports.map((item) => <li key={item.reportId} className="flex items-center gap-3 px-5 py-4"><div className="min-w-0 flex-1"><Link to={`/admin/reports/${item.reportId}`} className="font-semibold text-slate-900 hover:text-teal-700">{item.reasonCategory || "신고"}</Link><p className="mt-1 truncate text-xs text-slate-500">{item.targetType} #{item.targetId} · {formatAdminDate(item.createdAt)}</p></div><AdminStatusBadge value={item.status} /></li>)}</ul>}</AdminPanel>
      <AdminPanel><div className="border-b border-slate-200 px-5 py-4"><h2 className="font-bold text-slate-900">최근 관리자 처리</h2></div>{actions.length === 0 ? <div className="p-5"><AdminEmpty title="최근 처리 내역이 없습니다." /></div> : <ul className="divide-y divide-slate-100">{actions.map((item) => <li key={item.actionId} className="px-5 py-4"><p className="font-semibold text-slate-900">{item.actionType}</p><p className="mt-1 text-xs text-slate-500">{item.targetType} #{item.targetId} · {formatAdminDate(item.createdAt)}</p></li>)}</ul>}</AdminPanel>
    </div>
  </>;
}
