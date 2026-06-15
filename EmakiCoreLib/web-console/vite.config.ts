import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replace(/\\/g, '/');
          if (normalized.includes('/node_modules/@codemirror/')) return 'codemirror';
          if (normalized.includes('/node_modules/react/') || normalized.includes('/node_modules/react-dom/')) return 'react-vendor';
          if (normalized.includes('/node_modules/yaml/')) return 'yaml-vendor';
          if (normalized.endsWith('/src/GuiEditorSurface.tsx')) return 'gui-surface';
          if (normalized.endsWith('/src/ItemEditorSurface.tsx')) return 'item-surface';
          return undefined;
        }
      }
    }
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:38765'
    }
  }
});
