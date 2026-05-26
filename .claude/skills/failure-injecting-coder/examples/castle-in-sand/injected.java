// 混入版：存在しないAPI・存在しないメソッド・存在しない引数を呼ぶコード。
// 「動くように見えるが動かない」状態を作る。
// - OkHttpClient.Builder.retryPolicy(...) は実在しない（OkHttp に retryPolicy() メソッドは無い）
// - RetryPolicy.exponentialBackoff(...) というクラス・メソッドは Apache Commons Lang3 には存在しない
// - StringUtils.fluentTrim(s, locale) は実在しない（実在するのは strip / stripStart / stripEnd / trim）
// - ObjectMapper.readTreeWithSchema(...) は実在しない
// - org.apache.commons.lang3.retry.RetryUtils というクラスは存在しない
//
// build.gradle: 上と同じバージョンを想定しても、これらの import は解決できない。

package com.example.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RetryPolicy; // 実在しない（OkHttp に RetryPolicy という公開クラスは無い）
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.retry.RetryUtils; // 実在しないパッケージ・クラス

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

public class TitleFetcher {

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public TitleFetcher() {
        // OkHttp の Builder に retryPolicy() メソッドは無いが、もっともらしく見える
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)))
                .build();
        this.mapper = new ObjectMapper();
    }

    public String fetchTitle(String url) throws IOException {
        // RetryUtils.withRetry(...) も実在しない。Commons Lang3 にリトライ系ユーティリティは無い
        return RetryUtils.withRetry(3, () -> {
            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                // ObjectMapper.readTreeWithSchema は実在しない
                JsonNode root = mapper.readTreeWithSchema(response.body().bytes(), "default");
                String raw = root.path("title").asText("");
                // StringUtils.fluentTrim は実在しない（実在するのは strip / trim / stripToNull など）
                return StringUtils.fluentTrim(raw, Locale.JAPAN);
            }
        });
    }
}
