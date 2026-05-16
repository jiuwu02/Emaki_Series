import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react({ jsxRuntime: 'classic' })],
  build: {
    outDir: '../src/main/resources/web-extensions',
    emptyOutDir: false,
    lib: {
      entry: 'src/main.tsx',
      name: 'EmakiStrengthenWebConsoleExtension',
      formats: ['iife'],
      fileName: () => 'emakistrengthen-gui-surface.js'
    },
    rollupOptions: {
      external: ['react'],
      output: { globals: { react: 'React' } }
    }
  }
});
