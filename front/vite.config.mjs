import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

const proxyPaths = [
  '/api',
  '/database',
  '/user',
  '/webauthn',
  '/sql',
  '/version',
  '/health',
];

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_BACKEND_ORIGIN || 'http://localhost:8002';

  return {
    plugins: [
      react({
        include: /\.[jt]sx?$/,
      }),
    ],
    build: {
      outDir: 'dist',
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined;
            }
            if (id.includes('/@codemirror/') || id.includes('/@uiw/react-codemirror/')) {
              return 'codemirror';
            }
            if (id.includes('/recharts/') || id.includes('/victory-vendor/') || id.includes('/d3-')) {
              return 'recharts';
            }
            if (
              id.includes('/react/')
              || id.includes('/react-dom/')
              || id.includes('/react-router/')
              || id.includes('/react-router-dom/')
              || id.includes('/scheduler/')
            ) {
              return 'react';
            }
            return undefined;
          },
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    optimizeDeps: {
      esbuildOptions: {
        define: {
          global: 'globalThis',
        },
      },
    },
    server: {
      proxy: Object.fromEntries(
        proxyPaths.map((path) => [
          path,
          {
            target,
            changeOrigin: true,
          },
        ]),
      ),
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/app/setupTests.js',
    },
  };
});
