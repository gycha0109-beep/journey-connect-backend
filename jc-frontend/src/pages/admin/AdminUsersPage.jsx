import { useCallback, useState } from "react";
import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { getAdminUsers } from "../../services/adminApi";
import { USER_ACCOUNT_STATUSES, USER_ROLES, adminLabel } from "../../admin/adminPolicies";
import { formatAdminDate } from "../../admin/adminFormat";
import useAdminListQuery from "../../admin/useAdminListQuery";
import { AdminEmpty, AdminError, AdminLoading, AdminPageHeader, AdminPagination, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

const FILTERS = ["role", "accountStatus"];

export default function AdminUsersPage() {
  const loader = useCallback((params) => getAdminUsers(params), []);
  const { loading, data, error, page, search, filters, update, reload } = useAdminListQuery(loader, FILTERS);
  const [draft, setDraft] = useState(search);

  return <>
    <AdminPageHeader title="Users" description="사용자 계정 상태를 확인하고 정지 또는 정지 해제합니다." />
    <AdminPanel>
      <form onSubmit={(event) => { event.preventDefault(); update({ search: draft.trim().slice(0, 100) }); }} className="grid gap-3 border-b border-slate-200 p-5 lg:grid-cols-[1fr_180px_180px_auto]">
        <label className="relative"><span className="sr-only">사용자 검색</span><Search className="absolute left-3 top-2.5 text-slate-400" size={18} /><input maxLength="100" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="사용자 ID, 이메일, 표시 이름 검색" className="w-full rounded-lg border border-slate-300 py-2 pl-10 pr-3 text-sm" /></label>
        <label><span className="sr-only">사용자 역할</span><select value={filters.role} onChange={(event) => update({ role: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 역할</option>{USER_ROLES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <label><span className="sr-only">계정 상태</span><select value={filters.accountStatus} onChange={(event) => update({ accountStatus: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 계정 상태</option>{USER_ACCOUNT_STATUSES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <button type="submit" className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">검색</button>
      </form>
      {loading ? <AdminLoading /> : error ? <div className="p-5"><AdminError message={error.message} onRetry={reload} /></div> : !data?.items?.length ? <div className="p-5"><AdminEmpty title="조건에 맞는 사용자가 없습니다." /></div> : <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th scope="col" className="px-5 py-3">사용자</th><th scope="col" className="px-5 py-3">이메일</th><th scope="col" className="px-5 py-3">역할</th><th scope="col" className="px-5 py-3">상태</th><th scope="col" className="px-5 py-3">가입 시각</th><th scope="col" className="px-5 py-3 text-right">상세</th></tr></thead><tbody className="divide-y divide-slate-100">{data.items.map((item) => <tr key={item.userId}><td className="px-5 py-4"><p className="font-semibold text-slate-900">{item.displayName || item.username || `사용자 #${item.userId}`}</p><p className="text-xs text-slate-500">#{item.userId} · {item.username || "-"}</p></td><td className="px-5 py-4">{item.email || "-"}</td><td className="px-5 py-4">{adminLabel(item.role)}</td><td className="px-5 py-4"><AdminStatusBadge value={item.accountStatus} /></td><td className="px-5 py-4 text-slate-600">{formatAdminDate(item.createdAt)}</td><td className="px-5 py-4 text-right"><Link to={`/admin/users/${item.userId}`} className="font-semibold text-teal-700">상세 보기</Link></td></tr>)}</tbody></table></div>}
      {!loading && !error && data && <AdminPagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={(next) => update({ page: next })} />}
    </AdminPanel>
  </>;
}
