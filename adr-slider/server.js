import { createServer } from 'node:http';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, extname } from 'node:path';
import { WebSocketServer } from 'ws';
import { AXES, TOTAL, defaultSliders } from './lib/axes.js';
import { buildPrompt, sliderSummaryLine } from './lib/build-prompt.js';
import { runClaude } from './lib/run-claude.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 5178);
const MODEL = process.env.ADR_MODEL || undefined; // 未指定なら Claude Code 既定モデル
const TOPIC_ID = process.env.ADR_TOPIC || 'product-master';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
};

// 静的配信のホワイトリスト。public 配下と、node_modules のターミナル資産だけを公開する。
const VENDOR = {
  '/vendor/marked.esm.js': 'node_modules/marked/lib/marked.esm.js',
};

async function serveFile(res, relPath) {
  try {
    const buf = await readFile(join(__dirname, relPath));
    res.writeHead(200, { 'Content-Type': MIME[extname(relPath)] || 'application/octet-stream' });
    res.end(buf);
  } catch {
    res.writeHead(404).end('not found');
  }
}

async function loadPresets() {
  return JSON.parse(await readFile(join(__dirname, 'presets.json'), 'utf8'));
}

// topics/*.md を走査し、先頭見出し「# お題: …」をタイトルとして一覧化する。
async function listTopics() {
  const files = (await readdir(join(__dirname, 'topics'))).filter((f) => f.endsWith('.md'));
  const topics = [];
  for (const f of files) {
    const id = f.replace(/\.md$/, '');
    const text = await readFile(join(__dirname, 'topics', f), 'utf8');
    const m = text.match(/^#\s*(?:お題:\s*)?(.+)$/m);
    topics.push({ id, title: (m?.[1] || id).trim(), body: text.trim() });
  }
  return topics.sort((a, b) => (a.id === TOPIC_ID ? -1 : b.id === TOPIC_ID ? 1 : 0));
}

const server = createServer(async (req, res) => {
  const url = req.url.split('?')[0];

  if (url === '/' || url === '/index.html') return serveFile(res, 'public/index.html');
  if (url === '/app.js') return serveFile(res, 'public/app.js');
  if (url === '/styles.css') return serveFile(res, 'public/styles.css');
  if (VENDOR[url]) return serveFile(res, VENDOR[url]);

  if (url === '/api/config') {
    const [presets, topics] = await Promise.all([loadPresets(), listTopics()]);
    res.writeHead(200, { 'Content-Type': MIME['.json'] });
    res.end(
      JSON.stringify({
        axes: AXES,
        total: TOTAL,
        defaults: defaultSliders(),
        presets,
        topics,
        topicId: TOPIC_ID,
        model: MODEL || 'default',
      })
    );
    return;
  }

  res.writeHead(404).end('not found');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  let child = null;
  const send = (msg) => ws.readyState === ws.OPEN && ws.send(JSON.stringify(msg));

  ws.on('message', async (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (msg.type === 'regenerate') {
      if (child) child.kill('SIGTERM'); // 走行中なら止めて最新の価値観で取り直す
      const values = normalize(msg.values);
      const topicId = String(msg.topicId || TOPIC_ID).replace(/[^a-z0-9-]/gi, '');
      await persist(values, topicId);

      send({ type: 'reset' });
      send({ type: 'out', text: `> 価値観: ${sliderSummaryLine(values)}\n\n` });

      let prompt;
      try {
        prompt = buildPrompt(values, topicId);
      } catch {
        send({ type: 'out', text: `お題が見つかりません: ${topicId}\n` });
        send({ type: 'done', ok: false });
        return;
      }
      child = runClaude(
        prompt,
        { model: MODEL },
        {
          onText: (t) => send({ type: 'out', text: t }),
          onDone: ({ ok }) => {
            child = null;
            send({ type: 'done', ok });
          },
        }
      );
    } else if (msg.type === 'cancel') {
      if (child) child.kill('SIGTERM');
    }
  });

  ws.on('close', () => {
    if (child) child.kill('SIGTERM');
  });
});

function normalize(values) {
  const out = {};
  for (const a of AXES) out[a.key] = clampInt(values?.[a.key]);
  return out;
}
function clampInt(v) {
  const n = Math.round(Number(v) || 0);
  return Math.max(0, Math.min(100, n));
}

async function persist(values, topicId) {
  const dir = join(__dirname, 'state');
  await mkdir(dir, { recursive: true });
  await writeFile(
    join(dir, 'sliders.json'),
    JSON.stringify({ topicId, values }, null, 2)
  );
}

server.listen(PORT, () => {
  console.log(`adr-slider: http://localhost:${PORT}  (topic=${TOPIC_ID}, model=${MODEL || 'default'})`);
});
