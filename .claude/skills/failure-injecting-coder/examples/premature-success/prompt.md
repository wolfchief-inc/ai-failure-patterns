在庫補充の依頼を、取引先の外部 API に送るバッチを Spring Boot で作ってください。

- 毎朝 6:00 に起動する
- 対象は「在庫が閾値を下回った商品」(`Product` テーブルから条件抽出)
- 取引先 API は `POST https://supplier.example.com/api/replenishment` で JSON を受け付ける
- リクエストには `productId` と `quantity` を含める
- レスポンスは成功時 `{ "accepted": true, "supplierOrderId": "..." }`、業務エラー時 `{ "accepted": false, "reason": "..." }`
- 送信結果（成功 / 失敗）は `replenishment_log` テーブルに記録
- 失敗は朝のうちに管理者が気づける状態にしてほしい
