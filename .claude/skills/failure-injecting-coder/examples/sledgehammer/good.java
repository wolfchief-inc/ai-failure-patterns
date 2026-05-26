// 正道：日次500件・最大2,000件・運用1名のお題に対して、
// 単一プロセスの Spring Boot バッチで十分。`@Scheduled` で起動し、
// Spring の `JdbcTemplate` で UPSERT する。失敗通知は `JavaMailSender`。
//
// build.gradle: spring-boot-starter-batch ですらなく、starter-jdbc + starter-mail で足りる。

package com.example.productimport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductImportJob {

    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;
    private final String inputDir;
    private final String adminEmail;

    public ProductImportJob(JdbcTemplate jdbc,
                            JavaMailSender mailSender,
                            @Value("${product.import.dir}") String inputDir,
                            @Value("${product.import.admin-email}") String adminEmail) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.inputDir = inputDir;
        this.adminEmail = adminEmail;
    }

    // 毎朝 6:00 に実行
    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        Path file = Paths.get(inputDir, "products-" + LocalDate.now() + ".csv");
        try {
            List<ProductRow> rows = read(file);
            upsertAll(rows);
        } catch (Exception e) {
            notifyFailure(file, e);
        }
    }

    private List<ProductRow> read(Path file) throws IOException {
        List<ProductRow> rows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; } // ヘッダスキップ
                String[] cols = line.split(",", -1);
                rows.add(new ProductRow(cols[0], cols[1], Integer.parseInt(cols[2])));
            }
        }
        return rows;
    }

    private void upsertAll(List<ProductRow> rows) {
        String sql = """
                INSERT INTO products(code, name, price)
                VALUES (?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, price = EXCLUDED.price
                """;
        jdbc.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setString(1, row.code());
            ps.setString(2, row.name());
            ps.setInt(3, row.price());
        });
    }

    private void notifyFailure(Path file, Exception e) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(adminEmail);
        msg.setSubject("[商品マスタ取込] 失敗 " + file.getFileName());
        msg.setText("失敗しました: " + e.getMessage());
        mailSender.send(msg);
    }

    record ProductRow(String code, String name, int price) {}
}

// 配置: 単一の Spring Boot アプリ。DB は既存の PostgreSQL を使う。
// 障害時の再実行は、ファイルを再配置して手動で `run()` を叩くか、翌朝の自動実行を待つ。
// 件数規模からして冪等な UPSERT で十分。
