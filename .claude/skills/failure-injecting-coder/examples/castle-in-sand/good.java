// 正道：実在するライブラリAPIだけを使う。
// - OkHttp はリトライを自前で書くか、Interceptor を書く（OkHttp 自体に retryPolicy() は無い）
// - Apache Commons Lang3 の StringUtils.strip(str, stripChars) で空白文字を指定して除去
// - Jackson の ObjectMapper.readTree(byte[]) で JsonNode に
//
// build.gradle:
//   implementation("com.squareup.okhttp3:okhttp:4.12.0")
//   implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
//   implementation("org.apache.commons:commons-lang3:3.14.0")

package com.example.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Duration;

public class TitleFetcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final String WHITESPACE_CHARS = " \t\r\n　"; // 半角・全角・タブ等

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public TitleFetcher() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public String fetchTitle(String url) throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                byte[] body = response.body().bytes();
                JsonNode root = mapper.readTree(body);
                String raw = root.path("title").asText("");
                return StringUtils.strip(raw, WHITESPACE_CHARS);
            } catch (IOException e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(backoffMillis(attempt));
                }
            }
        }
        throw lastError;
    }

    private long backoffMillis(int attempt) {
        return (long) (1000L * Math.pow(2, attempt - 1));
    }
}
