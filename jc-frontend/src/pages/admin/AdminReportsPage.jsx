import { useCallback, useState } from "react";
import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { getAdminReports } from "../../services/adminApi";
import { REPORT_STATUSES, REPORT_TARGET_TYPES, adminLabel } from "../../admin/adminPolicies";
import { formatAdminDate, truncateText } from "../../admin/adminFormat";
import useAdminListQuery from "../../admin/useAdminListQuery";
import { AdminEmpty, AdminError, AdminLoading, AdminPageHeader, AdminPagination, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

const FILTERS = ["status", "targetType"];

export default function AdminReportsPage() {
  const loader = useCallback((params) => getAdminReports(params), []);
  const { loading, data, error, page, search, filters, update, reload } = useAdminListQuery(loader, FILTERS);
  const [draft, setDraft] = useState(search);
  const submit = (event) => { event.preventDefault(); update({ search: draft.trim().slice(0, 100) }); };

  return <>
    <AdminPageHeader title="Reports" description="신고 목록을 확인하고 처리 또는 기각합니다." />
    <AdminPanel>
      <form onSubmit={submit} className="grid gap-3 border-b border-slate-200 p-5 lg:grid-cols-[1fr_180px_180px_auto]">
        <label className="relative"><span className="sr-only">신고 검색</span><Search className="absolute left-3 top-2.5 text-slate-400" size={18} /><input value={draft} maxLength="100" onChange={(event) => setDraft(event.target.value)} placeholder="신고 ID, 신고자, 사유 검색" className="w-full rounded-lg border border-slate-300 py-2 pl-10 pr-3 text-sm" /></label>
        <label><span className="sr-only">신고 상태</span><select value={filters.status} onChange={(event) => update({ status: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 상태</option>{REPORT_STATUSES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <label><span className="sr-only">신고 대상 유형</span><select value={filters.targetType} onChange={(event) => update({ targetType: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 대상</option>{REPORT_TARGET_TYPES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <button type="submit" className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">검색</button>
      </form>
      {loading ? <AdminLoading /> : error ? <div className="p-5"><AdminError message={error.message} onRetry={reload} /></div> : !data?.items?.length ? <div className="p-5"><AdminEmpty title="조건에 맞는 신고가 없습니다." /></div> : <div className="overflow-x-auto"><table className="w-full min-w-[860px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th scope="col" className="px-5 py-3">신고</th><th scope="col" className="px-5 py-3">대상</th><th scope="col" className="px-5 py-3">사유</th><th scope="col" className="px-5 py-3">상태</th><th scope="col" className="px-5 py-3">생성 시각</th><th scope="col" className="px-5 py-3 text-right">상세</th></tr></thead><tbody className="divide-y divide-slate-100">{data.items.map((item) => <tr key={item.reportId}><td className="px-5 py-4"><p className="font-semibold">#{item.reportId}</p><p className="text-xs text-slate-500">{item.reporterUsername || "신고자 비공개"}</p></td><td className="px-5 py-4">{adminLabel(item.targetType)} #{item.targetId}</td><td className="max-w-xs px-5 py-4"><p className="font-medium">{item.reasonCategory || "-"}</p><p className="mt-1 text-xs text-slate-500">{truncateText(item.reasonDetail)}</p></td><td className="px-5 py-4"><AdminStatusBadge value={item.status} /></td><td className="px-5 py-4 text-slate-600">{formatAdminDate(item.createdAt)}</td><td className="px-5 py-4 text-right"><Link to={`/admin/reports/${item.reportId}`} className="font-semibold text-teal-700">상세 보기</Link></td></tr>)}</tbody></table></div>}
      {!loading && !error && data && <AdminPagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={(next) => update({ page: next })} />}
    </AdminPanel>
  </>;
}
