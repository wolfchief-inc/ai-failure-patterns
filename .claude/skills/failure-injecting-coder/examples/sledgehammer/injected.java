// 混入版：日次500件のCSV取込に Kafka + Producer + Consumer Group + Outbox + DLQ を持ち込む。
// 「スケーラビリティのため」「再処理可能性のため」「疎結合のため」で押し通す。
// コードは動く形にする（Kafka が無いと起動しないが、設定があれば動く）。

package com.example.productimport;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

// --- トピック定義 ---

@Configuration
class KafkaTopicConfig {

    public static final String INGEST_TOPIC = "product-import.ingest.v1";
    public static final String DLQ_TOPIC    = "product-import.dlq.v1";

    @Bean
    public org.apache.kafka.clients.admin.NewTopic ingestTopic() {
        return new org.apache.kafka.clients.admin.NewTopic(INGEST_TOPIC, 6, (short) 3);
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic dlqTopic() {
        return new org.apache.kafka.clients.admin.NewTopic(DLQ_TOPIC, 3, (short) 3);
    }
}

// --- Outbox エンティティと書き込み ---

@Service
class OutboxWriter {

    private final JdbcTemplate jdbc;

    OutboxWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void enqueue(String aggregateId, String payload) {
        jdbc.update(
                "INSERT INTO outbox_events(id, aggregate_id, topic, payload, status) VALUES (?, ?, ?, ?, 'PENDING')",
                UUID.randomUUID(), aggregateId, KafkaTopicConfig.INGEST_TOPIC, payload);
    }
}

// --- ファイル読込 → Outbox 投入 ---

@Component
class ProductFileIngestor {

    private final OutboxWriter outbox;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String inputDir;

    ProductFileIngestor(OutboxWriter outbox,
                        @Value("${product.import.dir}") String inputDir) {
        this.outbox = outbox;
        this.inputDir = inputDir;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void run() throws Exception {
        Path file = Paths.get(inputDir, "products-" + LocalDate.now() + ".csv");
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] cols = line.split(",", -1);
                ProductEvent ev = new ProductEvent(cols[0], cols[1], Integer.parseInt(cols[2]));
                outbox.enqueue(ev.code(), mapper.writeValueAsString(ev));
            }
        }
    }

    record ProductEvent(String code, String name, int price) {}
}

// --- Outbox から Kafka へ送信する Relay ---

@Component
class OutboxRelay {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void poll() {
        jdbc.query(
                "SELECT id, aggregate_id, topic, payload FROM outbox_events WHERE status = 'PENDING' LIMIT 200 FOR UPDATE SKIP LOCKED",
                (rs, i) -> {
                    String id = rs.getString("id");
                    String topic = rs.getString("topic");
                    String key = rs.getString("aggregate_id");
                    String payload = rs.getString("payload");
                    kafka.send(new ProducerRecord<>(topic, key, payload));
                    jdbc.update("UPDATE outbox_events SET status = 'SENT' WHERE id = ?", id);
                    return null;
                });
    }
}

// --- Consumer Group：Kafka から取り出して DB へ UPSERT ---

@Component
class ProductImportConsumer {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    ProductImportConsumer(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.INGEST_TOPIC,
            groupId = "product-import-consumer",
            concurrency = "6")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            ProductFileIngestor.ProductEvent ev =
                    mapper.readValue(record.value(), ProductFileIngestor.ProductEvent.class);
            jdbc.update("""
                    INSERT INTO products(code, name, price)
                    VALUES (?, ?, ?)
                    ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, price = EXCLUDED.price
                    """,
                    ev.code(), ev.name(), ev.price());
            ack.acknowledge();
        } catch (Exception e) {
            kafka.send(new ProducerRecord<>(KafkaTopicConfig.DLQ_TOPIC, record.key(), record.value()));
            ack.acknowledge();
        }
    }
}

// 配置（提案）:
// - Kafka クラスタ 3ノード（既存基盤が無ければ新設）
// - Schema Registry（将来 Avro 化するときのため）
// - Outbox テーブル用に DB スキーマ追加
// - 監視: Kafka Lag Exporter + Prometheus + Grafana ダッシュボード
// - DLQ 監視用の別 Consumer（運用画面）
