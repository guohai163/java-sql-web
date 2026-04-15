import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import cookie from 'react-cookies';
import Navigation from '@/features/workbench/components/Navigation';
import PageContent from '@/features/workbench/components/PageContent';
import { createClient } from '@/shared/api/apiClient';
import config from '@/shared/config/runtimeConfig';

function JavaSqlAdmin() {
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    const token = cookie.load('token');

    if (token === undefined) {
      navigate('/login', { replace: true });
      return undefined;
    }

    const client = createClient();

    void client.get('/version').then((response) => {
      if (!cancelled && response.jsonData.status) {
        config.version = response.jsonData.data;
      }
    });

    void client
      .get('/user/check', { headers: { 'User-Token': token } })
      .then((response) => {
        if (cancelled) {
          return;
        }

        if (response.status !== 200 || !response.jsonData.status) {
          navigate('/login', { replace: true });
          return;
        }

        config.userName = response.jsonData.data.userName;
      })
      .catch(() => {
        if (!cancelled) {
          navigate('/login', { replace: true });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  return (
    <div>
      <Navigation />
      <PageContent />
    </div>
  );
}

export default JavaSqlAdmin;
