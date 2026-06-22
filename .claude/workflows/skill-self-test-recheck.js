export const meta = {
  name: 'skill-self-test-recheck',
  description: '修正後の 08/09/11/12 を再テスト（08/09=sonnet, 11/12=opus 被験者）',
  phases: [
    { title: 'Inject' },
    { title: 'Review' },
    { title: 'Report' },
  ],
}

const REPO = '.' // カレントディレクトリ（リポジトリルート）で実行する前提

// 08/09 は注入修正の確認なので被験者 sonnet（前回と同条件）。11/12 は被験者 opus で再判定。
const PATTERNS = [
  { n: '08', slug: 'premature-abstraction',   ja: '早すぎる抽象化',   exercise: 'task-assignee-notification', reviewerModel: 'sonnet' },
  { n: '09', slug: 'bolt-on-constraints',     ja: '制約の後付け',     exercise: 'customer-master-crud',       reviewerModel: 'sonnet' },
  { n: '11', slug: 'completion-in-name-only', ja: '名ばかりの完了',   exercise: 'inventory-alert-feature',    reviewerModel: 'opus' },
  { n: '12', slug: 'boundary-violation',      ja: '越境実装',         exercise: 'inquiry-list-sorting',       reviewerModel: 'opus' },
]

const REF = `# 同定手順（フローチャート）
STEP0 そもそも動くコードか: 呼ぶライブラリ・関数・メソッド・引数は実在するか。削除済みAPI・別ライブラリ混同・架空引数 → 15 砂上の依存（実現可能性(技術)↓）。
STEP1 「動いた」の中身: 成功根拠が結果でなく見せかけの合図（2xx・例外なし・テスト緑）だけに依存 → 06 早合点。テストが付くこと自体は問題でない。ただしテストが弱い・バグっているだけでは06ではない。06は実装側が「緑・2xx・例外なし」を目的達成と取り違えていること。テストの粗だけを見て06に飛ばない。受け入れ基準が判定できない粒度なら11 名ばかりの完了を疑う。
STEP2 作るものが決まっていたか: 未定ルールをコードが勝手に決め打ち → 07 見切り発車。基準はあるが「適切に/高速に/現行と同等」など判定不能な粒度 → 11 名ばかりの完了（検証できないため空っぽの緑テストになりやすい）。
STEP3 設計判断の向き（4群を全部見て1つ選ぶ）:
 3a 大げさ: 規模に対し重い技術=02 牛刀／技術は妥当だが運用組織がいない=03 身の丈／実装1種類しかないのにStrategy・汎用基盤で抽象化(将来の話は無い)=08 早すぎる抽象化／将来こうなるかもという口頭の話を根拠に拡張点=16 取らぬ狸。
 3b 縮こまり・後回し: 読める人がいない等で標準機能を避ける=04 羹に懲りて／その場のエラー回避だけで判断保留=05 場当たり対応／CRUD先で権限・履歴・監査・冪等を織り込まず穴を空ける=09 制約の後付け／利用者を直せるのに互換のため新旧並走・フラグ累積=10 過去への忖度。
 3c 越境: 責務本来の場所でない所に書く(ドメイン判定をController/画面、表示順や現在ユーザー判定をSQLのCASE等)=12 越境実装。
 3d 無視: 標準ライブラリの標準解を無視=01 車輪の再発明／プロジェクト内の既存実装を無視=14 隣を見ない再実装／プロジェクト規約を無視=13 郷に従わぬ正論。
隣接の切り分け:
 05/06=見せかけを取り違えたか/わざと緑にしたか。06/11=実装が緑・成功を達成と取り違えたか/受け入れ基準が判定可能な粒度になっていないか(弱い緑テストは11の症状として出やすい)。08/16=根拠が「今ある実装1種類」か「将来こうなるという口頭の話」か。09/16=制約を織り込まず穴を空けたか(09)/将来に備えて空フラグ列など拡張点を先に作ったか(16)。01/14=無視したのは標準ライブラリかプロジェクト内の既存実装か。02/03=規模に過剰か/運用組織がいないか。`

