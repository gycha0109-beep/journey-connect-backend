import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendTarget = env.VITE_DEV_BACKEND_URL;

  return {
    plugins: [react()],
    server: backendTarget
      ? {
          proxy: {
            "/api": { target: backendTarget, changeOrigin: true },
          },
        }
      : undefined,
  };
});
