const API_BASE = window.PHOTOS_MIGRATOR_API || 'http://localhost:8080';
const statusEl = document.getElementById('backendStatus');

fetch(`${API_BASE}/api/health`)
  .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
  .then(data => { statusEl.textContent = `${data.status} — ${data.service}`; })
  .catch(err => { statusEl.textContent = `Not connected (${err.message})`; });
