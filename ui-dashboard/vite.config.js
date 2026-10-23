import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: process.env.HVPPS_LOCAL_DEMO === "true" ? {
      "/api/dashboard": "http://127.0.0.1:8083",
      "/api/payments": "http://127.0.0.1:8081"
    } : {
      "/api": "http://localhost:8080"
    }
  }
});