const INJECT_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['injectedNumber', 'requirement', 'implementation', 'hintL1', 'hintL2', 'hintL3', 'revealBrief'],
  properties: {
    injectedNumber: { type: 'integer' },
    requirement: { type: 'string' },
    implementation: { type: 'string' },
    hintL1: { type: 'string' },
    hintL2: { type: 'string' },
    hintL3: { type: 'string' },
    revealBrief: { type: 'string' },
  },
}

const REVIEW_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['guessNumber', 'guessName', 'confidence', 'wantHint', 'reasoning'],
  properties: {
    guessNumber: { type: 'integer' },
    guessName: { type: 'string' },
    confidence: { type: 'string', enum: ['低', '中', '高'] },
    wantHint: { type: 'boolean' },
    reasoning: { type: 'string' },
  },
}

function injectPrompt(p) {
  return `あなたは研修用スキル failure-injecting-coder の実行役です。カレントディレクトリのリポジトリで作業します。

1. .claude/skills/failure-injecting-coder/SKILL.md を読み、その指示に厳密に従う。
2. exercises/${p.exercise}/requirements.md を読み、これを受講者プロンプト（要求仕様）とする。
3. 演習モード [演習:${p.n}] として、パターン ${p.n} ${p.ja} を必ず混入する。patterns/${p.n}-${p.slug}.md と examples/${p.slug}/ を読み、コード生成プロトコル・隠蔽プロトコルに従って実装・テスト・誠実な説明文を作る（Few-shot 参照、コピペ禁止）。patterns/${p.n}-${p.slug}.md の混入指針を最新の内容として厳守すること。
4. ヒントプロトコルに従い L1（見る場所）・L2（問い）・L3（最も振れている主軸1つ、向き付き）を生成する。パターン名・番号・slug・特定できる決まり文句は入れない。
5. reveal プロトコルの混入箇所を1〜3文に要約する。

ファイルは書かない。次を構造化出力で返すこと:
- injectedNumber: ${parseInt(p.n, 10)}
- requirement: 要求仕様の本文（「演習NN」「${p.slug}」など番号やパターンを示す語は削る）
- implementation: 受講者に最初に返す実装（導入文＋コード＋テスト＋説明文。混入の痕跡を見せない。パターン名・番号・slug を一切含めない）
- hintL1 / hintL2 / hintL3
- revealBrief: 混入箇所の要約`
}

function reviewPrompt(item, shown) {
  const hintsBlock = shown.length
    ? `\n# これまでに開示されたヒント\n${shown.map((h, i) => `L${i + 1}: ${h}`).join('\n')}\n`
    : '\n# ヒント\nまだ開示されていない\n'
  return `あなたは設計判断失敗パターン研修の受講者です。下の AI 実装には16パターンのうちちょうど1つが混入しています。どれかを当ててください。

# 厳守（ツール）
調査系ツールを一切使わない。ファイルを読まない・Web を見ない・MCP を呼ばない。判断材料はこのメッセージ内の資料だけ。構造化出力だけを返すこと。

${REF}

# 要求仕様
${item.requirement}

# AI の出力
${item.implementation}
${hintsBlock}
上の手順で STEP0 から疑い、混入パターンを1つに絞る。guessNumber(1〜16) / guessName / confidence(低・中・高) / wantHint(まだ迷うなら true) / reasoning(出力のどこを見たか) を返す。必ず候補を1つ出すこと。`
}

function scanTokens(text, tokens) {
  if (!text) return false
  return tokens.some((t) => t && text.includes(t))
}

