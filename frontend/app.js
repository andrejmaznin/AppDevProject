'use strict';

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

const API = '/api/v1';

const METRICS = {
  heart_rate:  { label: 'Частота пульса', unit: 'bpm',  min: 60,   max: 90,   color: '#ff6b6b', decimals: 1 },
  cvp:         { label: 'ЦВД',            unit: 'mmHg', min: 2.0,  max: 8.0,  color: '#4dabf7', decimals: 2 },
  temperature: { label: 'Температура',    unit: '°C',   min: 36.5, max: 37.2, color: '#ffd43b', decimals: 2 },
};

const MAX_BUFFER = 2000;  // raw points kept in memory per metric
const MAX_RENDER = 240;   // points drawn after downsampling

// ---------------------------------------------------------------------------
// State management (buffering metrics for rendering)
// ---------------------------------------------------------------------------

const state = {
  token: localStorage.getItem('token'),
  patients: [],
  selectedId: null,
  buffers: {},              // metric key -> MetricBuffer
  unsubscribe: null,        // active metrics SSE cancel fn
  incidentUnsubscribe: null,// active incidents SSE cancel fn
  incidentMap: new Map(),   // incidentId -> incident object
};

class MetricBuffer {
  constructor() { this.points = []; }

  push(t, v) {
    this.points.push({ t, v });
    if (this.points.length > MAX_BUFFER) {
      this.points.splice(0, this.points.length - MAX_BUFFER);
    }
  }

  // Min/max bucket downsampling: preserves spikes that plain striding would lose
  downsampled() {
    const pts = this.points;
    if (pts.length <= MAX_RENDER) return pts;
    const bucketSize = Math.ceil(pts.length / (MAX_RENDER / 2));
    const out = [];
    for (let i = 0; i < pts.length; i += bucketSize) {
      const slice = pts.slice(i, i + bucketSize);
      let mn = slice[0], mx = slice[0];
      for (const p of slice) {
        if (p.v < mn.v) mn = p;
        if (p.v > mx.v) mx = p;
      }
      if (mn === mx) out.push(mn);
      else if (mn.t <= mx.t) out.push(mn, mx);
      else out.push(mx, mn);
    }
    return out;
  }

  last() { return this.points[this.points.length - 1] || null; }
}

// ---------------------------------------------------------------------------
// API helpers (RFC 6750 Bearer, RFC 7807 error parsing)
// ---------------------------------------------------------------------------

async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    ...opts,
    headers: {
      'Content-Type': 'application/json',
      ...(state.token ? { Authorization: 'Bearer ' + state.token } : {}),
      ...(opts.headers || {}),
    },
  });
  if (res.status === 401 && state.token) {
    logout();
    throw new Error('Сессия истекла, войдите снова');
  }
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const problem = await res.json(); // RFC 7807 Problem Details
      detail = problem.detail || problem.title || detail;
    } catch (_) { /* non-JSON body */ }
    throw new Error(detail);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---------------------------------------------------------------------------
// SSE stream (Observer pattern). EventSource can't send the Authorization
// header, so the text/event-stream is consumed via fetch + ReadableStream.
// ---------------------------------------------------------------------------

function subscribeStream(patientId, onMetric, onStatus) {
  const ctrl = new AbortController();
  let stopped = false;

  (async () => {
    while (!stopped) {
      try {
        const res = await fetch(`${API}/patients/${patientId}/stream`, {
          headers: {
            Authorization: 'Bearer ' + state.token,
            Accept: 'text/event-stream',
          },
          signal: ctrl.signal,
        });
        if (!res.ok || !res.body) throw new Error('stream HTTP ' + res.status);
        onStatus(true);

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buf = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += decoder.decode(value, { stream: true });

          const events = buf.split('\n\n');
          buf = events.pop(); // keep incomplete tail
          for (const evt of events) {
            let type = 'message', data = '';
            for (const line of evt.split('\n')) {
              if (line.startsWith('event:')) type = line.slice(6).trim();
              else if (line.startsWith('data:')) data += line.slice(5).trim();
            }
            if (type === 'metric' && data) {
              try { onMetric(JSON.parse(data)); } catch (_) { /* skip bad frame */ }
            }
          }
        }
      } catch (e) {
        if (e.name === 'AbortError') return;
      }
      onStatus(false);
      if (!stopped) await new Promise(r => setTimeout(r, 2000)); // reconnect
    }
  })();

  return () => { stopped = true; ctrl.abort(); };
}

