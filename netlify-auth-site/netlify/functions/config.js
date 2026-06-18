const { json, options } = require("./_backend");

exports.handler = async (event) => {
  const cors = options(event);
  if (cors) return cors;

  return json(200, {
    backend: "netlify",
    cloudSync: "netlify_account_snapshot",
    authSurface: "netlify_functions_supabase_auth_bridge",
    tvAuthStorage: "netlify_blobs",
    proxies: {
      tmdb: true,
      trakt: true
    },
    supabaseFallback: true,
    mediaProxy: false,
    timestamp: new Date().toISOString()
  });
};
