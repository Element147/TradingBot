import path from 'path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import { visualizer } from 'rollup-plugin-visualizer';

const vendorChunkFromModule = (id: string): string | undefined => {
  const normalizedId = id.replaceAll('\\', '/');

  if (!normalizedId.includes('/node_modules/')) {
    return undefined;
  }

  if (
    normalizedId.includes('/node_modules/react/') ||
    normalizedId.includes('/node_modules/react-dom/') ||
    normalizedId.includes('/node_modules/react-router-dom/')
  ) {
    return 'vendor-react';
  }
  if (
    normalizedId.includes('/node_modules/react-redux/') ||
    normalizedId.includes('/node_modules/@reduxjs/toolkit/')
  ) {
    return 'vendor-redux';
  }
  if (
    normalizedId.includes('/node_modules/@mui/material/') ||
    normalizedId.includes('/node_modules/@mui/icons-material/') ||
    normalizedId.includes('/node_modules/@emotion/react/') ||
    normalizedId.includes('/node_modules/@emotion/styled/')
  ) {
    return 'vendor-mui';
  }
  if (normalizedId.includes('/node_modules/recharts/')) {
    return 'vendor-charts';
  }

  return undefined;
};

export default defineConfig({
  plugins: [
    react(),
    ...(process.env.ANALYZE_BUNDLE === 'true'
      ? [
          visualizer({
            filename: 'dist/bundle-analysis.html',
            gzipSize: true,
            brotliSize: true,
          }),
        ]
      : []),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@/app': path.resolve(__dirname, './src/app'),
      '@/features': path.resolve(__dirname, './src/features'),
      '@/components': path.resolve(__dirname, './src/components'),
      '@/hooks': path.resolve(__dirname, './src/hooks'),
      '@/services': path.resolve(__dirname, './src/services'),
      '@/utils': path.resolve(__dirname, './src/utils'),
      '@/types': path.resolve(__dirname, './src/types'),
    },
  },
  build: {
    sourcemap: true,
    target: 'es2022',
    rollupOptions: {
      output: {
        manualChunks: vendorChunkFromModule,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/tests/setup.ts'],
    testTimeout: 15000,
    hookTimeout: 15000,
  },
});
