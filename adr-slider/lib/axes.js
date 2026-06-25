// 設計判断で使う評価軸（出典: Scrapbox decision_quality.md / DQ の Values を8つに集約）。
// スライダーは合計一定（TOTAL）のゼロサム。1本を上げると他が下がる＝トレードオフ。
export const TOTAL = 400; // 8軸 × 既定50

export const AXES = [
  {
    key: 'purpose',
    label: '目的適合性',
    short: '目的',
    hint: 'この判断が本来の目的・ユーザー価値にどれだけ寄与するか（Value-Focused Thinking）。',
  },
  {
    key: 'constraint',
    label: '制約適合性',
    short: '制約',
    hint: '機能・非機能の要件や前提制約をどれだけ満たすか。',
  },
  {
    key: 'feasibility',
    label: '実現可能性',
    short: '実現',
    hint: '技術的に成立するか（技術）と、当該組織のスキル・体制で運用・保守できるか（組織）の二層。高いほど「無理なく作って運用できること」を重視。',
  },
  {
    key: 'quality',
    label: '品質影響',
    short: '品質',
    hint: '保守性・運用性・整合性・監査性などの -ility（ISO/IEC 25010）への影響。高いほど品質を犠牲にしない。',
  },
  {
    key: 'time',
    label: '時間効果',
    short: '時間',
    hint: '高いほど短期の素早さ・実装容易性を優先（今すぐ動くことを重視）。低いほど時間をかけてよい。長期の負債は品質・整合性の軸で見る。',
  },
  {
    key: 'risk',
    label: 'リスク・不確実性',
    short: 'リスク',
    hint: '高いほど不確実性・障害・事故を避けることを重視（堅く倒す）。',
  },
  {
    key: 'coherence',
    label: '整合性',
    short: '整合',
    hint: 'データ・アーキテクチャ・記録（ADR）の一貫性。高いほど「辻褄が合っていること」を重視。',
  },
  {
    key: 'agreement',
    label: '合意可能性',
    short: '合意',
    hint: '関係者が納得し実行にコミットできるか（DQ の Commitment to Action）。高いほど単純で揉めない・運用現場が回ることを重視。',
  },
];

export const AXIS_KEYS = AXES.map((a) => a.key);

// すべて既定値（TOTAL を均等配分）にしたスライダー状態。
export function defaultSliders() {
  const v = Math.round(TOTAL / AXES.length);
  return Object.fromEntries(AXES.map((a) => [a.key, v]));
}