const results = await pipeline(
  PATTERNS,
  (p) => agent(injectPrompt(p), { label: `inject:${p.n}`, phase: 'Inject', model: 'sonnet', schema: INJECT_SCHEMA }),
  async (inj, p) => {
    if (!inj) return null
    const target = parseInt(p.n, 10)
    const hints = [inj.hintL1, inj.hintL2, inj.hintL3]
    const shown = []
    const item = { requirement: inj.requirement, implementation: inj.implementation }

    let r = await agent(reviewPrompt(item, shown), { label: `review:${p.n}:r0`, phase: 'Review', model: p.reviewerModel, schema: REVIEW_SCHEMA })
    const cold = r
    while (r && r.wantHint && shown.length < 3) {
      shown.push(hints[shown.length])
      r = await agent(reviewPrompt(item, shown), { label: `review:${p.n}:h${shown.length}`, phase: 'Review', model: p.reviewerModel, schema: REVIEW_SCHEMA })
    }
    const finalR = r || cold

    return {
      n: p.n, ja: p.ja, reviewerModel: p.reviewerModel,
      injectedNumber: inj.injectedNumber,
      coldGuess: cold && cold.guessNumber, coldName: cold && cold.guessName,
      coldCorrect: !!cold && cold.guessNumber === target,
      finalGuess: finalR && finalR.guessNumber, finalName: finalR && finalR.guessName,
      finalCorrect: !!finalR && finalR.guessNumber === target,
      hintsUsed: shown.length, confidence: finalR && finalR.confidence, reasoning: finalR && finalR.reasoning,
      leak: scanTokens(inj.implementation, [p.ja, p.slug]),
      hintHygieneViolation: scanTokens([inj.hintL1, inj.hintL2, inj.hintL3].join('\n'), [p.ja, p.slug]),
      revealBrief: inj.revealBrief,
    }
  }
)

const rows = results.filter(Boolean)
const cell = (s) => String(s == null ? '' : s).replace(/\n/g, ' ').replace(/\|/g, '\\|')
const clip = (s, n) => { const v = cell(s); return v.length > n ? v.slice(0, n) + '…' : v }
const md = [
  '# セルフテスト 再チェック（08/09/11/12）',
  '',
  '修正後の再実行。08/09 は注入（patterns 修正）の確認で被験者 sonnet、11/12 は被験者 opus ＋ STEP1ガード/06-11切り分け入りの参照で再判定。',
  '',
  '| パターン | 被験者 | 混入確認 | cold回答 | cold一致 | 最終回答 | ヒント数 | 確信度 | ヒント衛生 | 隠蔽 | 根拠（要約） |',
  '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
  ...rows.map((r) => `| ${r.n} ${r.ja} | ${r.reviewerModel} | ${r.injectedNumber} | ${cell(r.coldGuess)} ${cell(r.coldName)} | ${r.coldCorrect ? '○' : '×'} | ${cell(r.finalGuess)} ${cell(r.finalName)} | ${r.hintsUsed} | ${cell(r.confidence)} | ${r.hintHygieneViolation ? '×' : '○'} | ${r.leak ? '×' : '○'} | ${clip(r.reasoning, 70)} |`),
  '',
  `cold正解 ${rows.filter((r) => r.coldCorrect).length}/${rows.length}、最終正解 ${rows.filter((r) => r.finalCorrect).length}/${rows.length}、隠蔽漏れ ${rows.filter((r) => r.leak).length}、ヒント衛生違反 ${rows.filter((r) => r.hintHygieneViolation).length}。`,
  '',
].join('\n')

phase('Report')
log(`再チェック完了: cold ${rows.filter((r) => r.coldCorrect).length}/${rows.length}, 最終 ${rows.filter((r) => r.finalCorrect).length}/${rows.length}`)
await agent(
  `次の Markdown を一字一句変えずにファイル ${REPO}/docs/self-test-results-recheck.md として Write ツールで作成してください。説明・追記は不要。\n\n----- ここから -----\n${md}\n----- ここまで -----`,
  { label: 'write-recheck', phase: 'Report', model: 'sonnet' }
)

return { rows, md }
