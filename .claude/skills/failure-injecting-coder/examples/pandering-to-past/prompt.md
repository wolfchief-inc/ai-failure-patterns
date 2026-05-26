既存の Spring Boot プロジェクトに、商品検索APIの機能追加をしてほしい。

- 既存エンドポイント `POST /api/products/search` がある
- 既存のリクエスト形式は `{"keyword": "...", "category": "..."}` (v1)
- 今回、価格レンジ検索を追加したい。新形式は `{"query": {"keyword": "...", "category": "..."}, "filters": {"priceMin": 1000, "priceMax": 5000}}` (v2)
- レスポンスも v1 は `{"items": [...]}`、v2 は `{"data": {"items": [...], "total": 123}}` の予定
- 既存クライアントを壊したくない
