import { Link } from "react-router-dom";

export default function AdminNotFoundPage() {
  return <div className="rounded-xl border border-slate-200 bg-white p-8 text-center shadow-sm"><h1 className="text-xl font-bold text-slate-950">관리자 페이지를 찾을 수 없습니다.</h1><p className="mt-2 text-sm text-slate-500">주소를 확인하거나 대시보드로 이동해 주세요.</p><Link to="/admin" className="mt-5 inline-flex rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">Dashboard로 이동</Link></div>;
}
