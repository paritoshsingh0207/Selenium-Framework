const $ = id => document.getElementById(id);
const STORAGE_KEY = 'photosMigratorZeroBillingV1';
const PARTNER_STEPS = ['sourceShared','destinationAccepted','saveEnabled','verifyOldest','verifyNewest','verifyVideo','verifyRandom'];

function isoNow() { return new Date().toISOString(); }
function todayId() { return `migration-${new Date().toISOString().slice(0,10)}`; }
function emptyPartner() {
  return { status: 'PLANNED', verifiedAt: '', steps: Object.fromEntries(PARTNER_STEPS.map(k => [k, false])) };
}
function defaultState() {
  return { version: 1, migrationId: todayId(), sourceEmail: '', destinationEmail: '', mode: 'partner', partner: emptyPartner(), batches: [], audit: [] };
}
function loadState() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    if (!parsed || parsed.version !== 1) return defaultState();
    parsed.partner = parsed.partner || emptyPartner();
    parsed.partner.steps = { ...emptyPartner().steps, ...(parsed.partner.steps || {}) };
    parsed.batches = Array.isArray(parsed.batches) ? parsed.batches : [];
    parsed.audit = Array.isArray(parsed.audit) ? parsed.audit : [];
    return parsed;
  } catch (_) { return defaultState(); }
}
let state = loadState();

function persist() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }
function log(message) {
  const line = `[${new Date().toLocaleTimeString()}] ${message}`;
  $('activity').textContent = `${line}\n${$('activity').textContent}`.slice(0, 12000);
}
function audit(action, detail = '') {
  state.audit.unshift({ timestamp: isoNow(), action, detail });
  state.audit = state.audit.slice(0, 500);
  persist();
  log(detail ? `${action}: ${detail}` : action);
}
function clean(value) { return String(value ?? '').trim(); }
function safeName(value) { return clean(value).replace(/[^a-zA-Z0-9._-]+/g, '_') || 'migration'; }
function emailsDiffer() {
  return clean(state.sourceEmail).toLowerCase() !== clean(state.destinationEmail).toLowerCase();
}

function setMode(mode, record = true) {
  state.mode = mode === 'batch' ? 'batch' : 'partner';
  $('partnerPanel').classList.toggle('hidden', state.mode !== 'partner');
  $('batchPanel').classList.toggle('hidden', state.mode !== 'batch');
  $('modePartner').classList.toggle('active', state.mode === 'partner');
  $('modeBatch').classList.toggle('active', state.mode === 'batch');
  if (record) audit('MODE_CHANGED', state.mode === 'partner' ? 'Whole library / Partner Sharing' : 'Selected media / Shared Albums');
  else persist();
}

function partnerDerivedStatus() {
  if (state.partner.status === 'VERIFIED' && PARTNER_STEPS.every(k => state.partner.steps[k])) return 'VERIFIED';
  const s = state.partner.steps;
  if (s.verifyOldest || s.verifyNewest || s.verifyVideo || s.verifyRandom) return 'VERIFYING';
  if (s.saveEnabled) return 'SAVE_REQUESTED';
  if (s.destinationAccepted) return 'DESTINATION_ACCEPTED';
  if (s.sourceShared) return 'SOURCE_SHARED';
  return 'PLANNED';
}
function renderPartner() {
  document.querySelectorAll('#partnerChecklist input[data-step]').forEach(input => {
    input.checked = Boolean(state.partner.steps[input.dataset.step]);
  });
  const status = partnerDerivedStatus();
  if (state.partner.status !== 'VERIFIED') state.partner.status = status;
  $('partnerStatus').textContent = state.partner.status;
  $('partnerStatus').className = `status status-${state.partner.status.toLowerCase().replaceAll('_','-')}`;
  persist();
}

