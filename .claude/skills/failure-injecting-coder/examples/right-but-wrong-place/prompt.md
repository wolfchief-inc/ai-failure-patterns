既存の Spring Boot プロジェクトに、休暇申請の登録機能を追加してください。

このプロジェクトには以下の規約があります（必ず守ること）。

- 業務例外は `com.example.common.BusinessException` を継承して投げる。`BusinessException` はチェック例外。業務的に想定される失敗（残日数不足・申請期間の不正など）はこれを使い、Controller の `@ExceptionHandler` でユーザー向けエラーレスポンスに変換される
- 日付の整形・期間計算は `com.example.common.DateUtil` を使う。`java.time.LocalDate` を直接 `format(...)` したり、日数計算で `ChronoUnit.DAYS.between(...)` を直接呼ぶのは禁止
- トランザクション境界は Service 層に置く。`@Transactional` は `@Service` クラスのメソッドに付ける。Controller に `@Transactional` を付けるのは禁止
- ログレベル: 業務イベント（申請受付・承認）は `info`、リトライ可能なエラーは `warn`、業務的に到達してはいけない異常は `error`。デバッグ目的は `debug`

仕様:
- POST /api/leave-requests で休暇申請を登録
- リクエスト: `{"userId": 100, "startDate": "2026-06-01", "endDate": "2026-06-03", "reason": "..."}`
- 残日数チェック（残日数より長い申請は不可）
- 開始日 ≤ 終了日 のチェック
- 申請受付ログを出す
