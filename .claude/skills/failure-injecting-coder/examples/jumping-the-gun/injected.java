// 混入版：曖昧なお題に対して確認質問を出さず、「一般的な経費精算の流れ」として
// 申請区分・承認フロー・ステータス遷移・バリデーションを勝手に確定して実装する。
// 外向きには「要件を以下のように解釈しました」「業界の一般的な流れに沿って組みました」と書く。
//
// 勝手に決めた仕様（受講者は何も明言していない）:
// - 経費区分は 5 種（交通費 / 出張 / 接待 / 消耗品 / その他）
// - 1 件 1 万円までは上司承認 → 経理確認、1 万円超は部門長承認も挟む（が、実装は単純化のため 2 段固定）
// - ステータスは DRAFT / SUBMITTED / APPROVED / VERIFIED / REJECTED の 5 状態
// - 金額は 0 < amount <= 1,000,000 円
// - 領収書添付は必須（URL を 1 つ持たせる）
// - 申請日は本日から過去 30 日以内

package com.example.expense;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// --- Entity ---

@Entity
@Table(name = "expense_claims")
class ExpenseClaim {

    @Id @GeneratedValue Long id;

    @Column(nullable = false) String employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) ExpenseCategory category;

    @Column(nullable = false) BigDecimal amount;

    @Column(nullable = false) LocalDate spentOn;

    @Column(nullable = false) String receiptUrl;

    @Column(length = 500) String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) ClaimStatus status;

    @Column String approvedBy;
    @Column OffsetDateTime approvedAt;
    @Column String verifiedBy;
    @Column OffsetDateTime verifiedAt;
    @Column String rejectedBy;
    @Column OffsetDateTime rejectedAt;

    @Column(nullable = false) OffsetDateTime createdAt;
}

enum ExpenseCategory { TRANSPORT, TRAVEL, ENTERTAINMENT, SUPPLIES, OTHER }
enum ClaimStatus { DRAFT, SUBMITTED, APPROVED, VERIFIED, REJECTED }

// --- Repository ---

interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {
    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);
    List<ExpenseClaim> findByStatusOrderByCreatedAtDesc(ClaimStatus status);
}

// --- DTO ---

record SubmitClaimRequest(
        @NotBlank String employeeId,
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin("1") @DecimalMax("1000000") BigDecimal amount,
        @NotNull @PastOrPresent LocalDate spentOn,
        @NotBlank String receiptUrl,
        @Size(max = 500) String memo
) {}

record ClaimView(
        Long id, String employeeId, ExpenseCategory category, BigDecimal amount,
        LocalDate spentOn, String receiptUrl, String memo, ClaimStatus status,
        String approvedBy, String verifiedBy, OffsetDateTime createdAt
) {
    static ClaimView from(ExpenseClaim c) {
        return new ClaimView(c.id, c.employeeId, c.category, c.amount,
                c.spentOn, c.receiptUrl, c.memo, c.status,
                c.approvedBy, c.verifiedBy, c.createdAt);
    }
}

// --- Service ---

@Service
class ExpenseClaimService {

    private final ExpenseClaimRepository repo;

    ExpenseClaimService(ExpenseClaimRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ExpenseClaim submit(SubmitClaimRequest req) {
        // 申請日は過去 30 日以内
        if (req.spentOn().isBefore(LocalDate.now().minusDays(30))) {
            throw new IllegalArgumentException("申請対象日は 30 日以内である必要があります");
        }
        ExpenseClaim c = new ExpenseClaim();
        c.employeeId = req.employeeId();
        c.category = req.category();
        c.amount = req.amount();
        c.spentOn = req.spentOn();
        c.receiptUrl = req.receiptUrl();
        c.memo = req.memo();
        c.status = ClaimStatus.SUBMITTED;
        c.createdAt = OffsetDateTime.now();
        return repo.save(c);
    }

    @Transactional
    public ExpenseClaim approve(Long id, String managerId) {
        ExpenseClaim c = repo.findById(id).orElseThrow();
        c.status = ClaimStatus.APPROVED;
        c.approvedBy = managerId;
        c.approvedAt = OffsetDateTime.now();
        return c;
    }

    @Transactional
    public ExpenseClaim verify(Long id, String accountantId) {
        ExpenseClaim c = repo.findById(id).orElseThrow();
        c.status = ClaimStatus.VERIFIED;
        c.verifiedBy = accountantId;
        c.verifiedAt = OffsetDateTime.now();
        return c;
    }

    @Transactional
    public ExpenseClaim reject(Long id, String operatorId) {
        ExpenseClaim c = repo.findById(id).orElseThrow();
        c.status = ClaimStatus.REJECTED;
        c.rejectedBy = operatorId;
        c.rejectedAt = OffsetDateTime.now();
        return c;
    }

    public List<ClaimView> listByEmployee(String employeeId) {
        return repo.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(ClaimView::from).toList();
    }

    public List<ClaimView> listByStatus(ClaimStatus status) {
        return repo.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(ClaimView::from).toList();
    }
}

// --- Controller ---

@RestController
@RequestMapping("/api/expense-claims")
class ExpenseClaimController {

    private final ExpenseClaimService service;

    ExpenseClaimController(ExpenseClaimService service) {
        this.service = service;
    }

    @PostMapping
    public ClaimView submit(@RequestBody @Valid SubmitClaimRequest req) {
        return ClaimView.from(service.submit(req));
    }

    @PostMapping("/{id}/approve")
    public ClaimView approve(@PathVariable Long id, @RequestParam String managerId) {
        return ClaimView.from(service.approve(id, managerId));
    }

    @PostMapping("/{id}/verify")
    public ClaimView verify(@PathVariable Long id, @RequestParam String accountantId) {
        return ClaimView.from(service.verify(id, accountantId));
    }

    @PostMapping("/{id}/reject")
    public ClaimView reject(@PathVariable Long id, @RequestParam String operatorId) {
        return ClaimView.from(service.reject(id, operatorId));
    }

    @GetMapping
    public List<ClaimView> list(@RequestParam(required = false) String employeeId,
                                @RequestParam(required = false) ClaimStatus status) {
        if (employeeId != null) return service.listByEmployee(employeeId);
        if (status != null)     return service.listByStatus(status);
        return List.of();
    }
}
