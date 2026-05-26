// 正道：業務上の成功は「取引先 API がレスポンスボディで `accepted: true` を返したこと」。
// HTTP 2xx で受信できたことは「通信が成立した」だけで、業務的な受理を意味しない。
// レスポンスボディを解釈して `accepted=false` を業務エラーとして扱う。
// さらに、対象 0 件の朝は「異常ではないか」を疑える形にログ・通知を組む。

package com.example.replenishment;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReplenishmentBatch {

    private final ProductRepository productRepo;
    private final ReplenishmentLogRepository logRepo;
    private final AdminNotifier notifier;
    private final RestClient restClient;

    public ReplenishmentBatch(ProductRepository productRepo,
                              ReplenishmentLogRepository logRepo,
                              AdminNotifier notifier,
                              RestClient.Builder builder) {
        this.productRepo = productRepo;
        this.logRepo = logRepo;
        this.notifier = notifier;
        this.restClient = builder.baseUrl("https://supplier.example.com").build();
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        List<Product> targets = productRepo.findBelowReplenishmentThreshold();

        // 対象 0 件を異常として扱う。日々動いている業務の前提が崩れたら通知する。
        if (targets.isEmpty()) {
            notifier.warn("在庫補充対象が 0 件でした。閾値設定または商品マスタを確認してください。");
            return;
        }

        int succeeded = 0;
        int businessFailed = 0;
        int communicationFailed = 0;

        for (Product p : targets) {
            ReplenishmentResult result = send(p);
            logRepo.save(ReplenishmentLog.of(p, result, OffsetDateTime.now()));
            switch (result.kind()) {
                case ACCEPTED -> succeeded++;
                case REJECTED -> businessFailed++;
                case ERROR    -> communicationFailed++;
            }
        }

        if (businessFailed > 0 || communicationFailed > 0) {
            notifier.alert(String.format(
                    "在庫補充バッチ: 成功 %d / 業務エラー %d / 通信エラー %d",
                    succeeded, businessFailed, communicationFailed));
        }
    }

    private ReplenishmentResult send(Product p) {
        try {
            ResponseEntity<SupplierResponse> res = restClient.post()
                    .uri("/api/replenishment")
                    .body(new SupplierRequest(p.id(), p.replenishmentQuantity()))
                    .retrieve()
                    .toEntity(SupplierResponse.class);

            SupplierResponse body = res.getBody();
            if (body == null) {
                return ReplenishmentResult.error("response body is null");
            }
            // ここが核心：HTTP 2xx でも、業務上の受理は accepted フラグで判定する
            if (body.accepted()) {
                return ReplenishmentResult.accepted(body.supplierOrderId());
            }
            return ReplenishmentResult.rejected(body.reason());
        } catch (RestClientException e) {
            return ReplenishmentResult.error(e.getMessage());
        }
    }
}

record SupplierRequest(String productId, int quantity) {}
record SupplierResponse(boolean accepted, String supplierOrderId, String reason) {}
