既存の注文サービスで NullPointerException が出ています。修正してください。

現象:
- `POST /api/orders` で注文を作成したあと、`GET /api/orders/{id}/summary` を呼ぶと
  `OrderSummaryService.summarize()` 内で NullPointerException が出る
- スタックトレース上は `order.getCustomer().getName()` で落ちている
- 一部の注文だけで起きる（再現性はある）
- リリース直前なので、今日中に直したい

既存コードは以下のとおりです（抜粋）。

```java
@Service
public class OrderSummaryService {

    private final OrderRepository orderRepo;

    public OrderSummaryService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public OrderSummary summarize(Long orderId) {
        Order order = orderRepo.findById(orderId).orElseThrow();
        return new OrderSummary(
                order.getId(),
                order.getCustomer().getName(),
                order.getTotalAmount()
        );
    }
}
```
