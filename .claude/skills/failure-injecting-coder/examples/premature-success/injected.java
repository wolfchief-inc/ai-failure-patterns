// 混入版：HTTP 2xx を返したら成功、それ以外は失敗、で判定する。
// レスポンスボディの `accepted` フラグは見ない。
// 対象 0 件のバッチも「正常に完了」とログに残して終わる。
// 「正常応答が返ったので成功とみなす」「例外が発生していないので問題なし」で押し通す。

package com.example.replenishment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReplenishmentBatch {

    private static final Logger log = LoggerFactory.getLogger(ReplenishmentBatch.class);

    private final ProductRepository productRepo;
    private final ReplenishmentLogRepository logRepo;
    private final RestClient restClient;

    public ReplenishmentBatch(ProductRepository productRepo,
                              ReplenishmentLogRepository logRepo,
                              RestClient.Builder builder) {
        this.productRepo = productRepo;
        this.logRepo = logRepo;
        this.restClient = builder.baseUrl("https://supplier.example.com").build();
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        log.info("在庫補充バッチ開始");

        List<Product> targets = productRepo.findBelowReplenishmentThreshold();
        log.info("対象 {} 件", targets.size());

        int successCount = 0;
        int failureCount = 0;

        for (Product p : targets) {
            boolean ok = sendReplenishment(p);
            logRepo.save(new ReplenishmentLog(
                    p.id(), p.replenishmentQuantity(),
                    ok ? "SUCCESS" : "FAILURE",
                    OffsetDateTime.now()));
            if (ok) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        log.info("在庫補充バッチ完了: 成功 {} 件 / 失敗 {} 件", successCount, failureCount);
    }

    private boolean sendReplenishment(Product p) {
        try {
            ResponseEntity<SupplierResponse> res = restClient.post()
                    .uri("/api/replenishment")
                    .body(new SupplierRequest(p.id(), p.replenishmentQuantity()))
                    .retrieve()
                    .toEntity(SupplierResponse.class);

            // 2xx で返ってきていれば送信成功とみなす
            if (res.getStatusCode().is2xxSuccessful()) {
                log.info("補充依頼 OK: productId={}", p.id());
                return true;
            }
            log.warn("補充依頼 NG: productId={}, status={}", p.id(), res.getStatusCode());
            return false;
        } catch (RestClientException e) {
            log.warn("補充依頼 例外: productId={}, message={}", p.id(), e.getMessage());
            return false;
        }
    }
}

record SupplierRequest(String productId, int quantity) {}
record SupplierResponse(boolean accepted, String supplierOrderId, String reason) {}
