import React from 'react';
import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import '@/app/polyfills';
import '@/app/styles/global.css';
import App from '@/app/App';

ReactDOM.createRoot(document.getElementById('root')).render(
  <App />,
);
