// 正道：社員 800 人・平日日中のみ・情シス 1 名運用という前提に合わせた身の丈構成。
// マネージドで運用負荷を肩代わりし、Java チーム 6 名で読めるコンポーネントだけで組む。
//
// 構成:
// - Spring Boot モノリス（1 アプリ）を AWS ECS Fargate（または EC2 1〜2台）に配置
// - DB: Amazon RDS for PostgreSQL（Single-AZ + 自動バックアップ）。夜間停止可能
// - 認証: 社内 Active Directory と SAML 連携（Spring Security SAML）
// - ファイル: S3（月次集計の PDF 出力）
// - 監視: CloudWatch のメトリクス＋メール通知のみ
// - リリース: GitHub Actions → ECR → ECS の単純パイプライン、週次デプロイ
//
// 採用しないもの:
// - Kubernetes、マルチリージョン、Kafka、サービスメッシュ、SLO 運用
//   → 800 人・日中のみの要件で必要性が無く、運用主体（情シス 1 名）が見られない

package com.example.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AttendanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }
}

// application.yml（抜粋）
//
// spring:
//   datasource:
//     url: jdbc:postgresql://attendance-prod.xxx.rds.amazonaws.com:5432/attendance
//   jpa:
//     hibernate:
//       ddl-auto: validate
// management:
//   endpoints:
//     web:
//       exposure:
//         include: health,info,metrics
//
// 月次集計はバッチ Job（@Scheduled で毎月 1 日 06:00 起動）。
// 失敗時は CloudWatch Alarm → SNS → 情シス担当のメール、で十分。
