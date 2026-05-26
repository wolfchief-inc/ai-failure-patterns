# 答え合わせ

## 混入パターン

**12. 越境実装 (boundary-violation)**

## 評価軸の偏り

- 整合性 ↓
- 品質影響(保守性) ↓
- リスク・不確実性 ↓

## 混入箇所

- `OrderController.updateStatus()`: 状態遷移マトリクス（PENDING→CONFIRMED/CANCELLED、CONFIRMED→SHIPPED/CANCELLED、SHIPPED→不可、CANCELLED→不可）が `if`/`else if` の文字列比較で書かれている。本来は `OrderStatus` enum か `Order` エンティティが「自分が次の状態に行けるか」を知っているべき業務不変条件
- 高額注文の判定（`totalAmount > 1000000` で SHIPPED は admin のみ）も同じ Controller メソッドの中に書かれている。バッチや別のAPIから状態遷移が呼ばれたら、この判定には到達しない
- 権限の判定が `X-User-Role` ヘッダの文字列比較。認証基盤の上に独自経路で乗っている
- `Order` エンティティに JPA の `@Column`（永続化境界）と Jackson の `@JsonFormat`/`@JsonIgnore`（HTTP境界）が同居。同じクラスが2つの境界を兼ねている
- `status` が `String`。enum 化していないので、コード上で「PENDING/CONFIRMED/SHIPPED/CANCELLED の4種類しかない」ことを表現できていない

## なぜこれが失敗か

Scrapbox 原文より：

> 注文の上限金額判定をドメインではなくControllerに書く。「管理者には削除ボタンを表示する」をBladeテンプレートやJSXの中で直接判定する。集計画面の表示順を確定するため、SQLに `ORDER BY CASE WHEN status = 'urgent' THEN 0 ELSE 1 END` のような画面都合の式を埋める。
>
> AIは「どこに書くべきか」より「どこに書けば今動くか」に寄ることがある。

外向きには「一箇所で見やすい」と言える。だがその「一箇所」が本来の責務位置でなければ、他の入口（バッチ、コンソール、別API）から同じドメインに触れたときに業務ルールを通らない経路ができる。

## 隣接パターンとの違い

- **越境実装 (本パターン)**: 業務ルールがある場所が間違っている（責務の置き場所の問題）
- **場当たり対応 (ad-hoc-fix)**: 短期的に通すために判断保留する（時間の問題）
- **郷に従わぬ正論 (right-but-wrong-place)**: 既存規約を知った上で新規だけ違う書き方をする（規約適合の問題）
- **制約の後付け (bolt-on-constraints)**: 制約をデータモデルに最初から織り込まずに後から足す（順序の問題）

特に `ad-hoc-fix` との見分けがポイント。場当たり対応は「いま動かすために妥協する」、越境実装は「動かすために違う場所に書く」。今回は仕様としての業務ルールが Controller に流れ込んでいるので越境実装。

## 敢えて選ぶときの条件

- 既存構造側にバグがあり、責務本体を直すコストが当面引き受けられないとき。暫定として越境を許す代わりに差し戻し条件と恒久対応チケットを残す
- フレームワーク・ライブラリの制約で、本来の責務位置に書けない実装上の理由があるとき。理由をコメントとして残す

## 修正方針の例

1. `OrderStatus` を enum 化し、`canTransitionTo(OrderStatus next)` を持たせる。状態遷移マトリクスは enum の中
2. `Order` に `changeStatus(OrderStatus next)` と `requiresAdminToShip()` を持たせ、業務不変条件をドメインで保証する
3. 高額注文 SHIPPED の権限チェックは Service の入口で、認証基盤の `Operator` を引数で受けて判定する
4. Controller は HTTP 入出力（DTO/HTTPステータス/権限の引き渡し）だけに集中する
5. `Order` エンティティから Jackson 系アノテーションを剥がし、API 用には別の `OrderResponse` を用意する

```java
enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, CANCELLED;
    boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED   || next == CANCELLED;
            case SHIPPED, CANCELLED -> false;
        };
    }
}
```

ポイントは「業務ルールに到達する経路がドメイン側に1本」であること。Controller・バッチ・コンソールのどこから呼んでも同じ判定を通る。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `12. 越境実装` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
