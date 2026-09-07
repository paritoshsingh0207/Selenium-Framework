const $ = id => document.getElementById(id);
let API_BASE = localStorage.getItem('photosMigratorApi') || 'http://localhost:8080';
let ACCESS_KEY = sessionStorage.getItem('photosMigratorKey') || '';
let sessionId = localStorage.getItem('photosPickerSession') || '';
let pollTimer = null;

$('apiBase').value = API_BASE;
$('accessKey').value = ACCESS_KEY;
$('migrationId').value = localStorage.getItem('photosMigrationId') || `migration-${new Date().toISOString().slice(0,10)}`;

function log(message) { $('activity').textContent = `[${new Date().toLocaleTimeString()}] ${message}\n` + $('activity').textContent; }
function authHeaders() { return ACCESS_KEY ? {'X-Migrator-Key': ACCESS_KEY} : {}; }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {headers:{'Content-Type':'application/json', ...authHeaders(), ...(options.headers||{})}, ...options});
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try { const body = await response.json(); message = body.message || message; } catch (_) {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  const type = response.headers.get('content-type') || '';
  return type.includes('json') ? response.json() : response;
}
function durationMs(value, fallback=3000) {
  const match = /^([0-9]+(?:\.[0-9]+)?)s$/.exec(value || '');
  return match ? Math.max(1000, Number(match[1]) * 1000) : fallback;
}
async function health() {
  try { const data = await api('/api/health'); $('backendStatus').textContent = `✓ ${data.status} · ${data.service}`; }
  catch (e) { $('backendStatus').textContent = `Not connected · ${e.message}`; }
}
async function authStatus() {
  try {
    const data = await api('/api/oauth/status');
    const s = data.source || {}; const d = data.destination || {};
    $('sourceStatus').textContent = s.connected ? `✓ ${s.email || 'Connected'}` : 'Not connected';
    $('destinationStatus').textContent = d.connected ? `✓ ${d.email || 'Connected'}` : 'Not connected';
  } catch (e) { log(`Account status: ${e.message}`); }
}
async function connect(role) {
  try { const data = await api(`/api/oauth/${role}/start`); window.location.href = data.authorizationUrl; }
  catch (e) { log(`OAuth: ${e.message}`); }
}
async function createPicker() {
  try {
    const maxItemCount = Math.min(2000, Math.max(1, Number($('pickerMax').value) || 2000));
    const data = await api('/api/picker/sessions', {method:'POST', body:JSON.stringify({maxItemCount})});
    sessionId = data.id; localStorage.setItem('photosPickerSession', sessionId);
    $('pickerStatus').textContent = `Session ${sessionId} · waiting for selection`;
    window.open(`${data.pickerUri}/autoclose`, '_blank', 'noopener');
    pollPicker(data.pollingConfig?.pollInterval);
  } catch (e) { log(`Picker: ${e.message}`); }
}
function pollPicker(interval) {
  clearTimeout(pollTimer);
  pollTimer = setTimeout(async () => {
    try {
      const data = await api(`/api/picker/sessions/${encodeURIComponent(sessionId)}`);
      if (data.mediaItemsSet) {
        const items = await api(`/api/picker/sessions/${encodeURIComponent(sessionId)}/items`);
        $('pickerStatus').textContent = `✓ ${items.length} items selected`;
        log(`Picker ready with ${items.length} items.`);
      } else {
        $('pickerStatus').textContent = 'Waiting for you to finish selecting in Google Photos…';
        pollPicker(data.pollingConfig?.pollInterval);
      }
    } catch (e) { log(`Picker polling: ${e.message}`); }
  }, durationMs(interval));
}
async function transfer() {
  if (!sessionId) return log('Create and complete a Picker session first.');
  const migrationId = $('migrationId').value.trim();
  if (!migrationId) return log('Migration ID is required.');
  localStorage.setItem('photosMigrationId', migrationId);
  const maxItems = Math.min(100, Math.max(1, Number($('batchSize').value) || 25));
  const button = $('transfer');
  button.disabled = true;
  try {
    let requestNumber = 0;
    while (true) {
      requestNumber++;
      log(`Starting safe request ${requestNumber}, up to ${maxItems} uploads…`);
      const result = await api(`/api/migrations/${encodeURIComponent(migrationId)}/transfer`, {
        method:'POST',
        body:JSON.stringify({sessionId,maxItems})
      });
      const waiting = result.waiting || 0;
      log(`Request ${requestNumber}: ${result.verified} verified, ${result.failed} failed, ${waiting} waiting, ${result.skipped} already complete.`);
      await summary();

      if (result.failed > 0) {
        log('Transfer paused because one or more items failed. Review the Excel/status and press Resume when ready.');
        break;
      }
      if (waiting > 0) {
        log('Transfer paused while Google Photos finishes processing video(s). Press Resume later; waiting does not consume retry attempts.');
        break;
      }
      if (result.processed === 0) {
        log('No more transferable items remain in this Picker session. Migration pass is complete.');
        break;
      }
      await sleep(500);
    }
  } catch (e) {
    log(`Transfer: ${e.message}`);
  } finally {
    button.disabled = false;
  }
}
async function summary() {
  const migrationId = $('migrationId').value.trim(); if (!migrationId) return;
  try {
    const data = await api(`/api/migrations/${encodeURIComponent(migrationId)}/summary`);
    const entries = Object.entries(data);
    $('summary').innerHTML = entries.length ? entries.map(([k,v]) => `<div><strong>${v}</strong><span>${k.replaceAll('_',' ')}</span></div>`).join('') : '<span>No items recorded yet.</span>';
  } catch (e) { log(`Summary: ${e.message}`); }
}
async function downloadReport() {
  const migrationId = $('migrationId').value.trim(); if (!migrationId) return log('Migration ID is required.');
  try {
    const response = await fetch(`${API_BASE}/api/migrations/${encodeURIComponent(migrationId)}/report.xlsx`, {headers: authHeaders()});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `${migrationId.replace(/[^a-zA-Z0-9._-]/g,'_')}.xlsx`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (e) { log(`Report: ${e.message}`); }
}

$('saveApi').onclick = () => {
  API_BASE = $('apiBase').value.trim().replace(/\/$/, '');
  ACCESS_KEY = $('accessKey').value;
  localStorage.setItem('photosMigratorApi', API_BASE);
  sessionStorage.setItem('photosMigratorKey', ACCESS_KEY);
  health(); authStatus();
};
$('connectSource').onclick = () => connect('source');
$('connectDestination').onclick = () => connect('destination');
$('startPicker').onclick = createPicker;
$('transfer').onclick = transfer;
$('refreshSummary').onclick = summary;
$('downloadReport').onclick = downloadReport;

const query = new URLSearchParams(location.search);
if (query.get('oauth')) log(`OAuth completed: ${query.get('oauth')}`);
if (query.get('oauth_error')) log('OAuth could not be completed. Check the backend logs and reconnect the account.');
health(); authStatus();
if (sessionId) { $('pickerStatus').textContent = `Existing session ${sessionId}; checking…`; pollPicker('1s'); }
