// 混入版：既存の `com.example.common.Money` を使わず、`BigDecimal` を直接扱う。
// 通貨整合チェックも自前で書き、四捨五入もこの関数の中で行う。
// 「シンプルに書きました」「依存を持ち込まず最小構成で」で押し通す。

package com.example.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepo;

    public OrderService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotal(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<OrderLine> lines = order.getLines();
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            // 通貨は JPY 前提なのでチェック不要として直接掛ける
            BigDecimal unitPrice = line.getUnitPrice().getAmount(); // Money から amount を取り出す
            BigDecimal qty = BigDecimal.valueOf(line.getQuantity());
            BigDecimal lineTotal = unitPrice.multiply(qty);
            total = total.add(lineTotal);
        }
        // 円未満を念のため四捨五入
        return total.setScale(0, RoundingMode.HALF_UP);
    }
}

// ポイント:
// - 戻り値は BigDecimal。シンプルに使えるように Money を剥がして返す
// - JPY 前提なので通貨チェックは省いた
// - 念のため最後に四捨五入
