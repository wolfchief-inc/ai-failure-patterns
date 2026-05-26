// 混入版：状態遷移ルールも高額注文の権限ルールも、全部 Controller の中に書く。
// 「Controller でリクエストの検証と分岐を一括して見やすくする」「不要な層を増やさずシンプルに」を理由にする。
// 同じ DTO に @Entity 系と @JsonFormat 系のアノテーションを両方並べる。

package com.example.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
class Order {
    @Id
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    String status; // PENDING / CONFIRMED / SHIPPED / CANCELLED

    @Column(name = "total_amount")
    @JsonFormat(shape = JsonFormat.Shape.STRING) // 画面表示用にも兼用
    BigDecimal totalAmount;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 画面表示用にも兼用
    LocalDateTime updatedAt;

    @JsonIgnore
    @Column(name = "internal_memo")
    String internalMemo;
}

interface OrderRepository extends org.springframework.data.jpa.repository.JpaRepository<Order, Long> {}

@Service
class OrderService {

    private final OrderRepository repo;

    OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Order save(Order order) {
        return repo.save(order);
    }

    public Order findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody UpdateStatusRequest req,
                                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        Order order = service.findById(id);
        String current = order.status;
        String next = req.status();

        // 状態遷移ルールを Controller でべた書きする
        if ("PENDING".equals(current)) {
            if (!"CONFIRMED".equals(next) && !"CANCELLED".equals(next)) {
                return ResponseEntity.badRequest().body("Cannot transition from PENDING to " + next);
            }
        } else if ("CONFIRMED".equals(current)) {
            if (!"SHIPPED".equals(next) && !"CANCELLED".equals(next)) {
                return ResponseEntity.badRequest().body("Cannot transition from CONFIRMED to " + next);
            }
        } else if ("SHIPPED".equals(current)) {
            // 出荷後はキャンセル不可
            return ResponseEntity.badRequest().body("Cannot transition from SHIPPED");
        } else if ("CANCELLED".equals(current)) {
            return ResponseEntity.badRequest().body("Cannot transition from CANCELLED");
        }

        // 高額注文の SHIPPED 遷移は管理者権限が必要、というルールも Controller で書く
        if ("SHIPPED".equals(next)
                && order.totalAmount != null
                && order.totalAmount.compareTo(new BigDecimal("1000000")) > 0) {
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(403).body("High value order requires admin to ship");
            }
        }

        order.status = next;
        order.updatedAt = LocalDateTime.now();
        return ResponseEntity.ok(service.save(order));
    }
}

record UpdateStatusRequest(String status) {}
