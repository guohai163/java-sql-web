import React, { Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

const Admin = React.lazy(() => import('@/features/admin/Admin'));
const JavaSqlAdmin = React.lazy(() => import('@/features/workbench/JavaSqlAdmin'));
const Login = React.lazy(() => import('@/features/login/Login'));
const SecurityTask = React.lazy(() => import('@/features/security-task/SecurityTask'));
const SqlGuide = React.lazy(() => import('@/features/sql-guide/SqlGuide'));

function RouteFallback(): React.JSX.Element {
  return (
    <div
      aria-live="polite"
      style={{
        alignItems: 'center',
        color: '#334155',
        display: 'flex',
        fontSize: 14,
        justifyContent: 'center',
        minHeight: '100vh',
      }}
    >
      页面加载中...
    </div>
  );
}

function App(): React.JSX.Element {
  return (
    <BrowserRouter
      future={{
        v7_relativeSplatPath: true,
        v7_startTransition: true,
      }}
    >
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/security-task" element={<SecurityTask />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="/guid" element={<SqlGuide />} />
          <Route path="/" element={<JavaSqlAdmin />} />
          <Route path="*" element={<Navigate replace to="/" />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
