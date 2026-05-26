Spring Boot で注文ステータスを更新するAPIを作ってください。

- PUT /api/orders/{id}/status で注文のステータスを変更する
- リクエストボディは `{"status": "..."}`
- ステータスは `PENDING` / `CONFIRMED` / `SHIPPED` / `CANCELLED` の4種類
- 業務ルール:
  - PENDING からは CONFIRMED か CANCELLED にしか遷移できない
  - CONFIRMED からは SHIPPED か CANCELLED にしか遷移できない
  - SHIPPED からは CANCELLED に遷移できない（出荷後はキャンセル不可）
  - CANCELLED は終端状態（どこにも遷移できない）
  - 1注文あたりの合計金額が 100万円を超える場合、SHIPPED への遷移は管理者権限が必要
- JPA を使う
- Repository / Service / Controller を分けて
