import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { LockKeyhole, ShieldCheck } from "lucide-react";
import { isLogin, login } from "../services/auth";

function loginErrorMessage(error) {
  const status = error.response?.status;
  if (status === 400 || status === 401) return "이메일 또는 비밀번호를 확인해 주세요.";
  if (status === 429) return "요청이 많습니다. 잠시 후 다시 시도해 주세요.";
  return "로그인 요청을 처리하지 못했습니다.";
}

export default function AdminLoginPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  if (isLogin()) return <Navigate to="/admin" replace />;

  const submit = async (event) => {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError("");
    try {
      await login({ email: email.trim(), password });
      const destination = typeof location.state?.from === "string" && location.state.from.startsWith("/admin")
        ? location.state.from
        : "/admin";
      navigate(destination, { replace: true });
    } catch (requestError) {
      setError(loginErrorMessage(requestError));
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-4 py-10">
      <section className="w-full max-w-md rounded-3xl border border-white/10 bg-white p-8 shadow-2xl sm:p-10" aria-labelledby="login-title">
        <div className="mb-8 flex items-center gap-4">
          <div className="grid h-12 w-12 place-items-center rounded-2xl bg-teal-600 text-white">
            <ShieldCheck aria-hidden="true" size={25} />
          </div>
          <div>
            <p className="text-sm font-semibold text-teal-700">Journey Connect</p>
            <h1 id="login-title" className="text-2xl font-black text-slate-950">관리자 로그인</h1>
          </div>
        </div>

        <p className="mb-6 text-sm leading-6 text-slate-600">
          일반 계정 인증 후 관리자 API가 데이터베이스의 현재 권한을 다시 확인합니다.
        </p>

        <form className="space-y-5" onSubmit={submit}>
          <div>
            <label htmlFor="email" className="mb-2 block text-sm font-semibold text-slate-800">이메일</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-950 shadow-sm transition focus:border-teal-600 focus:ring-2 focus:ring-teal-200"
            />
          </div>
          <div>
            <label htmlFor="password" className="mb-2 block text-sm font-semibold text-slate-800">비밀번호</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-describedby={error ? "login-error" : undefined}
              className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-950 shadow-sm transition focus:border-teal-600 focus:ring-2 focus:ring-teal-200"
            />
          </div>
          {error && <p id="login-error" role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{error}</p>}
          <button
            type="submit"
            disabled={pending}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-3 font-bold text-white shadow-sm transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <LockKeyhole aria-hidden="true" size={18} />
            {pending ? "확인 중…" : "로그인"}
          </button>
        </form>
      </section>
    </main>
  );
}