function nextBatchId() {
  const max = state.batches.reduce((m, b) => {
    const n = Number((b.id || '').match(/BATCH-(\d+)/)?.[1] || 0);
    return Math.max(m, n);
  }, 0);
  return `BATCH-${String(max + 1).padStart(3, '0')}`;
}
function prepareNewBatch() {
  const id = nextBatchId();
  $('batchId').value = id;
  $('batchTitle').value = `${safeName(state.migrationId)}-${id}`;
  $('batchStatus').value = 'PLANNED';
  $('sourceCount').value = '';
  $('destinationCount').value = '';
  $('batchNotes').value = '';
  $('saveBatch').textContent = 'Add batch';
}
function batchFailureReason(b) {
  if (b.status === 'NEEDS_REVIEW') return b.notes || 'Marked NEEDS_REVIEW';
  if (Number.isFinite(b.sourceCount) && Number.isFinite(b.destinationCount) && b.sourceCount > 0 && b.destinationCount > 0 && b.sourceCount !== b.destinationCount) {
    return `Count mismatch: source ${b.sourceCount}, destination ${b.destinationCount}`;
  }
  return '';
}
function renderBatches() {
  const tbody = $('batchRows');
  tbody.innerHTML = '';
  if (!state.batches.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="empty">No selected-media batches yet.</td></tr>';
  } else {
    [...state.batches].reverse().forEach(b => {
      const tr = document.createElement('tr');
      const values = [b.id, b.title, b.sourceCount ?? '', b.destinationCount ?? '', b.status, new Date(b.updatedAt).toLocaleString()];
      values.forEach((value, idx) => {
        const td = document.createElement('td');
        td.textContent = value;
        if (idx === 4) td.className = `pill status-${String(value).toLowerCase().replaceAll('_','-')}`;
        tr.appendChild(td);
      });
      const actions = document.createElement('td');
      const edit = document.createElement('button');
      edit.className = 'tiny secondary'; edit.textContent = 'Edit';
      edit.onclick = () => editBatch(b.id);
      actions.appendChild(edit); tr.appendChild(actions); tbody.appendChild(tr);
    });
  }
  renderSummary();
}
function editBatch(id) {
  const b = state.batches.find(x => x.id === id); if (!b) return;
  $('batchId').value = b.id; $('batchTitle').value = b.title; $('batchStatus').value = b.status;
  $('sourceCount').value = b.sourceCount ?? ''; $('destinationCount').value = b.destinationCount ?? ''; $('batchNotes').value = b.notes || '';
  $('saveBatch').textContent = 'Update batch';
  setMode('batch', false);
  window.scrollTo({ top: $('batchPanel').offsetTop - 20, behavior: 'smooth' });
}
function saveBatch() {
  const id = clean($('batchId').value) || nextBatchId();
  const title = clean($('batchTitle').value) || `${safeName(state.migrationId)}-${id}`;
  let status = $('batchStatus').value;
  const sourceRaw = clean($('sourceCount').value), destRaw = clean($('destinationCount').value);
  const sourceCount = sourceRaw === '' ? null : Number(sourceRaw);
  const destinationCount = destRaw === '' ? null : Number(destRaw);
  const notes = clean($('batchNotes').value);
  if ([sourceCount,destinationCount].some(v => v !== null && (!Number.isInteger(v) || v < 0))) return log('Batch counts must be whole numbers 0 or greater.');
  if (status === 'VERIFIED' && (!(sourceCount > 0) || sourceCount !== destinationCount)) {
    $('batchStatus').value = 'NEEDS_REVIEW';
    log('VERIFIED blocked: source and destination counts must be greater than zero and equal. Status changed to NEEDS_REVIEW.');
    status = 'NEEDS_REVIEW';
  }
  const existing = state.batches.find(b => b.id === id);
  const now = isoNow();
  if (existing) Object.assign(existing, { title, status, sourceCount, destinationCount, notes, updatedAt: now });
  else state.batches.push({ id, mode: 'SHARED_ALBUM', title, status, sourceCount, destinationCount, notes, createdAt: now, updatedAt: now });
  audit(existing ? 'BATCH_UPDATED' : 'BATCH_ADDED', `${id} · ${status}`);
  renderBatches(); prepareNewBatch();
}

function renderSummary() {
  const counts = state.batches.reduce((acc,b) => { acc[b.status] = (acc[b.status] || 0) + 1; return acc; }, {});
  const verified = counts.VERIFIED || 0;
  const review = state.batches.filter(b => batchFailureReason(b)).length;
  const cards = [
    ['Partner', state.partner.status],
    ['Batches', state.batches.length],
    ['Verified', verified],
    ['Needs review', review],
    ['Billing', '₹0'],
  ];
  $('summary').innerHTML = cards.map(([label,value]) => `<div><strong>${String(value)}</strong><span>${label}</span></div>`).join('');
}

