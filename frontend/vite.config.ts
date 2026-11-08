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
    fs: {
      strict: true,
      deny: ['**/.*'],
    },
  },
});
