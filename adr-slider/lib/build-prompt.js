import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { AXES, TOTAL } from './axes.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');

const template = readFileSync(join(ROOT, 'prompt', 'template.md'), 'utf8');

export function loadTopic(topicId = 'product-master') {
  return readFileSync(join(ROOT, 'topics', `${topicId}.md`), 'utf8').trim();
}

// スライダー値を降順に並べ、最大値を支配軸として明示した一覧テキストを作る。
export function formatSliders(values) {
  const sorted = AXES.map((a) => ({ ...a, v: values[a.key] ?? 0 })).sort((x, y) => y.v - x.v);
  const top = sorted[0]?.v ?? 0;
  return sorted
    .map((a) => `- ${a.label}: ${a.v}${a.v === top && top > 0 ? '   ← 支配軸（最重視）' : ''}`)
    .join('\n');
}

export function buildPrompt(values, topicId = 'product-master') {
  const sliders = formatSliders(values);
  const topic = loadTopic(topicId);
  return template
    .replaceAll('{{SLIDERS}}', sliders)
    .replaceAll('{{TOPIC}}', topic);
}

// 端末の冒頭に出す、価値観の一行サマリ（誰が見ても何を入力したか分かるように）。
export function sliderSummaryLine(values) {
  const parts = AXES.map((a) => `${a.short}${values[a.key] ?? 0}`);
  return parts.join(' ');
}

export { TOTAL };
