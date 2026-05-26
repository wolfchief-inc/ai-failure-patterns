// 正道：NPE は症状であって原因ではない。
// 「なぜ Order.customer が null になり得るのか」を調べることから始める。
//
// 調査の結果、たとえば以下のいずれかが原因として考えられる:
//   (A) Order 作成時に customerId が必須でなく、customer 未紐づけの Order が DB に存在する
//   (B) JPA のフェッチ戦略の問題で customer が遅延ロードされていない
//   (C) 顧客が削除されたが Order 側の外部キーが null 許可になっていてダングリング状態
//
// (A)〜(C) のどれが原因かによって、修正は別物になる。場当たりに `if (customer != null)` を入れない。
// 以下は (A) が原因だった場合の修正方針。

package com.example.order;

import jakarta.persistence.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Entity
@Table(name = "orders")
class Order {
    @Id Long id;

    // 顧客は必須にする（DB 側も NOT NULL 制約を追加するマイグレーションを別途用意）
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    Customer customer;

    BigDecimal totalAmount;

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}

@Entity
@Table(name = "customers")
class Customer {
    @Id Long id;
    String name;
    public String getName() { return name; }
}

@Service
public class OrderSummaryService {

    private final OrderRepository orderRepo;

    public OrderSummaryService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public OrderSummary summarize(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // ここで customer.getName() を呼んでも、不変条件「Order には Customer が必ず紐づく」が
        // モデルとDBで保証されているので NPE は起きない。
        return new OrderSummary(
                order.getId(),
                order.getCustomer().getName(),
                order.getTotalAmount()
        );
    }
}

// 別途、対応するマイグレーションと注文作成側の修正:
//
// 1. 既存データで customer_id が null の注文を調査し、業務上どう扱うかを決める
//    （仮顧客に寄せる／削除する／別ステータスに移す等）
// 2. DB に NOT NULL 制約を追加するマイグレーション
// 3. OrderCreateService 側で customerId 必須バリデーションを入れる
// 4. テスト: customer なしで Order を作ろうとするとエラーになることを確認
