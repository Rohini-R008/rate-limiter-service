import React from 'react';

// Hand-rolled SVG bar chart - no charting library (AGENTS.md: plain React).
export default function VolumeChart({ points }) {
  const width = 720, height = 200, pad = 28;
  if (!points || points.length === 0) {
    return <p className="muted">No volume data yet.</p>;
  }

  const max = Math.max(1, ...points.map(p => p.requests));
  const barW = (width - pad * 2) / points.length;

  return (
    <svg width={width} height={height} role="img" aria-label="Request volume over time">
      {/* y-axis baseline */}
      <line x1={pad} y1={height - pad} x2={width - pad} y2={height - pad} stroke="#cbd5e1" />
      {points.map((p, i) => {
        const h = Math.round((p.requests / max) * (height - pad * 2));
        const x = pad + i * barW;
        const y = height - pad - h;
        return (
          <g key={p.timestampMs}>
            <rect x={x + 1} y={y} width={Math.max(1, barW - 2)} height={h}
                  fill="#2563eb">
              <title>{new Date(p.timestampMs).toLocaleTimeString()}: {p.requests} req</title>
            </rect>
          </g>
        );
      })}
      <text x={pad} y={16} fontSize="12" fill="#64748b">peak {max} req/bucket</text>
    </svg>
  );
}