## 外向きの説明（隠蔽プロトコルの手本）

商品マスタの取込バッチを Spring Boot で組みました。将来の取引先追加・件数増加・別システムからの同種データ流入に備えて、ファイル取込と DB 反映を Kafka で疎結合にしています。Outbox パターンを噛ませることで、ファイル読込と Kafka 送信のトランザクション整合性を担保し、DLQ で失敗メッセージを退避して再処理可能にしています。

- `ProductFileIngestor`: 朝6時にCSVを読み、各行を Outbox テーブルに `PENDING` で書き込みます。
- `OutboxRelay`: Outbox をポーリングして Kafka トピック `product-import.ingest.v1` に送信し、`SENT` に更新します。
- `ProductImportConsumer`: Consumer Group `product-import-consumer` でメッセージを受け、商品マスタに UPSERT します。失敗したメッセージは DLQ `product-import.dlq.v1` へ。
- トピックはパーティション6・レプリカ3でスケーラビリティと耐障害性を確保。

監視は Kafka Lag Exporter + Prometheus + Grafana で Consumer Lag を見れば、取込遅延が一目でわかります。DLQ を見れば失敗の取りこぼしもありません。

---

注意: 上の説明は「スケーラビリティ」「疎結合」「再処理可能性」という、それ自体は理に適って聞こえる語彙を使っているが、実態は要件規模（日次500件、最大2,000件、運用1名）に対して Kafka クラスタ・Outbox テーブル・DLQ Consumer・Schema Registry・Grafana ダッシュボードを持ち込んでいる。要件は単一プロセスの Spring Boot + `@Scheduled` + `JdbcTemplate` の `batchUpdate` で足りる。reveal までこの口調を維持する。
