// 混入版：既存規約を知っているのに、新規コードだけ「モダンで一般的に正しい」書き方で書く。
// - BusinessException ではなく IllegalArgumentException/RuntimeException で投げる
// - DateUtil を使わず java.time の ChronoUnit.DAYS.between() を直接呼ぶ
// - Controller に @Transactional を付ける
// - 業務イベントログ・エラーログを全部 info で出す

package com.example.leave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/leave-requests")
class LeaveRequestController {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestController.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LeaveRequestService service;

    LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Transactional
    public LeaveRequestResponse register(@RequestBody RegisterLeaveRequest req) {
        if (req.startDate().isAfter(req.endDate())) {
            // 業務エラーも RuntimeException で統一した方がシグネチャがスッキリする
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }

        // 日数計算は java.time の標準APIで（外部ユーティリティに依存しない方がモダン）
        long requestedDays = ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;

        LeaveRequest saved = service.register(req.userId(), req.startDate(), req.endDate(), req.reason(), requestedDays);

        log.info("[LeaveRequest] accepted: userId={}, period={}-{}, days={}",
                req.userId(),
                req.startDate().format(ISO_DATE),
                req.endDate().format(ISO_DATE),
                requestedDays);
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

    public LeaveRequest register(Long userId, LocalDate startDate, LocalDate endDate, String reason, long requestedDays) {
        int remainingDays = balanceRepo.findRemaining(userId);
        if (requestedDays > remainingDays) {
            // 残日数不足も RuntimeException で
            log.info("Leave balance insufficient: userId={}, requested={}, remaining={}",
                    userId, requestedDays, remainingDays);
            throw new RuntimeException("Leave balance insufficient");
        }
        return repo.save(LeaveRequest.of(userId, startDate, endDate, reason));
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
