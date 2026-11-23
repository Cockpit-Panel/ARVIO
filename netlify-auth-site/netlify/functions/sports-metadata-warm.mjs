import { getStore } from '@netlify/blobs';
import metadata from './_sports-metadata.js';

export const config = { schedule: '*/5 * * * *' };

export default async () => {
  await metadata.getMetadata({
    store: getStore({ name: 'sports-metadata', consistency: 'strong' }),
    apiKey: process.env.SPORTSDB_API_KEY,
    supplemental: process.env.SPORTS_SUPPLEMENTAL_ENABLED !== 'false',
    refreshSupplemental: true,
  });
  return new Response(null, { status: 204 });
};
