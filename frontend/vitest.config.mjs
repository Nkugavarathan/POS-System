import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react({ include: /\.(js|jsx)$/ })],
  // Next.js allows JSX in .js files; tell Vite's transformer to parse them as JSX too.
  oxc: { include: /\.(js|jsx)$/, exclude: /node_modules/, lang: "jsx" },
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.js"],
    include: ["tests/unit/**/*.test.{js,jsx}"],
    css: false,
  },
});
