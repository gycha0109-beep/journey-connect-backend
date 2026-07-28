import { useState } from "react";
import { LayoutDashboard, LogOut, Menu, MessageSquareWarning, Newspaper, Users, X } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { getUser, logout } from "../services/auth";

const navigation = [
  { to: "/admin", end: true, label: "Dashboard", icon: LayoutDashboard },
  { to: "/admin/reports", label: "Reports", icon: MessageSquareWarning },
  { to: "/admin/posts", label: "Posts", icon: Newspaper },
  { to: "/admin/users", label: "Users", icon: Users },
];

export default function AdminLayout() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const user = getUser();
  const leave = async () => { await logout(); navigate("/login", { replace: true }); };

  return <div className="min-h-screen bg-slate-50 text-slate-800">
    {open && <button type="button" aria-label="관리자 메뉴 닫기" onClick={() => setOpen(false)} className="fixed inset-0 z-30 bg-slate-950/40 lg:hidden" />}
    <aside aria-label="관리자 메뉴" className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-[#172033] text-slate-300 shadow-xl transition-transform lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}>
      <div className="flex h-20 items-center gap-3 border-b border-white/10 px-6"><div className="grid h-10 w-10 place-items-center rounded-xl bg-teal-500 font-black text-white">JC</div><div><p className="font-bold text-white">Journey Connect</p><p className="text-xs text-slate-400">Admin Dashboard</p></div><button type="button" aria-label="관리자 메뉴 닫기" onClick={() => setOpen(false)} className="ml-auto rounded-lg p-2 lg:hidden"><X size={20} /></button></div>
      <nav className="flex-1 space-y-2 px-4 py-6">{navigation.map(({ to, end, label, icon: Icon }) => <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)} className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-semibold transition ${isActive ? "bg-teal-500/15 text-teal-200" : "text-slate-300 hover:bg-white/5 hover:text-white"}`}><Icon size={19} />{label}</NavLink>)}</nav>
      <div className="border-t border-white/10 p-4"><button type="button" onClick={leave} className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-white/5"><LogOut size={17} />로그아웃</button></div>
    </aside>
    <div className="lg:pl-64"><header className="sticky top-0 z-20 flex h-20 items-center border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-8"><button type="button" aria-label="관리자 메뉴 열기" onClick={() => setOpen(true)} className="mr-3 rounded-lg p-2 hover:bg-slate-100 lg:hidden"><Menu size={22} /></button><div><p className="font-bold text-slate-950">관리자 화면</p><p className="hidden text-xs text-slate-500 sm:block">신고·게시물·사용자 상태를 간단히 관리합니다.</p></div><div className="ml-auto text-right"><p className="text-sm font-semibold text-slate-800">{user?.nickname || user?.email || "로그인 사용자"}</p><p className="text-xs text-slate-500">권한은 서버에서 확인됩니다</p></div></header><main className="mx-auto max-w-7xl p-4 sm:p-8"><Outlet /></main></div>
  </div>;
}
