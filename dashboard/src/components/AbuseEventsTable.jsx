import React from 'react';

const ACTION_COLORS = {
  LOG_ONLY:   '#64748b',
  TEMP_BLOCK: '#d97706',
  LONG_BAN:   '#dc2626'
};

export default function AbuseEventsTable({ events }) {
  if (!events || events.length === 0) {
    return <p className="muted">No abuse events recorded yet.</p>;
  }
  return (
    <table>
      <thead>
        <tr><th>Time</th><th>API key</th><th>Rule fired</th><th>Action</th></tr>
      </thead>
      <tbody>
        {events.map(e => (
          <tr key={e.id}>
            <td>{new Date(e.eventTime).toLocaleTimeString()}</td>
            <td><code>{e.apiKey}</code></td>
            <td>{e.ruleName}</td>
            <td>
              <span className="pill" style={{ background: ACTION_COLORS[e.actionTaken] || '#64748b' }}>
                {e.actionTaken}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}