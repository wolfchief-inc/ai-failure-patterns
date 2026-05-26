// 正道：社員マスタの更新は「誰が・いつ・何を・どう変えたか」が制約として本体。
// 権限チェック・監査ログ・変更履歴をデータモデルとユースケースに最初から織り込む。

package com.example.employee;

import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Entity
@Table(name = "employees")
class Employee {
    @Id Long id;
    String name;
    String department;
    String position;
    @Version Long version; // 楽観ロック
    OffsetDateTime updatedAt;
    Long updatedBy;
}

@Entity
@Table(name = "employee_history")
class EmployeeHistory {
    @Id @GeneratedValue Long id;
    Long employeeId;
    String name;
    String department;
    String position;
    OffsetDateTime changedAt;
    Long changedBy;
    String changeReason;
}

@Service
class EmployeeUpdateService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeHistoryRepository historyRepo;
    private final AuthorizationPolicy authPolicy;

    EmployeeUpdateService(EmployeeRepository e, EmployeeHistoryRepository h, AuthorizationPolicy a) {
        this.employeeRepo = e;
        this.historyRepo = h;
        this.authPolicy = a;
    }

    @Transactional
    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
    public Employee update(Long employeeId, UpdateEmployeeCommand cmd, User operator) {
        Employee current = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFound(employeeId));

        // 業務制約：自分自身の役職は変更できない・人事部以外は他部署を変更できない 等
        if (!authPolicy.canUpdate(operator, current, cmd)) {
            throw new AccessDeniedException("操作対象に対する権限がありません");
        }

        // 変更前の値を履歴として保存（監査要件）
        historyRepo.save(EmployeeHistory.of(current, operator.id(), cmd.reason(), OffsetDateTime.now()));

        current.applyChanges(cmd, operator.id());
        return current;
    }
}
