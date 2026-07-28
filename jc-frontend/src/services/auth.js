import apiClient, { clearStoredAuth, unwrapApiResponse } from "./apiClient";

const USER_KEY = "loginUser";

export function getUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export function isLogin() {
  return Boolean(localStorage.getItem("accessToken"));
}

export async function login(credentials) {
  const payload = unwrapApiResponse(await apiClient.post("/auth/login", credentials));
  localStorage.setItem("accessToken", payload.accessToken);
  localStorage.setItem("refreshToken", payload.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(payload.user ?? null));
  return payload;
}

export async function logout() {
  const refreshToken = localStorage.getItem("refreshToken");
  try {
    if (refreshToken) await apiClient.post("/auth/logout", { refreshToken });
  } finally {
    clearStoredAuth();
  }
}
