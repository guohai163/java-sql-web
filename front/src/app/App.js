import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Admin from '@/features/admin/Admin';
import Login from '@/features/login/Login';
import SqlGuide from '@/features/sql-guide/SqlGuide';
import JavaSqlAdmin from '@/features/workbench/JavaSqlAdmin';

function App() {
  return (
    <BrowserRouter
      future={{
        v7_relativeSplatPath: true,
        v7_startTransition: true,
      }}
    >
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/admin" element={<Admin />} />
        <Route path="/guid" element={<SqlGuide />} />
        <Route path="/" element={<JavaSqlAdmin />} />
        <Route path="*" element={<Navigate replace to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
