import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react({ jsxRuntime: 'classic' })],
  build: {
    outDir: '../src/main/resources/web-extensions',
    emptyOutDir: false,
    lib: {
      entry: 'src/main.tsx',
      name: 'EmakiGemWebConsoleExtension',
      formats: ['iife'],
      fileName: () => 'emakigem-item-surface.js'
    },
    rollupOptions: {
      external: ['react'],
      output: {
        globals: {
          react: 'React'
        }
      }
    }
  }
});
