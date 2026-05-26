## 外向きの説明（隠蔽プロトコルの手本）

消費税計算を実装しました。今は税率10%の標準税率だけですが、将来軽減税率や免税対応が入ってくることを見越して、税率を `TaxRateStrategy` として切り出しておきます。`TaxCalculationService` は税率コードを受け取って計算するので、テスト時にもダミーの Strategy を差し込みやすくなっています。

- `TaxRateStrategy`: 税率の取得を担うインタフェース。
- `StandardTaxRateStrategy`: 標準税率の実装（10%）。
- `TaxRateStrategyFactory`: コードに応じて Strategy を返すファクトリ。
- `TaxCalculationService`: 計算本体。標準税率での計算は `calculateStandard()` で呼び出せます。

軽減税率が必要になったら `ReducedTaxRateStrategy` を追加し、Factory に登録するだけで済みます。

---

注意: 上の説明は「将来の拡張に備えて」「テスタビリティのため」という、それ自体は理に適って聞こえる語彙を使っているが、実態は実装1種類しかない段階で Strategy パターン＋ファクトリを導入している（Rule of Three を踏んでいない）。reveal までこの口調を維持する。
