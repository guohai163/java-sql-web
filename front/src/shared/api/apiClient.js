import FetchHttpClient, { json } from 'fetch-http-client';
import config from '@/shared/config/runtimeConfig';

export function createClient() {
  const client = new FetchHttpClient(config.serverDomain);
  client.addMiddleware(json());
  return client;
}
