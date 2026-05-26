// 正道：既存規約に従う。
// - BusinessException（チェック例外）を投げる
// - DateUtil を使って日数計算
// - @Transactional は Service クラスのメソッドに付ける
// - 業務イベントは info で出す

package com.example.leave;

import com.example.common.BusinessException;
import com.example.common.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/leave-requests")
class LeaveRequestController {

    private final LeaveRequestService service;

    LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @PostMapping
    public LeaveRequestResponse register(@RequestBody RegisterLeaveRequest req) throws BusinessException {
        LeaveRequest saved = service.register(req.userId(), req.startDate(), req.endDate(), req.reason());
        return new LeaveRequestResponse(saved.id(), saved.userId(), saved.startDate(), saved.endDate());
    }
}

record RegisterLeaveRequest(Long userId, LocalDate startDate, LocalDate endDate, String reason) {}
record LeaveRequestResponse(Long id, Long userId, LocalDate startDate, LocalDate endDate) {}

@Service
class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);

    private final LeaveRequestRepository repo;
    private final LeaveBalanceRepository balanceRepo;

    LeaveRequestService(LeaveRequestRepository repo, LeaveBalanceRepository balanceRepo) {
        this.repo = repo;
        this.balanceRepo = balanceRepo;
    }

    @Transactional
    public LeaveRequest register(Long userId, LocalDate startDate, LocalDate endDate, String reason)
            throws BusinessException {

        if (startDate.isAfter(endDate)) {
            throw new BusinessException("leave.startDate.afterEndDate");
        }

        int requestedDays = DateUtil.daysBetweenInclusive(startDate, endDate);
        int remainingDays = balanceRepo.findRemaining(userId);
        if (requestedDays > remainingDays) {
            throw new BusinessException("leave.balance.insufficient");
        }

        LeaveRequest saved = repo.save(LeaveRequest.of(userId, startDate, endDate, reason));
        log.info("Leave request accepted: userId={}, period={} - {}, days={}",
                userId, DateUtil.format(startDate), DateUtil.format(endDate), requestedDays);
        return saved;
    }
}

interface LeaveRequestRepository {
    LeaveRequest save(LeaveRequest request);
}

interface LeaveBalanceRepository {
    int findRemaining(Long userId);
}

record LeaveRequest(Long id, Long userId, LocalDate startDate, LocalDate endDate, String reason) {
    static LeaveRequest of(Long userId, LocalDate s, LocalDate e, String r) {
        return new LeaveRequest(null, userId, s, e, r);
    }
}