function subscribeIncidentStream(patientId, onIncident) {
  const ctrl = new AbortController();
  let stopped = false;

  (async () => {
    while (!stopped) {
      try {
        const res = await fetch(`${API}/patients/${patientId}/incidents/stream`, {
          headers: { Authorization: 'Bearer ' + state.token, Accept: 'text/event-stream' },
          signal: ctrl.signal,
        });
        if (!res.ok || !res.body) throw new Error('incidents stream HTTP ' + res.status);

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buf = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += decoder.decode(value, { stream: true });
          const events = buf.split('\n\n');
          buf = events.pop();
          for (const evt of events) {
            let type = 'message', data = '';
            for (const line of evt.split('\n')) {
              if (line.startsWith('event:')) type = line.slice(6).trim();
              else if (line.startsWith('data:')) data += line.slice(5).trim();
            }
            if (type === 'incident' && data) {
              try { onIncident(JSON.parse(data)); } catch (_) {}
            }
          }
        }
      } catch (e) {
        if (e.name === 'AbortError') return;
      }
      if (!stopped) await new Promise(r => setTimeout(r, 2000));
    }
  })();

  return () => { stopped = true; ctrl.abort(); };
}

// ---------------------------------------------------------------------------
// Canvas chart rendering
// ---------------------------------------------------------------------------

function drawChart(canvas, cfg, buffer) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
    canvas.width = w * dpr;
    canvas.height = h * dpr;
  }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  const pts = buffer.downsampled();
  const padL = 44, padR = 8, padT = 8, padB = 18;
  const plotW = w - padL - padR, plotH = h - padT - padB;

  // Y range: data extent merged with the normal band, plus headroom
  let lo = cfg.min, hi = cfg.max;
  for (const p of pts) { if (p.v < lo) lo = p.v; if (p.v > hi) hi = p.v; }
  const pad = (hi - lo) * 0.15 || 1;
  lo -= pad; hi += pad;

  const y = v => padT + plotH - ((v - lo) / (hi - lo)) * plotH;

  // Normal range band
  ctx.fillStyle = 'rgba(81, 207, 102, 0.08)';
  ctx.fillRect(padL, y(cfg.max), plotW, y(cfg.min) - y(cfg.max));
  ctx.strokeStyle = 'rgba(81, 207, 102, 0.35)';
  ctx.setLineDash([4, 4]);
  for (const bound of [cfg.min, cfg.max]) {
    ctx.beginPath();
    ctx.moveTo(padL, y(bound));
    ctx.lineTo(w - padR, y(bound));
    ctx.stroke();
  }
  ctx.setLineDash([]);

  // Y axis labels
  ctx.fillStyle = '#8b95a1';
  ctx.font = '11px sans-serif';
  ctx.textAlign = 'right';
  for (let i = 0; i <= 4; i++) {
    const v = lo + (hi - lo) * (i / 4);
    ctx.fillText(v.toFixed(1), padL - 6, y(v) + 4);
  }

  if (pts.length < 2) return;

  // Time-proportional X mapping over the visible window
  const t0 = pts[0].t, t1 = pts[pts.length - 1].t;
  const span = Math.max(t1 - t0, 1);
  const x = t => padL + ((t - t0) / span) * plotW;

  // Series line
  ctx.strokeStyle = cfg.color;
  ctx.lineWidth = 1.6;
  ctx.lineJoin = 'round';
  ctx.beginPath();
  pts.forEach((p, i) => i === 0 ? ctx.moveTo(x(p.t), y(p.v)) : ctx.lineTo(x(p.t), y(p.v)));
  ctx.stroke();

  // Latest point marker
  const lastPt = pts[pts.length - 1];
  const critical = lastPt.v < cfg.min || lastPt.v > cfg.max;
  ctx.fillStyle = critical ? '#ff6b6b' : cfg.color;
  ctx.beginPath();
  ctx.arc(x(lastPt.t), y(lastPt.v), 3.5, 0, Math.PI * 2);
  ctx.fill();

  // Time axis: window length
  ctx.fillStyle = '#8b95a1';
  ctx.textAlign = 'left';
  ctx.fillText('-' + Math.round(span / 1000) + ' c', padL, h - 4);
  ctx.textAlign = 'right';
  ctx.fillText('сейчас', w - padR, h - 4);
}

// ---------------------------------------------------------------------------
// DOM
// ---------------------------------------------------------------------------

const $ = id => document.getElementById(id);

function show(view) {
  $('login-view').hidden = view !== 'login';
  $('app-view').hidden = view !== 'app';
}

