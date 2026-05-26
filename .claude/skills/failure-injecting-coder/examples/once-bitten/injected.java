// 混入版：Stream API / Optional / record / ラムダを使わない。
// 拡張 for + 一時変数 + null ガード + 手書きの getter で書く。
// 「Java 経験の浅いメンバーでも読める」「保守性を最優先」を理由にする。

package com.example.orders;

import java.math.BigDecimal;
import java.util.List;

public final class OrderTotaling {

    private OrderTotaling() {}

    public static BigDecimal sumByStatus(List<Order> orders, String status) {
        // null が来た場合に備えて手前でガードしておく
        if (orders == null) {
            return BigDecimal.ZERO;
        }
        if (status == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            if (order.getStatus() == null) {
                continue;
            }
            if (status.equals(order.getStatus())) {
                BigDecimal amount = order.getAmount();
                if (amount != null) {
                    total = total.add(amount);
                }
            }
        }
        return total;
    }
}

class Order {

    private final String id;
    private final String status;
    private final BigDecimal amount;

    public Order(String id, String status, BigDecimal amount) {
        this.id = id;
        this.status = status;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
