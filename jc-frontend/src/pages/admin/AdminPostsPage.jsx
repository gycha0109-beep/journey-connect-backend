import { useCallback, useState } from "react";
import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { getAdminPosts } from "../../services/adminApi";
import { POST_MODERATION_STATUSES, POST_VISIBILITIES, adminLabel } from "../../admin/adminPolicies";
import { formatAdminDate, truncateText } from "../../admin/adminFormat";
import useAdminListQuery from "../../admin/useAdminListQuery";
import { AdminEmpty, AdminError, AdminLoading, AdminPageHeader, AdminPagination, AdminPanel, AdminStatusBadge } from "../../admin/AdminUi";

const FILTERS = ["moderationStatus", "visibility"];

export default function AdminPostsPage() {
  const loader = useCallback((params) => getAdminPosts(params), []);
  const { loading, data, error, page, search, filters, update, reload } = useAdminListQuery(loader, FILTERS);
  const [draft, setDraft] = useState(search);

  return <>
    <AdminPageHeader title="Posts" description="게시물 상태를 확인하고 숨김 또는 복구합니다." />
    <AdminPanel>
      <form onSubmit={(event) => { event.preventDefault(); update({ search: draft.trim().slice(0, 100) }); }} className="grid gap-3 border-b border-slate-200 p-5 lg:grid-cols-[1fr_180px_180px_auto]">
        <label className="relative"><span className="sr-only">게시물 검색</span><Search className="absolute left-3 top-2.5 text-slate-400" size={18} /><input maxLength="100" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="게시물 ID, 제목, 작성자 검색" className="w-full rounded-lg border border-slate-300 py-2 pl-10 pr-3 text-sm" /></label>
        <label><span className="sr-only">관리 상태</span><select value={filters.moderationStatus} onChange={(event) => update({ moderationStatus: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 관리 상태</option>{POST_MODERATION_STATUSES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <label><span className="sr-only">공개 범위</span><select value={filters.visibility} onChange={(event) => update({ visibility: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"><option value="">모든 공개 범위</option>{POST_VISIBILITIES.map((value) => <option key={value} value={value}>{adminLabel(value)}</option>)}</select></label>
        <button type="submit" className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white">검색</button>
      </form>
      {loading ? <AdminLoading /> : error ? <div className="p-5"><AdminError message={error.message} onRetry={reload} /></div> : !data?.items?.length ? <div className="p-5"><AdminEmpty title="조건에 맞는 게시물이 없습니다." /></div> : <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th scope="col" className="px-5 py-3">게시물</th><th scope="col" className="px-5 py-3">작성자</th><th scope="col" className="px-5 py-3">공개 범위</th><th scope="col" className="px-5 py-3">관리 상태</th><th scope="col" className="px-5 py-3">생성 시각</th><th scope="col" className="px-5 py-3 text-right">상세</th></tr></thead><tbody className="divide-y divide-slate-100">{data.items.map((item) => <tr key={item.postId}><td className="max-w-md px-5 py-4"><p className="font-semibold text-slate-900">{item.title || `게시물 #${item.postId}`}</p><p className="mt-1 text-xs text-slate-500">#{item.postId} · {truncateText(item.contentPreview)}</p></td><td className="px-5 py-4">{item.authorDisplayName || `사용자 #${item.authorId}`}</td><td className="px-5 py-4">{adminLabel(item.visibility)}</td><td className="px-5 py-4"><AdminStatusBadge value={item.moderationStatus} /></td><td className="px-5 py-4 text-slate-600">{formatAdminDate(item.createdAt)}</td><td className="px-5 py-4 text-right"><Link to={`/admin/posts/${item.postId}`} className="font-semibold text-teal-700">상세 보기</Link></td></tr>)}</tbody></table></div>}
      {!loading && !error && data && <AdminPagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={(next) => update({ page: next })} />}
    </AdminPanel>
  </>;
}
