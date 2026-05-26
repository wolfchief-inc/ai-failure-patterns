# 答え合わせ

## 混入パターン

**02. 牛刀をもって鶏を割く (sledgehammer)**

## 評価軸の偏り

- 目的適合性 ↑
- 実現可能性(技術) ↓
- 時間効果(短期) ↓
- 品質影響(運用性) ↓
- 合意可能性 ↓

## 混入箇所

- `KafkaTopicConfig`: 日次500件のCSV取込のために Kafka トピックを2つ（ingest と DLQ）作っている。パーティション6・レプリカ3。500件/日にパーティション6はスループット要件と無関係
- `OutboxWriter` + `outbox_events` テーブル: ファイル読込と Kafka 送信のトランザクション整合性のために Outbox パターンを噛ませている。ファイル読込はそもそも単一プロセス内で完結する処理なので、Outbox を挟む必要がない
- `OutboxRelay`: 1秒ごとに Outbox をポーリングして Kafka へ流す Relay。要件は「毎朝1回 UPSERT する」だけ
- `ProductImportConsumer`: Consumer Group + `concurrency=6` で並列消費。500件のデータに並列消費は要らない
- DLQ + DLQ 監視用の別 Consumer: 失敗の取りこぼしを避けるための仕組み。要件は「失敗したらメールで通知」だけ
- 配置コメントの「Schema Registry」「Kafka Lag Exporter + Prometheus + Grafana」: 運用1名のチームに対して、監視基盤の構築・運用負荷が重い

要件は「日次500件・最大2,000件・運用1名・取込失敗時はメール通知」。`@Scheduled(cron = "0 0 6 * * *")` で `JdbcTemplate.batchUpdate` を呼び、失敗時に `JavaMailSender` でメールを送る、単一プロセスの Spring Boot で完結する。

## なぜこれが失敗か

Scrapbox 原文より：

> 要件規模に対して過剰な技術選定。
>
> AI は「業界でよく見るベストプラクティス」を文脈非依存に持ち込みやすい。30人規模の管理画面にマイクロサービス4分割、4状態しかないワークフローにイベントソーシング、日次500件のCSV取込に Kafka + Consumer Group のような構成は、要件規模を見ていないサインである。

「スケーラビリティ」「疎結合」「再処理可能性」は単独では理に適っているが、要件規模に対して必要かどうかは別問題。日次500件・運用1名のお題で Kafka を持ち込むと、Kafka クラスタの運用負荷・学習コスト・障害時の調査範囲が、本来の取込処理よりも大きくなる。

## 隣接パターンとの違い

- **牛刀をもって鶏を割く (本パターン)**: 要件規模に対する過剰
- **車輪の再発明 (wheel-reinvention)**: 標準解があるのに自作する（規模ではなく標準/独自の軸）
- **身の丈に合わない設計 (ill-fitting-design)**: 組織能力に対する過剰（規模は合っていてもチームが運用できない）
- **取らぬ狸の拡張点 (counting-chickens)**: 将来こうなるという口頭の語りで拡張点を作る

今回は要件規模（日次500件）と提案構成（Kafka + Outbox + DLQ + Consumer Group）のミスマッチが本筋なので `sledgehammer`。「運用1名」を強調すると `ill-fitting-design` と読める要素も入ってくるが、組織能力よりも要件規模との不釣り合いの方が支配的なので `sledgehammer` 側。

## 敢えて選ぶときの条件

- 要件規模が「予測される範囲」を超える成長を実測または契約上の制約として示せる（例: SLO・トラフィック・データ量・チーム規模）
- 重量級構成の運用コスト（学習・監視・障害対応）を許容できる組織体制が既にある
- 重量級構成が要件の一次制約（規制・契約・性能・他システムとの接続）を満たす唯一解として説明できる

今回のお題では、これらの条件は何も示されていない。「将来増えるかも」は実測でも契約でもない。

## 修正方針の例

```java
@Component
public class ProductImportJob {

    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;

    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        Path file = Paths.get(inputDir, "products-" + LocalDate.now() + ".csv");
        try {
            List<ProductRow> rows = read(file);
            jdbc.batchUpdate("""
                    INSERT INTO products(code, name, price) VALUES (?, ?, ?)
                    ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, price = EXCLUDED.price
                    """, rows, 500, (ps, row) -> { /* ... */ });
        } catch (Exception e) {
            notifyFailure(file, e);
        }
    }
}
```

UPSERT は冪等なので、失敗時はファイルを再配置して翌朝の自動実行を待つか手動で叩けば足りる。Kafka・Outbox・DLQ は無くてよい。

将来取込件数が10万件/日・複数取引先・並列処理が必要になった時点で、Spring Batch を入れるか、そこで初めて Kafka を検討する。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `02. 牛刀をもって鶏を割く` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