function logout() {
  state.token = null;
  localStorage.removeItem('token');
  closeStream();
  state.selectedId = null;
  show('login');
}

function closeStream() {
  if (state.unsubscribe) { state.unsubscribe(); state.unsubscribe = null; }
  if (state.incidentUnsubscribe) { state.incidentUnsubscribe(); state.incidentUnsubscribe = null; }
}

// --- incidents ---

function formatIncidentDuration(startedAt, resolvedAt) {
  const end = resolvedAt ? new Date(resolvedAt) : new Date();
  const s = Math.round((end - new Date(startedAt)) / 1000);
  if (s < 60) return s + ' с';
  return Math.floor(s / 60) + ' мин ' + (s % 60) + ' с';
}

function renderIncidents() {
  const body = $('incidents-body');
  const badge = $('active-incident-count');
  const incidents = [...state.incidentMap.values()]
    .sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));

  const activeCount = incidents.filter(i => !i.resolvedAt).length;
  badge.hidden = activeCount === 0;
  badge.textContent = activeCount;

  if (!incidents.length) {
    body.innerHTML = '<p class="muted incidents-empty">Инцидентов не зафиксировано</p>';
    return;
  }

  const rows = incidents.map(i => {
    const active = !i.resolvedAt;
    const cfg = METRICS[i.metric];
    const label = cfg ? cfg.label : i.metric;
    const started = new Date(i.startedAt).toLocaleTimeString('ru-RU');
    const ended = active
      ? '<span class="badge-active">● активен</span>'
      : new Date(i.resolvedAt).toLocaleTimeString('ru-RU');
    const duration = formatIncidentDuration(i.startedAt, i.resolvedAt);
    const peak = i.maxDeviationValue != null
      ? i.maxDeviationValue.toFixed(cfg?.decimals ?? 1) + ' ' + (cfg?.unit ?? '')
      : '—';
    return `<tr class="${active ? 'incident-active-row' : ''}">
      <td>${label}</td><td>${started}</td><td>${ended}</td>
      <td>${duration}</td><td>${peak}</td>
    </tr>`;
  }).join('');

  body.innerHTML = `<table class="incidents-table">
    <thead><tr>
      <th>Показатель</th><th>Начало</th><th>Конец</th><th>Длительность</th><th>Пиковое значение</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

async function loadIncidents(id) {
  state.incidentMap = new Map();
  try {
    const incidents = await api(`/patients/${id}/incidents`);
    for (const inc of incidents || []) state.incidentMap.set(inc.id, inc);
  } catch (_) {}
  renderIncidents();
}

function onIncidentEvent(incident) {
  state.incidentMap.set(incident.id, incident);
  renderIncidents();
  renderPatientList(); // refresh sidebar badge
}

// --- patients ---

async function refreshPatients() {
  state.patients = await api('/patients');
  renderPatientList();
  updateActionButtons();
}

function updateActionButtons() {
  const startBtn = $('start-btn');
  const stopBtn = $('stop-btn');
  const p = state.patients.find(x => x.id === state.selectedId);
  if (!p) {
    startBtn.disabled = stopBtn.disabled = true;
    return;
  }
  startBtn.disabled = p.monitoringActive;
  stopBtn.disabled = !p.monitoringActive;
}

function renderPatientList() {
  const ul = $('patient-list');
  ul.innerHTML = '';
  for (const p of state.patients) {
    const li = document.createElement('li');
    li.className = p.id === state.selectedId ? 'selected' : '';

    const name = document.createElement('span');
    name.textContent = `${p.firstName} ${p.lastName}`;

    const badge = document.createElement('span');
    badge.className = 'badge' + (p.monitoringActive ? ' active' : '');
    badge.textContent = p.monitoringActive ? 'наблюдение' : 'пауза';

    li.append(name, badge);

    if (p.id === state.selectedId) {
      const activeCount = [...state.incidentMap.values()].filter(i => !i.resolvedAt).length;
      if (activeCount > 0) {
        const incBadge = document.createElement('span');
        incBadge.className = 'incident-badge';
        incBadge.textContent = activeCount;
        li.appendChild(incBadge);
      }
    }
    li.onclick = () => selectPatient(p.id);
    ul.appendChild(li);
  }
}

async function selectPatient(id) {
  if (state.selectedId === id) return;
  state.selectedId = id;
  closeStream();
  state.incidentMap = new Map();

  const p = state.patients.find(x => x.id === id);
  $('placeholder').hidden = true;
  $('patient-panel').hidden = false;
  $('patient-title').textContent = `${p.firstName} ${p.lastName}`;
  renderPatientList();
  updateActionButtons();
  buildCharts();
  loadStatistics();

  // Load history and incidents in parallel before going live
  await Promise.all([loadHistory(id), loadIncidents(id)]);
  if (state.selectedId !== id) return; // user switched patients while loading

  state.unsubscribe = subscribeStream(id, onMetricFrame, live => {
    const el = $('stream-status');
    el.textContent = live ? '● поток активен' : 'переподключение…';
    el.className = 'status' + (live ? ' live' : ' muted');
  });

  state.incidentUnsubscribe = subscribeIncidentStream(id, onIncidentEvent);
}

// --- measurement history (last N stored points per metric) ---

async function loadHistory(id) {
  let history;
  try {
    history = await api(`/patients/${id}/measurements?limit=100`);
  } catch (_) {
    return; // history is best-effort; live stream still works without it
  }
  if (state.selectedId !== id) return;

  for (const frame of history || []) {
    const buffer = state.buffers[frame.n];
    if (!buffer || frame.v == null) continue;
    buffer.push(frame.t ? Date.parse(frame.t) : Date.now(), frame.v);
  }

  for (const [key, cfg] of Object.entries(METRICS)) {
    const buffer = state.buffers[key];
    const last = buffer.last();
    if (last) updateValueLabel(key, cfg, last.v);
    drawChart($(`canvas-${key}`), cfg, buffer);
    renderMeasurements(key, cfg);
  }
}

// --- statistics (mean, variance, quartiles over a chosen interval) ---

async function loadStatistics() {
  if (!state.selectedId) return;
  const minutes = Number($('stats-interval').value);
  const to = new Date();
  const from = new Date(to.getTime() - minutes * 60_000);
  const qs = `from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`;

  const body = $('stats-body');
  try {
    const stats = await api(`/patients/${state.selectedId}/statistics?${qs}`);
    renderStatistics(stats || []);
  } catch (err) {
    body.innerHTML = '';
    const p = document.createElement('p');
    p.className = 'error';
    p.textContent = 'Ошибка загрузки статистики: ' + err.message;
    body.appendChild(p);
  }
}

function renderStatistics(stats) {
  const body = $('stats-body');
  const byMetric = Object.fromEntries(stats.map(s => [s.metric, s]));
  const fmt = (v, d) => (v == null ? '<span class="na">—</span>' : v.toFixed(d));

  const rows = Object.entries(METRICS).map(([key, cfg]) => {
    const s = byMetric[key];
    const d = cfg.decimals;
    return `<tr>
      <td>${cfg.label}, ${cfg.unit}</td>
      <td>${s ? s.count : '<span class="na">—</span>'}</td>
      <td>${fmt(s?.mean, d)}</td>
      <td>${fmt(s?.variance, d + 1)}</td>
      <td>${fmt(s?.q1, d)}</td>
      <td>${fmt(s?.median, d)}</td>
      <td>${fmt(s?.q3, d)}</td>
      <td>${fmt(s?.min, d)}</td>
      <td>${fmt(s?.max, d)}</td>
    </tr>`;
  }).join('');

  body.innerHTML = `<table class="stats-table">
    <thead><tr>
      <th>Показатель</th><th>N</th><th>Среднее</th><th>Дисперсия</th>
      <th>Q1</th><th>Медиана</th><th>Q3</th><th>Мин</th><th>Макс</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// --- charts ---

