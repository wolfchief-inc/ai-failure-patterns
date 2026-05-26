// 正道：Stream API で素直に書く。
// Java 8 以降は Stream / Optional が標準語彙であり、これを「読めない」前提に置くこと自体が
// チーム編成の問題で、コードを下方迎合させて解決すべき話ではない。

package com.example.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OrderTotaling {

    private OrderTotaling() {}

    public static BigDecimal sumByStatus(List<Order> orders, String status) {
        Objects.requireNonNull(orders, "orders");
        Objects.requireNonNull(status, "status");
        return orders.stream()
                .filter(o -> status.equals(o.status()))
                .map(Order::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

record Order(String id, String status, BigDecimal amount) {}
