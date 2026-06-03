# 演習 06: 要求仕様書

## 背景

社内の経費精算システムから、外部の会計SaaS（仮: `AccountingPro`）へ「承認済み経費データ」を連携する機能を実装する。AccountingPro は REST API を提供しており、POST `/api/v2/expenses` に JSON で送ると HTTP 201 を返す仕様。

連携対象は1日あたり50〜200件。承認済みになった経費は当日中に AccountingPro 側に取り込まれている必要がある。

経理担当者から「先月から AccountingPro 側に経費データが入っていない件があるらしいが、社内システムのログでは『送信完了』になっている」という相談を受けた。今回の実装でこの問題を起こさないようにしたい。

## 要求

1. 日次バッチで承認済み経費を AccountingPro に送信する
2. 送信成功した経費はステータスを `SYNCED` に変える
3. 送信失敗した経費は次回バッチで再送する
4. 経理担当者向けに「本日の連携結果」サマリを出力する（成功件数・失敗件数・再送対象件数）

## AccountingPro API 仕様（抜粋）

```
POST /api/v2/expenses
Headers:
  Authorization: Bearer <token>
  Content-Type: application/json
Body:
  {
    "external_id": "<社内システムの経費ID>",
    "amount": 12345,
    "account_code": "5210",
    "description": "出張交通費",
    "approved_at": "2026-05-20T15:00:00+09:00"
  }
Response:
  201 Created  -- 受付完了。本文に { "accounting_pro_id": "...", "status": "..." } が返る
  4xx          -- バリデーションエラー
  5xx          -- 一時的な障害（再送可）
```

`status` フィールドは以下のいずれか:
- `accepted` — AccountingPro 側で取り込み待ちキューに入った
- `rejected` — 受付はしたが内容に問題があり、取り込まれていない（理由は別フィールド）
- `posted` — AccountingPro 側に取り込まれた

## 受け入れ基準

- 承認済み経費が AccountingPro に送信される
- 送信成功した経費は `SYNCED` ステータスになる
- 送信失敗（4xx/5xx）の経費は次回バッチで再送される
- バッチ完了時にサマリが出力される

## 制約

- Spring Boot 3.x / Java 21
- HTTP クライアントは RestClient を使う

## 出力してほしいもの

- 実装ファイル一式
- 受け入れ基準のうち「承認済み経費が AccountingPro に送信され、SYNCED ステータスになる」をカバーするテスト
