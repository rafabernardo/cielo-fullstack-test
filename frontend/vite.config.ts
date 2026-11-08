/// <reference types="vitest/config" />
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import path from 'node:path';
import svgr from 'vite-plugin-svgr';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), svgr(), babel({ presets: [reactCompilerPreset()] })],
  css: { modules: { localsConvention: 'camelCaseOnly' } },
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  server: {
    strictPort: true,
    // The backend has no CORS: the browser talks to it through this proxy.
    proxy: { '/api': 'http://localhost:8080' },
    fs: {
      strict: true,
      deny: ['**/.*'],
    },
  },
  test: {
    environment: 'jsdom',
    // Globals let Testing Library clean up the DOM after each test on its own.
    globals: true,
    setupFiles: ['./src/testing/setup.ts'],
  },
});
