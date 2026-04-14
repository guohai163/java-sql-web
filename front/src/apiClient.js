import FetchHttpClient, { json } from 'fetch-http-client';
import config from './config';

export function createClient() {
  const client = new FetchHttpClient(config.serverDomain);
  client.addMiddleware(json());
  return client;
}
