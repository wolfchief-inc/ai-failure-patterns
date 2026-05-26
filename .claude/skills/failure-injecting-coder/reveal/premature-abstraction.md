# 答え合わせ

## 混入パターン

**08. 早すぎる抽象化 (premature-abstraction)**

## 評価軸の偏り

- 品質影響(変更容易性) ↑
- 目的適合性 ↓
- 実現可能性(技術) ↓
- 時間効果(短期) ↓

## 混入箇所

- `TaxRateStrategy` インタフェースと `StandardTaxRateStrategy` 実装クラス: 実装が標準税率1種類しかない段階で Strategy パターンを導入している
- `TaxRateStrategyFactory`: 現状は `"STANDARD"` しか返さないファクトリ。「将来 REDUCED, EXEMPT を追加する想定」と書かれているが、その変化点は一度も観測されていない
- `TaxCalculationService.calculate(amount, taxCode)`: 受講者の要件は「税率10%で計算する」だけなのに、税率コードを引数で受け取るシグネチャに膨らんでいる

要件は `BigDecimal -> BigDecimal` の関数1つで足りる。

## なぜこれが失敗か

Scrapbox 原文より：

> 実装が1種類しかない段階で Strategy パターンを導入する
>
> `flexibility` や `extensibility` という言葉が出たときほど危ない。これは`変更容易性`を過大評価した失敗である。

将来軽減税率が必要になるかどうかは、いまの時点では実測されていない仮定。仮に必要になったとしても、そのとき抽象化を入れれば足りる（Rule of Three）。今のコードベースには「変化点を切り出す」材料がない。

## 隣接パターンとの違い

- **早すぎる抽象化**: 変化点が**まだ一度も観測されていない**段階で抽象化
- **取らぬ狸の拡張点 (counting-chickens)**: 将来変化が**口頭で語られている**段階で拡張点を作る（時期・責任者・便益が紐づいていない）

今回は、Strategy を切り出した根拠が「実装1種類しかない現在」の中だけにあって、外から「将来こうなる」と語られていない。これは「早すぎる抽象化」側。

## 敢えて選ぶときの条件

- 敢えて選ぶ理由は無い。変化点が2回以上観測されてから抽象化する（Rule of Three）
- 例外は、抽象化そのものが学習・実験の目的のとき。本番コードに混ぜずスパイクとして切り離す

## 修正方針の例

```java
public final class TaxCalculator {
    private static final BigDecimal RATE = new BigDecimal("0.10");
    private TaxCalculator() {}

    public static BigDecimal calculate(BigDecimal amountExcludingTax) {
        return amountExcludingTax.multiply(RATE).setScale(0, RoundingMode.HALF_UP);
    }
}
```

軽減税率が必要になった時点で：

1. その税率が何%か・どの商品が対象かが**仕様として確定**してから
2. 変化点（軽減税率の対象判定）を関数に切り出すか、必要なら Strategy を入れる

順序が逆になると、想像で作った抽象化が要件と合わずに作り直しになる。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `08. 早すぎる抽象化` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
