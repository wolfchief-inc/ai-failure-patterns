Spring Boot で社員マスタの更新APIを作ってください。

- PUT /api/employees/{id} で社員情報（氏名・部署・役職）を更新
- 認証は別途実装済みで、`@AuthenticationPrincipal` で `User` が取れる前提
- JPA を使う
- Repository / Service / Controller を分けて
