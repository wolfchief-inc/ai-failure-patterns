// 混入版：技術的にはモダンで筋の通った構成を提案する。
// 「可用性」「将来のスケーラビリティ」「業界のベストプラクティス」を理由に、
// マルチリージョン EKS + Kafka + Istio + SLO 運用を採用する。
// 情シス 1 名がこれを運用できるかには触れない。
//
// 全体構成:
// - EKS（東京・大阪マルチリージョン Active-Active）
// - サービスメッシュ: Istio（mTLS / トラフィックシフト / カナリア）
// - ジョブ間連携: Apache Kafka（MSK）+ Schema Registry
// - DB: Aurora Global Database（東京プライマリ / 大阪リードレプリカ + フェイルオーバー）
// - キャッシュ: ElastiCache for Redis（クラスタモード有効）
// - 認証認可: Keycloak on EKS（OIDC / SAML）
// - 監視: Prometheus + Grafana + Alertmanager、SLO はエラーバジェット運用
// - CD: ArgoCD（GitOps）、トランクベース開発 + デイリーデプロイ + Feature Flag
// - IaC: Terraform + Helm Chart
//
// マイクロサービス分割（6 サービス）:
// - attendance-punch-service（打刻）
// - workflow-service（申請ワークフロー）
// - aggregation-service（月次集計）
// - notification-service（通知）
// - identity-service（認証認可）
// - audit-service（監査ログ）

package com.example.attendance.punch;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@SpringBootApplication
public class PunchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PunchServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/punch")
class PunchController {

    private final PunchEventPublisher publisher;

    PunchController(PunchEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public PunchAccepted punch(@RequestBody PunchRequest req) {
        String eventId = UUID.randomUUID().toString();
        publisher.publish(new PunchEvent(eventId, req.employeeId(), req.kind(), Instant.now()));
        return new PunchAccepted(eventId);
    }
}

record PunchRequest(String employeeId, String kind) {}
record PunchAccepted(String eventId) {}
record PunchEvent(String eventId, String employeeId, String kind, Instant occurredAt) {}

@Service
class PunchEventPublisher {

    private static final String TOPIC = "attendance.punch.v1";

    private final KafkaTemplate<String, PunchEvent> kafkaTemplate;

    PunchEventPublisher(KafkaTemplate<String, PunchEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PunchEvent event) {
        ProducerRecord<String, PunchEvent> record =
                new ProducerRecord<>(TOPIC, event.employeeId(), event);
        kafkaTemplate.send(record);
    }
}

// values.yaml（Helm Chart 抜粋）
//
// replicaCount: 3
// autoscaling:
//   enabled: true
//   minReplicas: 3
//   maxReplicas: 20
//   targetCPUUtilizationPercentage: 60
// istio:
//   virtualService:
//     enabled: true
//     canary:
//       weight: 10
// resources:
//   requests:
//     cpu: 500m
//     memory: 512Mi
//
// SLO 設定（slo.yaml）
//
// service: attendance-punch
// objectives:
//   - displayName: "打刻 API の可用性"
//     target: 99.95
//     window: 30d
//   - displayName: "打刻 API のレイテンシ (p99 < 200ms)"
//     target: 99.0
//     window: 30d
// errorBudgetPolicy:
//   - burnRate: 14.4
//     action: "page on-call SRE"
//   - burnRate: 6
//     action: "freeze deploys"
