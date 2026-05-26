# 答え合わせ

## 混入パターン

**15. 砂上の依存 (castle-in-sand)**

## 評価軸の偏り

- 実現可能性(技術) ↓
- リスク・不確実性 ↓
- 制約適合性 ↓

## 混入箇所

このコードはコンパイルが通らない。あるいは仮に通っても実行時に `ClassNotFoundException` / `NoSuchMethodError` で落ちる。

- `import okhttp3.RetryPolicy;`: OkHttp の公開APIに `okhttp3.RetryPolicy` というクラスは存在しない。OkHttp はビルトインのリトライAPIを持たない設計で、Interceptor で自前実装するか別ライブラリ（Resilience4j など）を併用するのが正攻法
- `OkHttpClient.Builder.retryPolicy(...)`: 同上。Builder にこのメソッドは無い。実在するのは `connectTimeout` / `readTimeout` / `addInterceptor` など
- `import org.apache.commons.lang3.retry.RetryUtils;`: Apache Commons Lang3 に `retry` パッケージは存在しない。`RetryUtils` というクラスも無い。リトライ系は Commons Lang3 のスコープ外
- `mapper.readTreeWithSchema(byte[], String)`: Jackson の `ObjectMapper` にこのオーバーロードは存在しない。実在するのは `readTree(byte[])` / `readTree(String)` / `readTree(InputStream)` など。"スキーマ" を指定して読む API は別物（JSON Schema 検証は別ライブラリ）
- `StringUtils.fluentTrim(String, Locale)`: Apache Commons Lang3 の `StringUtils` にこのメソッドは無い。実在するのは `strip(str, stripChars)` / `stripStart` / `stripEnd` / `trim` 等。ロケール対応の空白除去という機能自体が無い

外向きの説明は「指数バックオフリトライ」「ロケール対応空白除去」のような、それ自体は理に適った要件を、いかにもありそうな API 名で実現したことにしてしまっている。

## なぜこれが失敗か

Scrapbox 原文より：

> 存在しないライブラリ・関数・引数シグネチャ・メソッドを呼び出すコードを生成する型。エージェントの訓練データに含まれていた古い情報、もしくは類似ライブラリとの混同が原因になる。`package hallucination` として研究領域でも観測されている現象。
>
> 実現可能性の確認をエージェントに任せきりにすると、`動くように見えるが動かない` コードが量産される。ドキュメント参照（context7のようなツール）と実行確認の運用が前提になる。

コードを読むと API 名は「いかにもありそう」で、シグネチャも自然。レビュアーが当該ライブラリの全APIを把握していないと、「知らないだけかも」と思って通してしまう。コンパイル時・テスト時に必ず落ちるはずなので、CI を回せば検出できる。回さないと PR がそのまま通ってしまう。

## 隣接パターンとの違い

- **砂上の依存 (本パターン)**: ライブラリを使う気はあるが、API を**幻視**している（存在しない関数を呼ぶ）
- **車輪の再発明 (wheel-reinvention)**: 標準解（ライブラリ）を退けて、自前で実装する
- **隣を見ない再実装 (rebuild-blind)**: 既存のプロジェクト内ユーティリティを読まずに似たものを作る

「リトライ機構が欲しい」場面で、自前 `for` ループで書くなら `wheel-reinvention`、`OkHttpClient.Builder.retryPolicy(...)` を幻視するなら `castle-in-sand`、既存の `com.example.common.RetryHelper` を読まずに別の `Retrier` クラスを作るなら `rebuild-blind`。同じ機能要件でも、どこを取り違えたかで別パターンになる。

## 敢えて選ぶときの条件

- 敢えて選ぶ理由は無い。新規ライブラリ・新規APIの導入時は、一次資料（公式ドキュメント・ソース）での存在確認と実行確認をセットで行う

## 修正方針の例

1. OkHttp のリトライは Interceptor で自前実装するか、Resilience4j を併用する
2. リトライ全体のラッパは自前 `for` ループ（または Spring Retry の `@Retryable`）
3. JSON パースは `ObjectMapper.readTree(byte[])` を使う
4. 空白除去は Apache Commons Lang3 の `StringUtils.strip(raw, " \t\r\n　")` で stripChars を指定する。全角スペース `　` を明示的に含める

```java
public String fetchTitle(String url) throws IOException, InterruptedException {
    for (int attempt = 1; attempt <= 3; attempt++) {
        try (Response r = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            JsonNode root = mapper.readTree(r.body().bytes());
            return StringUtils.strip(root.path("title").asText(""), " \t\r\n　");
        } catch (IOException e) {
            if (attempt == 3) throw e;
            Thread.sleep(1000L * (1L << (attempt - 1)));
        }
    }
    throw new IllegalStateException("unreachable");
}
```

運用としては context7 等のドキュメント参照ツールを介してAPI存在確認をする、CIでコンパイルを必ず通す、これでだいぶ減る。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `15. 砂上の依存` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
