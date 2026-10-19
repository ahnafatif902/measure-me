import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// base: "./" keeps the built app openable straight from file:// as well as from a server.
export default defineConfig({
  base: "./",
  plugins: [react(), tailwindcss()],
});
