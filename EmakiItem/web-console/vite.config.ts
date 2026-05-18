import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react({ jsxRuntime: 'classic' })],
  build: {
    outDir: '../src/main/resources/web-extensions',
    emptyOutDir: false,
    lib: {
      entry: 'src/main.tsx',
      name: 'EmakiItemWebConsoleExtension',
      formats: ['iife'],
      fileName: () => 'emakiitem-locale.js'
    },
    rollupOptions: {
      external: ['react', 'emaki-web-console'],
      output: {
        globals: {
          react: 'React',
          'emaki-web-console': 'EmakiWebConsole'
        }
      }
    }
  }
});
