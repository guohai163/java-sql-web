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
    esbuild: {
      loader: 'jsx',
      include: /src\/.*\.[jt]sx?$/,
      exclude: [],
    },
    build: {
      outDir: 'dist',
    },
    optimizeDeps: {
      esbuildOptions: {
        loader: {
          '.js': 'jsx',
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
      setupFiles: './src/setupTests.js',
    },
  };
});