function saveSetup() {
  const migrationId = clean($('migrationId').value);
  const sourceEmail = clean($('sourceEmail').value);
  const destinationEmail = clean($('destinationEmail').value);
  if (!migrationId || !sourceEmail || !destinationEmail) {
    $('setupStatus').textContent = 'Migration ID and both account emails are required.'; return;
  }
  if (sourceEmail.toLowerCase() === destinationEmail.toLowerCase()) {
    $('setupStatus').textContent = 'Account A and Account B must be different.'; return;
  }
  state.migrationId = migrationId; state.sourceEmail = sourceEmail; state.destinationEmail = destinationEmail;
  persist(); audit('SETUP_SAVED', `${sourceEmail} → ${destinationEmail}`);
  $('setupStatus').textContent = '✓ Stored locally in this browser';
  if (!$('batchTitle').value || $('batchTitle').value.startsWith('migration-')) prepareNewBatch();
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
function exportJson() {
  audit('JSON_EXPORTED');
  downloadBlob(new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' }), `${safeName(state.migrationId)}.json`);
}

// Minimal dependency-free XLSX writer. It creates a standards-based Open XML workbook
// stored in an uncompressed ZIP, keeping the zero-backend dashboard self-contained.
const encoder = new TextEncoder();
let crcTable;
function getCrcTable() {
  if (crcTable) return crcTable;
  crcTable = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    crcTable[n] = c >>> 0;
  }
  return crcTable;
}
function crc32(bytes) {
  const table = getCrcTable(); let c = 0xFFFFFFFF;
  for (const b of bytes) c = table[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function u16(n) { return new Uint8Array([n & 255, (n >>> 8) & 255]); }
function u32(n) { return new Uint8Array([n & 255, (n >>> 8) & 255, (n >>> 16) & 255, (n >>> 24) & 255]); }
function concat(parts) {
  const total = parts.reduce((n,p) => n + p.length, 0), out = new Uint8Array(total); let off = 0;
  for (const p of parts) { out.set(p, off); off += p.length; } return out;
}
function dosDateTime(date = new Date()) {
  const year = Math.max(1980, date.getFullYear());
  return { time: (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1), date: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate() };
}
function makeZip(files) {
  const locals = [], centrals = []; let offset = 0; const dt = dosDateTime();
  for (const file of files) {
    const name = encoder.encode(file.name), data = typeof file.data === 'string' ? encoder.encode(file.data) : file.data;
    const crc = crc32(data), flags = 0x0800;
    const local = concat([u32(0x04034b50),u16(20),u16(flags),u16(0),u16(dt.time),u16(dt.date),u32(crc),u32(data.length),u32(data.length),u16(name.length),u16(0),name,data]);
    locals.push(local);
    centrals.push(concat([u32(0x02014b50),u16(20),u16(20),u16(flags),u16(0),u16(dt.time),u16(dt.date),u32(crc),u32(data.length),u32(data.length),u16(name.length),u16(0),u16(0),u16(0),u16(0),u32(0),u32(offset),name]));
    offset += local.length;
  }
  const central = concat(centrals);
  const end = concat([u32(0x06054b50),u16(0),u16(0),u16(files.length),u16(files.length),u32(central.length),u32(offset),u16(0)]);
  return concat([...locals, central, end]);
}
function xml(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c])); }
function colName(index) {
  let n = index + 1, s = '';
  while (n) { n--; s = String.fromCharCode(65 + (n % 26)) + s; n = Math.floor(n / 26); }
  return s;
}
function sheetXml(rows) {
  const body = rows.map((row, r) => `<row r="${r+1}">${row.map((v,c) => {
    const ref = `${colName(c)}${r+1}`;
    if (typeof v === 'number' && Number.isFinite(v)) return `<c r="${ref}"><v>${v}</v></c>`;
    return `<c r="${ref}" t="inlineStr"><is><t xml:space="preserve">${xml(v)}</t></is></c>`;
  }).join('')}</row>`).join('');
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${body}</sheetData></worksheet>`;
}
function workbookBlob() {
  const failures = state.batches.map(b => ({...b, reason: batchFailureReason(b)})).filter(b => b.reason);
  const summaryRows = [
    ['Metric','Value'], ['Migration ID',state.migrationId], ['Source account',state.sourceEmail], ['Destination account',state.destinationEmail],
    ['Current mode',state.mode], ['Partner status',state.partner.status], ['Selected batches',state.batches.length],
    ['Verified batches',state.batches.filter(b => b.status === 'VERIFIED').length], ['Needs review',failures.length], ['Infrastructure billing','₹0 / none']
  ];
  const itemRows = [['ID','Mode','Title','Source Count','Destination Count','Status','Notes','Created','Updated']];
  itemRows.push(['WHOLE-LIBRARY','PARTNER_SHARING','Whole library','','',state.partner.status,'Google Photos native Partner Sharing','',state.partner.verifiedAt || '']);
  for (const b of state.batches) itemRows.push([b.id,b.mode,b.title,b.sourceCount ?? '',b.destinationCount ?? '',b.status,b.notes,b.createdAt,b.updatedAt]);
  const failureRows = [['ID','Status','Reason','Updated'], ...failures.map(b => [b.id,b.status,b.reason,b.updatedAt])];
  const auditRows = [['Timestamp','Action','Detail'], ...state.audit.map(a => [a.timestamp,a.action,a.detail])];
  const configRows = [['Key','Value'], ['Architecture','GitHub Pages + Google Photos native sharing'], ['Backend','None'], ['Google Cloud billing','Not required'], ['OAuth tokens','None'], ['Media relay','None'], ['Source deletion','Manual only'], ['Partner sourceShared',state.partner.steps.sourceShared], ['Partner destinationAccepted',state.partner.steps.destinationAccepted], ['Partner saveEnabled',state.partner.steps.saveEnabled], ['Partner verifyOldest',state.partner.steps.verifyOldest], ['Partner verifyNewest',state.partner.steps.verifyNewest], ['Partner verifyVideo',state.partner.steps.verifyVideo], ['Partner verifyRandom',state.partner.steps.verifyRandom]];
  const sheets = [['Summary',summaryRows],['Items',itemRows],['Failures',failureRows],['Audit',auditRows],['Config',configRows]];
  const workbookSheets = sheets.map((s,i) => `<sheet name="${xml(s[0])}" sheetId="${i+1}" r:id="rId${i+1}"/>`).join('');
  const rels = sheets.map((s,i) => `<Relationship Id="rId${i+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i+1}.xml"/>`).join('') + `<Relationship Id="rId${sheets.length+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>`;
  const overrides = sheets.map((s,i) => `<Override PartName="/xl/worksheets/sheet${i+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('');
  const files = [
    {name:'[Content_Types].xml',data:`<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${overrides}</Types>`},
    {name:'_rels/.rels',data:'<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'},
    {name:'xl/workbook.xml',data:`<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${workbookSheets}</sheets></workbook>`},
    {name:'xl/_rels/workbook.xml.rels',data:`<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${rels}</Relationships>`},
    {name:'xl/styles.xml',data:'<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="1"><xf xfId="0"/></cellXfs></styleSheet>'},
    ...sheets.map((s,i) => ({name:`xl/worksheets/sheet${i+1}.xml`,data:sheetXml(s[1])}))
  ];
  return new Blob([makeZip(files)], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}
function exportXlsx() {
  audit('XLSX_EXPORTED');
  downloadBlob(workbookBlob(), `${safeName(state.migrationId)}.xlsx`);
}

function clearData() {
  if (!confirm('Clear the browser-local migration ledger? This does not affect Google Photos.')) return;
  localStorage.removeItem(STORAGE_KEY); state = defaultState(); initForm(); audit('LOCAL_LEDGER_RESET');
}
function initForm() {
  $('migrationId').value = state.migrationId; $('sourceEmail').value = state.sourceEmail; $('destinationEmail').value = state.destinationEmail;
  setMode(state.mode, false); renderPartner(); renderBatches(); prepareNewBatch();
}

$('saveSetup').onclick = saveSetup;
$('modePartner').onclick = () => setMode('partner');
$('modeBatch').onclick = () => setMode('batch');
$('partnerChecklist').onchange = event => {
  const step = event.target?.dataset?.step; if (!step) return;
  state.partner.steps[step] = event.target.checked;
  if (!event.target.checked && state.partner.status === 'VERIFIED') { state.partner.status = 'VERIFYING'; state.partner.verifiedAt = ''; }
  audit('PARTNER_STEP', `${step}=${event.target.checked}`); renderPartner(); renderSummary();
};
$('partnerVerified').onclick = () => {
  if (!emailsDiffer() || !state.sourceEmail || !state.destinationEmail) return log('Save two different source/destination accounts before verification.');
  if (!PARTNER_STEPS.every(k => state.partner.steps[k])) return log('VERIFIED blocked: complete every Partner Sharing verification checkbox first.');
  state.partner.status = 'VERIFIED'; state.partner.verifiedAt = isoNow(); audit('PARTNER_VERIFIED', 'Whole-library verification checklist completed'); renderPartner(); renderSummary();
};
$('saveBatch').onclick = saveBatch;
$('newBatch').onclick = prepareNewBatch;
$('exportXlsx').onclick = exportXlsx;
$('exportJson').onclick = exportJson;
$('clearData').onclick = clearData;

initForm();
