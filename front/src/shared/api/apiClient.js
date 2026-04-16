import FetchHttpClient, { json } from 'fetch-http-client';
import cookie from 'react-cookies';
import config from '@/shared/config/runtimeConfig';

let loginRedirecting = false;

function shouldRedirectToLogin(response) {
  if (!response) {
    return false;
  }

  if (response.status === 401) {
    return true;
  }

  const message = response.jsonData?.message;
  return (
    response.jsonData?.status === false
    && typeof message === 'string'
    && message.trim().toLowerCase() === 'not logged in'
  );
}

function redirectToLogin() {
  if (loginRedirecting) {
    return;
  }

  if (window.location.pathname === '/login') {
    return;
  }

  loginRedirecting = true;
  cookie.remove('token', { path: '/' });
  window.location.replace('/login');
}

export function createClient() {
  const client = new FetchHttpClient(config.serverDomain);
  client.addMiddleware(json());
  client.addMiddleware(() => (response) => {
    if (shouldRedirectToLogin(response)) {
      redirectToLogin();
    }
    return response;
  });
  return client;
}