function renderMeasurements(key, cfg) {
  const panel = $(`meas-${key}`);
  if (!panel || panel.hidden) return;
  const pts = [...(state.buffers[key]?.points || [])].reverse().slice(0, 30);
  if (!pts.length) {
    panel.innerHTML = '<p class="muted meas-empty">Нет данных</p>';
    return;
  }
  const rows = pts.map(p => {
    const d = new Date(p.t);
    const time = d.toLocaleTimeString('ru-RU');
    const critical = p.v < cfg.min || p.v > cfg.max;
    return `<tr class="${critical ? 'critical-row' : ''}">
      <td class="meas-time">${time}</td>
      <td class="meas-val${critical ? ' critical' : ''}">${p.v.toFixed(cfg.decimals)}</td>
      <td class="meas-unit">${cfg.unit}</td>
    </tr>`;
  }).join('');
  panel.innerHTML = `<table class="meas-table"><tbody>${rows}</tbody></table>`;
}

function buildCharts() {
  state.buffers = {};
  const wrap = $('charts');
  wrap.innerHTML = '';

  for (const [key, cfg] of Object.entries(METRICS)) {
    state.buffers[key] = new MetricBuffer();

    const card = document.createElement('div');
    card.className = 'chart-card';
    card.innerHTML = `
      <div class="chart-top">
        <h3>${cfg.label}<span class="range">норма ${cfg.min}–${cfg.max} ${cfg.unit}</span></h3>
        <div class="chart-top-right">
          <div class="value" id="value-${key}">—</div>
          <button class="ghost meas-toggle" id="meas-btn-${key}">Измерения ▼</button>
        </div>
      </div>
      <canvas id="canvas-${key}"></canvas>
      <div id="meas-${key}" class="meas-panel" hidden></div>`;
    wrap.appendChild(card);

    card.querySelector(`#meas-btn-${key}`).addEventListener('click', () => {
      const panel = $(`meas-${key}`);
      const btn = $(`meas-btn-${key}`);
      panel.hidden = !panel.hidden;
      btn.textContent = panel.hidden ? 'Измерения ▼' : 'Измерения ▲';
      btn.classList.toggle('active', !panel.hidden);
      if (!panel.hidden) renderMeasurements(key, cfg);
    });

    drawChart($(`canvas-${key}`), cfg, state.buffers[key]);
  }
}

