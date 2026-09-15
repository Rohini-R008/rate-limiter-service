// Base URL of the rate-limiter service. Override with VITE_API_BASE if needed.
const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

async function get(path) {
  const res = await fetch(`${BASE}/api/dashboard${path}`);
  if (!res.ok) throw new Error(`${path} -> ${res.status}`);
  return res.json();
}

export const fetchRates       = ()      => get('/rates');
export const fetchAbuseEvents = (limit) => get(`/abuse-events?limit=${limit}`);
export const fetchBans        = ()      => get('/bans');
export const fetchVolume      = (buckets)=> get(`/volume?buckets=${buckets}`);