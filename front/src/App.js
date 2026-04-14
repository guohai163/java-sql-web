import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Admin from './Admin';
import JavaSqlAdmin from './JavaSqlAdmin';
import Login from './Login';
import SqlGuid from './sqlGuid';

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
        <Route path="/guid" element={<SqlGuid />} />
        <Route path="/" element={<JavaSqlAdmin />} />
        <Route path="*" element={<Navigate replace to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
