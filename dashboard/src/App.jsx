import React, { useEffect, useState } from 'react';
import { fetchRates, fetchAbuseEvents, fetchBans, fetchVolume } from './api.js';
import VolumeChart from './components/VolumeChart.jsx';
import AbuseEventsTable from './components/AbuseEventsTable.jsx';

const REFRESH_MS = 3000;

export default function App() {
  const [volume, setVolume] = useState([]);
  const [events, setEvents] = useState([]);
  const [rates, setRates] = useState([]);
  const [bans, setBans] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [v, e, r, b] = await Promise.all([
          fetchVolume(30), fetchAbuseEvents(50), fetchRates(), fetchBans()
        ]);
        if (!cancelled) { setVolume(v); setEvents(e); setRates(r); setBans(b); setError(null); }
      } catch (err) {
        if (!cancelled) setError(err.message);
      }
    }
    load();
    const id = setInterval(load, REFRESH_MS);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  return (
    <div className="wrap">
      <h1>Rate Limiter &amp; Abuse Dashboard</h1>
      {error && <p className="error">Cannot reach API: {error}</p>}

      <section className="card">
        <h2>Request volume over time</h2>
        <VolumeChart points={volume} />
      </section>

      <section className="card">
        <h2>Recent abuse events</h2>
        <AbuseEventsTable events={events} />
      </section>

      <div className="row">
        <section className="card half">
          <h2>Current rate per key</h2>
          {rates.length === 0 ? <p className="muted">No active keys.</p> : (
            <table>
              <thead><tr><th>API key</th><th>Used / limit</th></tr></thead>
              <tbody>
                {rates.map(r => (
                  <tr key={r.apiKey}>
                    <td><code>{r.apiKey}</code></td>
                    <td>{r.used} / {r.capacity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card half">
          <h2>Active bans</h2>
          {bans.length === 0 ? <p className="muted">No active bans.</p> : (
            <table>
              <thead><tr><th>API key</th><th>Expires in</th></tr></thead>
              <tbody>
                {bans.map(b => (
                  <tr key={b.apiKey}>
                    <td><code>{b.apiKey}</code></td>
                    <td>{b.ttlSeconds}s</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  );
}