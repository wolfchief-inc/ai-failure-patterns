import { marked } from '/vendor/marked.esm.js';

marked.setOptions({ breaks: true, gfm: true });

const state = { axes: [], keys: [], total: 400, values: {}, presets: [], topics: [], topicId: null, busy: false };

const els = {
  sliders: document.getElementById('sliders'),
  presets: document.getElementById('presets'),
  topic: document.getElementById('topic'),
  regenerate: document.getElementById('regenerate'),
  status: document.getElementById('status'),
  total: document.getElementById('total'),
  output: document.getElementById('output'),
  badge: document.getElementById('dominant-badge'),
};

// ---- 出力（ストリーミング Markdown） ----
let acc = '';
let raf = 0;
function renderOutput() {
  if (raf) return;
  raf = requestAnimationFrame(() => {
    raf = 0;
    els.output.innerHTML = marked.parse(acc);
    els.output.scrollTop = els.output.scrollHeight;
  });
}
function resetOutput() {
  acc = '';
  els.output.innerHTML = '<div class="placeholder">生成中…</div>';
  els.badge.textContent = '';
}
function updateBadge() {
  const pick = (acc.match(/採用案[^A-Z]*([A-F])/) || [])[1];
  const dom = (acc.match(/支配軸[^：:]*[：:]\s*\*{0,2}([^\n*（(]+)/) || [])[1];
  els.badge.textContent = pick ? `採用 ${pick}${dom ? ` ・ 支配軸 ${dom.trim()}` : ''}` : '';
}

// ---- WebSocket ----
let ws;
function connect() {
  ws = new WebSocket(`ws://${location.host}`);
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.type === 'reset') resetOutput();
    else if (msg.type === 'out') {
      acc += msg.text;
      renderOutput();
      updateBadge();
    } else if (msg.type === 'done') {
      renderOutput();
      updateBadge();
      setBusy(false);
    }
  };
  ws.onclose = () => setTimeout(connect, 1000);
}
connect();

// ---- ゼロサム再配分 ----
function applyChange(changedKey, desiredRaw) {
  const desired = clampInt(desiredRaw, 0, 100);
  const others = state.keys.filter((k) => k !== changedKey);
  const target = state.total - desired;
  const cur = others.map((k) => state.values[k]);
  const curSum = cur.reduce((a, b) => a + b, 0);
  let flo =
    curSum <= 0
      ? others.map(() => target / others.length)
      : cur.map((v) => (v * target) / curSum);
  flo = waterfill(flo, target, 0, 100);
  const ints = largestRemainder(flo, target);
  state.values[changedKey] = desired;
  others.forEach((k, i) => (state.values[k] = ints[i]));
  renderValues();
}

function waterfill(arr, target, lo, hi) {
  const a = arr.slice();
  for (let iter = 0; iter < 12; iter++) {
    a.forEach((v, i) => (a[i] = Math.min(hi, Math.max(lo, v))));
    const sum = a.reduce((x, y) => x + y, 0);
    const diff = target - sum;
    if (Math.abs(diff) < 1e-6) break;
    const movable = a.map((v, i) => ({ i, v })).filter(({ v }) => (diff > 0 ? v < hi : v > lo));
    if (!movable.length) break;
    const base = movable.reduce((s, m) => s + (diff > 0 ? hi - m.v : m.v - lo), 0) || movable.length;
    movable.forEach((m) => {
      const room = diff > 0 ? hi - m.v : m.v - lo;
      a[m.i] += diff * (base === movable.length ? 1 / movable.length : room / base);
    });
  }
  return a;
}
function largestRemainder(arr, target) {
  const floor = arr.map((v) => Math.floor(v));
  let rem = target - floor.reduce((a, b) => a + b, 0);
  const order = arr.map((v, i) => ({ i, frac: v - Math.floor(v) })).sort((a, b) => b.frac - a.frac);
  for (let j = 0; j < order.length && rem > 0; j++, rem--) floor[order[j].i] += 1;
  return floor;
}
function clampInt(v, lo, hi) {
  return Math.max(lo, Math.min(hi, Math.round(Number(v) || 0)));
}

