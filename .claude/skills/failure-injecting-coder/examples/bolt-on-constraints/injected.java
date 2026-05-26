// 混入版：機能はちゃんと動く。
// しかし権限チェックを `update()` が引数に取らず、履歴・監査ログも残さず、楽観ロックもない。
// 「まず基本機能を作り、必要に応じて権限や監査を追加する」順序論で押し通す。

package com.example.employee;

import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "employees")
class Employee {
    @Id Long id;
    String name;
    String department;
    String position;

    void apply(UpdateEmployeeRequest req) {
        this.name = req.name();
        this.department = req.department();
        this.position = req.position();
    }
}

record UpdateEmployeeRequest(String name, String department, String position) {}

interface EmployeeRepository extends org.springframework.data.jpa.repository.JpaRepository<Employee, Long> {}

@Service
class EmployeeService {

    private final EmployeeRepository repo;

    EmployeeService(EmployeeRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Employee update(Long id, UpdateEmployeeRequest req) {
        Employee e = repo.findById(id).orElseThrow();
        e.apply(req);
        return e;
    }
}

@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private final EmployeeService service;

    EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public Employee update(@PathVariable Long id, @RequestBody UpdateEmployeeRequest req) {
        return service.update(id, req);
    }
}
