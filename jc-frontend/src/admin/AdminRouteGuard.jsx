import { useCallback, useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { isLogin } from "../services/auth";
import { getAdminDashboard } from "../services/adminApi";
import { normalizeAdminError } from "./adminErrors";
import { AdminError, AdminLoading } from "./AdminUi";
import { AdminProvider } from "./AdminContext";

export default function AdminRouteGuard({ children }) {
  const location = useLocation();
  const authenticated = isLogin();
  const [state, setState] = useState({ status: authenticated ? "checking" : "anonymous", dashboard: null, error: null });

  const verify = useCallback(async () => {
    if (!authenticated) return;
    setState({ status: "checking", dashboard: null, error: null });
    try {
      const dashboard = await getAdminDashboard();
      setState({ status: "allowed", dashboard, error: null });
    } catch (error) {
      const normalized = normalizeAdminError(error);
      setState({ status: normalized.status === 401 ? "anonymous" : normalized.status === 403 ? "forbidden" : "error", dashboard: null, error: normalized });
    }
  }, [authenticated]);

  useEffect(() => {
    verify();
    const handleAuthCleared = () => setState({ status: "anonymous", dashboard: null, error: null });
    window.addEventListener("jc:auth-cleared", handleAuthCleared);
    return () => window.removeEventListener("jc:auth-cleared", handleAuthCleared);
  }, [verify]);

  if (!authenticated || state.status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  if (state.status === "checking") return <div className="min-h-screen bg-slate-50"><AdminLoading label="관리자 권한을 확인하는 중입니다." /></div>;
  if (state.status === "forbidden") return <main className="grid min-h-screen place-items-center bg-slate-50 p-6"><div className="max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm"><h1 className="text-xl font-bold text-slate-950">관리자 접근 불가</h1><p className="mt-3 text-sm text-slate-600">관리자 권한이 없습니다.</p><a href="/feedpage" className="mt-5 inline-flex rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">서비스로 돌아가기</a></div></main>;
  if (state.status === "error") return <main className="grid min-h-screen place-items-center bg-slate-50 p-6"><div className="w-full max-w-lg"><AdminError message={state.error.message} onRetry={verify} /></div></main>;
  return <AdminProvider initialDashboard={state.dashboard}>{children}</AdminProvider>;
}
