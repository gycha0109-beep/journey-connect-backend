import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { ADMIN_MAX_REASON_LENGTH } from "./adminPolicies";

export default function AdminCommandDialog({ open, title, description, confirmLabel, pending, onClose, onConfirm }) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const dialogRef = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const previous = document.activeElement;
    const timer = window.setTimeout(() => textareaRef.current?.focus(), 0);
    const handleKeyDown = (event) => {
      if (event.key === "Escape" && !pending) onClose();
      if (event.key !== "Tab") return;
      const controls = dialogRef.current?.querySelectorAll('button:not([disabled]), textarea:not([disabled])');
      if (!controls?.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => { window.clearTimeout(timer); document.removeEventListener("keydown", handleKeyDown); previous?.focus?.(); };
  }, [open, pending, onClose]);

  if (!open) return null;

  const submit = async (event) => {
    event.preventDefault();
    const normalized = reason.trim();
    if (!normalized) { setError("처리 사유를 입력해 주세요."); return; }
    if (normalized.length > ADMIN_MAX_REASON_LENGTH) { setError("처리 사유는 1000자 이하여야 합니다."); return; }
    if (pending) return;
    await onConfirm(normalized);
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4" onMouseDown={(event) => event.target === event.currentTarget && !pending && onClose()}>
    <form ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="admin-command-title" aria-describedby="admin-command-description" onSubmit={submit} className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
      <div className="flex items-start border-b border-slate-200 px-6 py-4"><div><h2 id="admin-command-title" className="text-xl font-bold text-slate-950">{title}</h2><p id="admin-command-description" className="mt-1 text-sm text-slate-500">{description}</p></div><button type="button" aria-label="대화상자 닫기" disabled={pending} onClick={onClose} className="ml-auto rounded-lg p-2 hover:bg-slate-100 disabled:opacity-40"><X size={20} /></button></div>
      <div className="p-6"><label htmlFor="admin-command-reason" className="mb-2 block text-sm font-semibold text-slate-700">처리 사유</label><textarea ref={textareaRef} id="admin-command-reason" required maxLength={ADMIN_MAX_REASON_LENGTH} rows="6" value={reason} aria-describedby="admin-command-reason-help admin-command-reason-error" aria-invalid={Boolean(error)} onChange={(event) => { setReason(event.target.value); setError(""); }} className="w-full resize-y rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100" /><div id="admin-command-reason-help" className="mt-1 flex justify-between text-xs text-slate-500"><span>필수 입력 · 최대 1000자</span><span>{reason.length}/1000</span></div>{error && <p id="admin-command-reason-error" role="alert" className="mt-2 text-sm text-red-700">{error}</p>}</div>
      <div className="flex justify-end gap-3 border-t border-slate-200 bg-slate-50 px-6 py-4"><button type="button" disabled={pending} onClick={onClose} className="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold disabled:opacity-40">취소</button><button type="submit" disabled={pending || !reason.trim()} className="rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50">{pending ? "처리 중..." : confirmLabel}</button></div>
    </form>
  </div>;
}
