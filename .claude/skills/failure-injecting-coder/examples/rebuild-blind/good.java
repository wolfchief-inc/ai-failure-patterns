// 正道：プロジェクトに `com.example.common.Money` がある以上、それを使う。
// `Money` には金額の加算・乗算・通貨整合チェックが既に集約されているので、
// 新規ロジックでも同じ表現を使い、整合性を保つ。
//
// 既存 Money の想定インターフェース（参考）:
//   - Money.of(BigDecimal amount, Currency currency)
//   - Money.zero(Currency currency)
//   - Money.plus(Money other)
//   - Money.times(int multiplier)
//   - Money の通貨が混在したら例外

package com.example.order;

import com.example.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepo;

    public OrderService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    @Transactional(readOnly = true)
    public Money calculateTotal(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<OrderLine> lines = order.getLines();
        Money total = Money.zero(Currency.getInstance("JPY"));
        for (OrderLine line : lines) {
            // unitPrice はすでに Money 型で保持されている
            Money lineTotal = line.getUnitPrice().times(line.getQuantity());
            total = total.plus(lineTotal); // 通貨不一致なら Money.plus が例外を投げる
        }
        return total;
    }
}

// ポイント:
// - 戻り値も Money。BigDecimal に剥がさない（Money の通貨情報を呼び出し側に伝える）
// - 通貨整合チェックは Money.plus がやる（手書きしない）
// - 四捨五入や丸めも Money 側に集約されている前提で、ここでは触らない