function updateValueLabel(key, cfg, v) {
  const valueEl = $(`value-${key}`);
  const critical = v < cfg.min || v > cfg.max;
  valueEl.className = 'value' + (critical ? ' critical' : '');
  valueEl.innerHTML = `${v.toFixed(cfg.decimals)}<span class="unit">${cfg.unit}</span>`;
}

// SenML frame (RFC 8428): {"n":"heart_rate","u":"bpm","v":75.2,"t":"...Z"}
function onMetricFrame(senml) {
  const cfg = METRICS[senml.n];
  const buffer = state.buffers[senml.n];
  if (!cfg || !buffer) return;

  const t = senml.t ? Date.parse(senml.t) : Date.now();
  buffer.push(t, senml.v);
  updateValueLabel(senml.n, cfg, senml.v);
  drawChart($(`canvas-${senml.n}`), cfg, buffer);
  renderMeasurements(senml.n, cfg);
}

// ---------------------------------------------------------------------------
// Event wiring
// ---------------------------------------------------------------------------

$('login-form').addEventListener('submit', async e => {
  e.preventDefault();
  const errEl = $('login-error');
  errEl.hidden = true;
  $('login-btn').disabled = true;
  try {
    const resp = await api('/auth/token', {
      method: 'POST',
      body: JSON.stringify({
        username: $('login-username').value,
        password: $('login-password').value,
      }),
    });
    state.token = resp.token;
    localStorage.setItem('token', state.token);
    show('app');
    await refreshPatients();
  } catch (err) {
    errEl.textContent = err.message;
    errEl.hidden = false;
  } finally {
    $('login-btn').disabled = false;
  }
});

$('logout-btn').addEventListener('click', logout);

$('add-patient-form').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    await api('/patients', {
      method: 'POST',
      body: JSON.stringify({
        firstName: $('new-first-name').value,
        lastName: $('new-last-name').value,
      }),
    });
    e.target.reset();
    await refreshPatients();
  } catch (err) {
    alert(err.message);
  }
});

async function toggleMonitoring(action) {
  if (!state.selectedId) return;
  // Lock both buttons for the duration of the request to rule out double clicks
  $('start-btn').disabled = true;
  $('stop-btn').disabled = true;
  try {
    await api(`/patients/${state.selectedId}/monitoring/${action}`, { method: 'POST' });
    await refreshPatients();
  } catch (err) {
    alert(err.message);
    updateActionButtons(); // restore buttons from actual state on failure
  }
}

$('start-btn').addEventListener('click', () => toggleMonitoring('start'));
$('stop-btn').addEventListener('click', () => toggleMonitoring('stop'));

$('incidents-refresh').addEventListener('click', () => {
  if (state.selectedId) loadIncidents(state.selectedId);
});

$('stats-refresh').addEventListener('click', loadStatistics);
$('stats-interval').addEventListener('change', loadStatistics);

window.addEventListener('resize', () => {
  for (const [key, cfg] of Object.entries(METRICS)) {
    const canvas = $(`canvas-${key}`);
    if (canvas && state.buffers[key]) drawChart(canvas, cfg, state.buffers[key]);
  }
});

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

(async function init() {
  if (!state.token) { show('login'); return; }
  try {
    await refreshPatients();
    show('app');
  } catch (_) {
    logout();
  }
})();
