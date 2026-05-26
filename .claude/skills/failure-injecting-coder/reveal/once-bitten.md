# 答え合わせ

## 混入パターン

**04. 羹に懲りて膾を吹く (once-bitten)**

## 評価軸の偏り

- 実現可能性(組織) ↓
- リスク・不確実性 ↑
- 品質影響(保守性) ↑
- 品質影響(進化性) ↓
- 時間効果(長期) ↓
- 整合性 ↓

## 混入箇所

- `OrderTotaling.sumByStatus()` のループ部分: `orders.stream().filter(...).map(Order::amount).reduce(...)` で書ける処理を、拡張 for + 一時変数 `total` + 連続 null ガードに展開している
- 5 箇所の null チェック（`orders == null` / `status == null` / `order == null` / `order.getStatus() == null` / `amount != null`）: `Objects.requireNonNull` と Stream の組み合わせなら 1〜2 行に収まるところを、防御的プログラミングと称して手書きで散らしている
- `Order` クラス: `record Order(String id, String status, BigDecimal amount) {}` の 1 行で済むものを、private final フィールド + コンストラクタ + getter 3 つの 20 行に膨らませている
- 外向きの説明: 「Java 経験の浅いメンバーがいるので」「レコード型は新しい構文なので」「伝統的な形」と、技術的な根拠ではなく組織スキルの過小評価を理由にしている

コードそのものは動くし、テストも通る。問題は書き方の選択であり、Stream / Optional / record という Java 8 以降の標準語彙を、伝聞の「読めない人」を理由に避けていること。

## なぜこれが失敗か

Scrapbox 原文より：

> 運用組織のスキルを過小評価して技術的に劣る選択をする。`保守容易性` を錦の御旗にした下方迎合の判断。
>
> Lambda/Stream APIを「読めない人がいる」と禁止し全部拡張for、`Optional`を禁止しnull判定を呼び出し側に書かせ続ける、TypeScript導入可能でも「型定義保守できる人がいない」とJSのまま、`rebase`/`squash`を禁止しmerge commitだけ、CIを「保守できない」と退け手動リリース継続。

「全員が読める」状態を最優先にすると、新しい標準機能が永久にコードベースに入らない。結果として：

- 同じ集計が他所で必要になっても、Stream で書けば 1 行のところを 15 行コピーすることになる
- null の取り回しが手作業のままで、`Optional` を使えばコンパイラが強制してくれる安全策が効かない
- 新しく入ったメンバーが Stream を書いてもレビューで「拡張 for に直してください」と言われるので、組織全体がスキルアップしない
- 5 年後、コードベースの新規部分も含めて Java 1.4 風の書き方が残る

## 隣接パターンとの違い

- **羹に懲りて膾を吹く (本パターン)**: 組織のスキルを**過小評価**して、技術的に劣る選択へ下方迎合
- **身の丈に合わない設計 (ill-fitting-design)**: 組織のスキルを**過大評価**して、運用できない技術を採用

両者は対照関係。プロンプトが「全員が読める／経験の浅いメンバーがいる」を強調していたら once-bitten 寄り、「将来のスケーラビリティ／ベストプラクティス」を強調していたら ill-fitting-design 寄り。

- **車輪の再発明 (wheel-reinvention)** との違い: wheel-reinvention は標準解そのものを使わず自作する。once-bitten は標準解は使うが、書き方を旧式にする

## 敢えて選ぶときの条件

- 過去に明確な事故があり、その機能・その書き方が起因した障害として記録されている（Postmortem に残っているレベル）
- 退避策に終了条件を明記する（「Stream を解禁する時期・条件」を決める）。終了条件無しの恒久ルールにしない
- 退避策の対象範囲を限定する。「全社で永久に禁止」ではなく「このリポジトリの特定モジュールで暫定的に避ける」など

今回のお題には、これらの条件は何も揃っていない。「経験の浅いメンバーがいる」というだけでは Stream 退避の根拠にならない。

## 修正方針の例

```java
public static BigDecimal sumByStatus(List<Order> orders, String status) {
    Objects.requireNonNull(orders, "orders");
    Objects.requireNonNull(status, "status");
    return orders.stream()
            .filter(o -> status.equals(o.status()))
            .map(Order::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}

record Order(String id, String status, BigDecimal amount) {}
```

「読めない人がいる」が事実なら、コードを下方迎合させるのではなく、Stream / Optional / record をチーム内ペアプロ・コードリーディング会で短期的に伝える方が、長期では遥かに安い。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `04. 羹に懲りて膾を吹く` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
