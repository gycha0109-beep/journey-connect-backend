import axios from "axios";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export function clearStoredAuth() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("loginUser");
  if (typeof window !== "undefined") window.dispatchEvent(new Event("jc:auth-cleared"));
}

export function createApiClient(baseURL) {
  const client = axios.create({
    baseURL,
    timeout: 10000,
    headers: { "Content-Type": "application/json" },
  });

  client.interceptors.request.use((config) => {
    const token = localStorage.getItem("accessToken");
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) clearStoredAuth();
      return Promise.reject(error);
    },
  );

  return client;
}

const apiClient = createApiClient(API_BASE_URL);

export const unwrapApiResponse = (response) => response.data?.data ?? response.data;

export const getApiErrorMessage = (error, fallbackMessage = "요청 처리에 실패했습니다.") => (
  error.response?.data?.message || error.response?.data?.error?.message || fallbackMessage
);

export default apiClient;
