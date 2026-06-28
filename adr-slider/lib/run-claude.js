import { spawn } from 'node:child_process';

// `claude -p` を新規プロセスで起動し、回答テキストをトークン単位でストリームする。
// 各回が独立プロセス＝独立推論なので、前回の結論に引きずられない。
//
// onText(text): 回答の text_delta が来るたびに呼ばれる
// onDone({ ok, code }): 終了時に1回呼ばれる
// 戻り値: child プロセス（中断したいとき kill する）
export function runClaude(prompt, { model = 'sonnet' } = {}, { onText, onStatus, onDone }) {
  const args = [
    '-p',
    prompt,
    '--output-format',
    'stream-json',
    '--include-partial-messages',
    '--verbose',
  ];
  if (model) args.push('--model', model);

  const child = spawn('claude', args, {
    cwd: process.cwd(),
    env: process.env,
  });

  let buf = '';
  child.stdout.setEncoding('utf8');
  child.stdout.on('data', (chunk) => {
    buf += chunk;
    let nl;
    while ((nl = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, nl);
      buf = buf.slice(nl + 1);
      if (!line.trim()) continue;
      handleLine(line, { onText, onStatus });
    }
  });

  let stderr = '';
  child.stderr.setEncoding('utf8');
  child.stderr.on('data', (c) => (stderr += c));

  child.on('close', (code) => {
    if (code !== 0 && stderr) onText?.(`\n\n**[claude エラー]** ${stderr.trim()}\n`);
    onDone?.({ ok: code === 0, code });
  });
  child.on('error', (err) => {
    onText?.(`\n\n**[起動エラー]** ${err.message}\n`);
    onDone?.({ ok: false, code: -1 });
  });

  return child;
}

function handleLine(line, { onText, onStatus }) {
  let ev;
  try {
    ev = JSON.parse(line);
  } catch {
    return; // NDJSON 以外の行は無視
  }
  if (ev.type === 'stream_event') {
    const e = ev.event;
    if (e?.type === 'content_block_delta' && e.delta?.type === 'text_delta') {
      onText?.(e.delta.text);
    }
  } else if (ev.type === 'system' && ev.subtype === 'status') {
    onStatus?.(ev.status);
  }
}