// ---- 描画 ----
function renderSliders() {
  els.sliders.innerHTML = '';
  for (const ax of state.axes) {
    const row = document.createElement('div');
    row.className = 'slider';
    row.dataset.key = ax.key;
    row.title = ax.hint; // ヒントはホバーのツールチップへ退避（場所を節約）
    row.innerHTML = `
      <div class="slider__top">
        <span class="slider__label">${ax.label}<span class="slider__tag">支配軸</span></span>
        <span class="slider__val">${state.values[ax.key]}</span>
      </div>
      <input type="range" min="0" max="100" value="${state.values[ax.key]}" />
    `;
    const input = row.querySelector('input');
    input.addEventListener('input', () => applyChange(ax.key, input.value));
    els.sliders.appendChild(row);
  }
  renderValues();
}
function renderValues() {
  const max = Math.max(...state.keys.map((k) => state.values[k]));
  const uniqueMax = state.keys.filter((k) => state.values[k] === max).length === 1;
  for (const row of els.sliders.children) {
    const k = row.dataset.key;
    const v = state.values[k];
    row.querySelector('.slider__val').textContent = v;
    const input = row.querySelector('input');
    if (Number(input.value) !== v) input.value = v;
    row.classList.toggle('is-dominant', uniqueMax && v === max && max > 0);
  }
}
function renderPresets() {
  els.presets.innerHTML = '';
  for (const p of state.presets) {
    const b = document.createElement('button');
    b.className = 'preset';
    b.title = p.note || '';
    b.innerHTML = p.pattern ? `${p.label} <small>· ${p.pattern}</small>` : p.label;
    b.addEventListener('click', () => {
      for (const k of state.keys) state.values[k] = clampInt(p.values[k], 0, 100);
      renderValues();
    });
    els.presets.appendChild(b);
  }
}
function renderTopics() {
  els.topic.innerHTML = '';
  for (const t of state.topics) {
    const o = document.createElement('option');
    o.value = t.id;
    o.textContent = t.title;
    els.topic.appendChild(o);
  }
  els.topic.value = state.topicId;
  els.topic.addEventListener('change', () => {
    state.topicId = els.topic.value;
    showTopic(); // お題を切り替えたら、その問題と代替案を右ペインに出す（生成前の状態）
  });
}

// 選択中のお題（問題＋代替案）を右ペインに表示する。再生成すると議論＋ADRに置き換わる。
function showTopic() {
  const t = state.topics.find((x) => x.id === state.topicId);
  els.badge.textContent = '';
  els.output.innerHTML = t
    ? `<div class="topic-view">${marked.parse(t.body)}</div>`
    : '<div class="placeholder">お題を選んでください。</div>';
  els.output.scrollTop = 0;
}

// ---- 再生成 ----
function setBusy(b) {
  state.busy = b;
  els.regenerate.disabled = b;
  els.status.textContent = b ? '生成中…（スライダーを変えて再生成すると採用案が変わります）' : '';
}
els.regenerate.addEventListener('click', () => {
  if (ws.readyState !== WebSocket.OPEN) return;
  setBusy(true);
  ws.send(JSON.stringify({ type: 'regenerate', values: state.values, topicId: state.topicId }));
});

// ---- 初期化 ----
(async () => {
  const cfg = await fetch('/api/config').then((r) => r.json());
  state.axes = cfg.axes;
  state.keys = cfg.axes.map((a) => a.key);
  state.total = cfg.total;
  state.values = { ...cfg.defaults };
  state.presets = cfg.presets;
  state.topics = cfg.topics;
  state.topicId = cfg.topicId;
  els.total.textContent = cfg.total;
  renderTopics();
  renderSliders();
  renderPresets();
  showTopic(); // 初期表示は選択中のお題
})();
