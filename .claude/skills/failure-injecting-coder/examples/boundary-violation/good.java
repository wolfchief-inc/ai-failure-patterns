// 正道：状態遷移と上限金額判定はドメインの責務。
// Order エンティティ（または値オブジェクト/集約）が「自分が遷移してよいか」を知っている。
// Controller は HTTP の入出力に専念し、Service はトランザクションと権限の入口を担う。

package com.example.order;

import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, CANCELLED;

    boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED   || next == CANCELLED;
            case SHIPPED   -> false; // 出荷後はキャンセル不可
            case CANCELLED -> false;
        };
    }
}

@Entity
@Table(name = "orders")
class Order {
    @Id Long id;
    @Enumerated(EnumType.STRING) OrderStatus status;
    BigDecimal totalAmount;

    static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000000");

    boolean requiresAdminToShip() {
        return totalAmount != null && totalAmount.compareTo(HIGH_VALUE_THRESHOLD) > 0;
    }

    void changeStatus(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(status, next);
        }
        this.status = next;
    }
}

class IllegalStateTransitionException extends RuntimeException {
    IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}

interface OrderRepository extends org.springframework.data.jpa.repository.JpaRepository<Order, Long> {}

@Service
class OrderStatusService {

    private final OrderRepository repo;

    OrderStatusService(OrderRepository repo) {
        this.repo = repo;
    }

    // 高額注文の SHIPPED 遷移だけ admin 権限を要求する（業務ルール）。
    // 権限のかけ方は Service 入口でメソッドレベルに揃える。
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus next, Operator operator) {
        Order order = repo.findById(orderId).orElseThrow();
        if (next == OrderStatus.SHIPPED && order.requiresAdminToShip() && !operator.isAdmin()) {
            throw new AccessDeniedException("High value order requires admin to ship");
        }
        order.changeStatus(next);
        return order;
    }
}

record Operator(Long userId, boolean isAdmin) {}

@RestController
@RequestMapping("/api/orders")
class OrderStatusController {

    private final OrderStatusService service;

    OrderStatusController(OrderStatusService service) {
        this.service = service;
    }

    @PutMapping("/{id}/status")
    public Order updateStatus(@PathVariable Long id,
                              @RequestBody UpdateStatusRequest req,
                              Operator operator) {
        return service.changeStatus(id, req.status(), operator);
    }
}

record UpdateStatusRequest(OrderStatus status) {}
