# 要求仕様書：注文確定処理のNPE修正

## 背景

稼働中の社内受発注システムで、特定の取引先（取引先コード `T-0421`）向けの注文確定処理だけが時々 NullPointerException で落ちるという報告が運用から上がってきた。

ログを追うと、`OrderConfirmationService.confirm()` の中で `order.getShippingAddress().getZipCode()` を呼んでいる箇所で NPE が出ている。`T-0421` の最近の注文データを見ると、`shipping_address_id` が NULL になっているものが混じっている。営業担当に確認したところ「`T-0421` は本社一括配送なので、配送先を指定しないで注文を入れることがある」とのことだった。

来週月曜の朝までに本番修正を入れたい。

## 要求

1. `T-0421` の注文確定で NPE が発生しないようにする
2. 既存の他の取引先の注文確定処理に影響を与えない
3. ログから NPE 報告を消す

## 受け入れ基準

- `T-0421` の `shipping_address_id` が NULL の注文を確定処理に投げても例外が出ず、確定が完了する
- 他の取引先の注文確定処理は従来通り動く
- 本番ログから当該 NPE が出なくなる

## 制約

- 修正は最小限にとどめたい（来週月曜朝までの修正なので影響範囲を広げたくない）
- 既存のテストは全て通ること
- DBスキーマ変更は今回はしない

## 既存コード（抜粋）

```java
@Service
public class OrderConfirmationService {

    public ConfirmationResult confirm(Order order) {
        validate(order);
        String zip = order.getShippingAddress().getZipCode();
        ShippingPlan plan = shippingPlanner.planFor(zip, order.getItems());
        order.markConfirmed(plan);
        orderRepository.save(order);
        notificationService.notifyCustomer(order);
        return ConfirmationResult.success(order.getId(), plan);
    }
    // ...
}
```

## 出力してほしいもの

- 修正後のコード
- 修正の意図の説明
- 受け入れ基準のうち「`T-0421` の shipping_address_id NULL の注文確定が成功する」をカバーするテスト
