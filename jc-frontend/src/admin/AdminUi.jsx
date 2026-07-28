import { ChevronLeft, ChevronRight, LoaderCircle } from "lucide-react";
import { adminLabel } from "./adminPolicies";

export function AdminStatusBadge({ value }) {
  const tone = {
    pending: "bg-amber-50 text-amber-800 border-amber-200",
    in_review: "bg-blue-50 text-blue-800 border-blue-200",
    resolved: "bg-emerald-50 text-emerald-800 border-emerald-200",
    rejected: "bg-slate-100 text-slate-700 border-slate-200",
    visible: "bg-emerald-50 text-emerald-800 border-emerald-200",
    hidden: "bg-rose-50 text-rose-800 border-rose-200",
    active: "bg-emerald-50 text-emerald-800 border-emerald-200",
    suspended: "bg-rose-50 text-rose-800 border-rose-200",
    withdrawn: "bg-slate-100 text-slate-600 border-slate-200",
  }[value] || "bg-slate-50 text-slate-700 border-slate-200";

  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${tone}`}>{adminLabel(value)}</span>;
}

export function AdminLoading({ label = "데이터를 불러오는 중입니다." }) {
  return <div role="status" aria-live="polite" className="flex min-h-48 items-center justify-center gap-2 text-sm text-slate-500"><LoaderCircle className="animate-spin" size={20} />{label}</div>;
}

export function AdminError({ message, onRetry }) {
  return <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800"><p>{message}</p>{onRetry && <button type="button" onClick={onRetry} className="mt-3 rounded-lg border border-red-300 bg-white px-3 py-2 font-semibold">다시 시도</button>}</div>;
}

export function AdminEmpty({ title = "표시할 데이터가 없습니다.", description }) {
  return <div className="min-h-48 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center"><p className="font-semibold text-slate-700">{title}</p>{description && <p className="mt-2 text-sm text-slate-500">{description}</p>}</div>;
}

export function AdminPagination({ page, totalPages, totalElements, onPage }) {
  const safeTotalPages = Math.max(totalPages || 0, 1);
  return <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-5 py-4 text-sm text-slate-500"><span>총 {Number(totalElements || 0).toLocaleString()}건</span><div className="flex items-center gap-2"><button aria-label="이전 페이지" type="button" disabled={page <= 0} onClick={() => onPage(page - 1)} className="rounded-md border border-slate-300 p-2 disabled:opacity-30"><ChevronLeft size={17} /></button><span className="min-w-20 text-center font-medium text-slate-700">{page + 1} / {safeTotalPages}</span><button aria-label="다음 페이지" type="button" disabled={page + 1 >= safeTotalPages} onClick={() => onPage(page + 1)} className="rounded-md border border-slate-300 p-2 disabled:opacity-30"><ChevronRight size={17} /></button></div></div>;
}

export function AdminPageHeader({ title, description, actions }) {
  return <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h1 className="text-2xl font-bold text-slate-950">{title}</h1><p className="mt-1 text-sm text-slate-500">{description}</p></div>{actions && <div className="flex flex-wrap gap-2">{actions}</div>}</div>;
}

export function AdminPanel({ children, className = "" }) {
  return <section className={`overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm ${className}`}>{children}</section>;
}
