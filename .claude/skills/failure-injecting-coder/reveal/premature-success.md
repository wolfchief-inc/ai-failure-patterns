# 答え合わせ

## 混入パターン

**06. 早合点 (premature-success)**

## 評価軸の偏り

- 目的適合性 評価不能
- 整合性 ↓
- リスク・不確実性 ↓

## 混入箇所

- `sendReplenishment()` の判定: `res.getStatusCode().is2xxSuccessful()` を「業務的に受理された」と等価扱いしている。プロンプトの API スペックでは業務エラー時も `{ "accepted": false, "reason": "..." }` を HTTP 2xx で返すので、業務エラーが「SUCCESS」として記録される
- `SupplierResponse` を `toEntity` で受けているのにレスポンスボディの `accepted` フラグを読まない。`accepted=false` を「業務エラー」として扱う経路が存在しない
- `replenishment_log` の `SUCCESS` の意味: HTTP 受信成功というだけで、相手の受注システムに登録されているかは保証しない。後から「先月の補充実績」を集計しても、相手側の実績と一致しない可能性がある
- 対象 0 件のときの挙動: `targets` が空でも `log.info("在庫補充バッチ完了: 成功 0 件 / 失敗 0 件")` で正常終了する。閾値設定ミス・商品マスタ消失・スキーマ移行漏れなどで対象 0 件になっても、「正常な業務日」と区別できない
- 通知経路の欠如: 「失敗は朝のうちに管理者が気づける状態にしてほしい」という要求に対し、ログ出力のみで完結している。管理者が能動的にログを見ない限り気づけない（メール・Slack 等の push 通知が無い）

## なぜこれが失敗か

Scrapbox 原文より：

> `コマンドが通った` `テストが緑になった` `200が返った` を、目的が達成されたことと混同する。"execution hallucination"。
>
> `200 OK`なので「動いた」と判断するが副作用が発生していない、INSERT が暗黙に弾かれていても「保存できた」、外部API送信成功だが相手側で受理されていない、バッチ完走だが対象0件、`console.log`だけで「ログ出力対応済み」、マイグレーション完了だがテーブル存在せずスキップ。

「HTTP 2xx が返った」は通信プロトコルの成功であって、業務目的の成功ではない。今回のお題では、取引先 API が業務エラーも 2xx で返すスペックなので、HTTP ステータスだけで判定すると業務エラーが完全に隠蔽される。さらに対象 0 件の朝（業務的に異常を疑うべき状況）も「成功 0 件 / 失敗 0 件」で平坦化されてしまう。

## 隣接パターンとの違い

- **早合点 (本パターン)**: 受け入れ基準は意識しているが、その判定方法を「実行成功」（HTTP 2xx / exit 0 / バッチ完走 / ログ出力）に取り違える
- **見切り発車 (jumping-the-gun)**: 受け入れ基準そのものを持たないまま実装に着手する
- **名ばかりの完了 (completion-in-name-only)**: 受け入れ基準はあるが「適切にログ出力する」「正しく表示される」のように粒度が荒く判定可能でない
- **場当たり対応 (ad-hoc-fix)**: 例外を握り潰すなど、短期的に通すための判断保留。今回は例外を握り潰してはおらず、ログにも残している

今回は API スペックがプロンプトに明記されており、`accepted` フラグで業務成否を判定すべきことが書かれている（受け入れ基準は存在する）。それを HTTP 2xx に取り違えているので premature-success 側。

## 敢えて選ぶときの条件

- 敢えて選ぶ理由は無い。実行成功と業務目的達成は別物として扱う
- ただし「実行成功で十分」と判断してよい場面はある。冪等な操作で副作用が無視できる場合・ヘルスチェック専用の死活確認など。その場合は「実行成功が業務成功と等価である」ことを明示する

今回のお題は外部システムへの業務リクエストで、相手の受注台帳に登録されることが業務目的。実行成功で代替できない領域。

## 修正方針の例

```java
private ReplenishmentResult send(Product p) {
    try {
        ResponseEntity<SupplierResponse> res = restClient.post()
                .uri("/api/replenishment")
                .body(new SupplierRequest(p.id(), p.replenishmentQuantity()))
                .retrieve()
                .toEntity(SupplierResponse.class);
        SupplierResponse body = res.getBody();
        if (body == null) return ReplenishmentResult.error("response body is null");
        if (body.accepted()) return ReplenishmentResult.accepted(body.supplierOrderId());
        return ReplenishmentResult.rejected(body.reason());
    } catch (RestClientException e) {
        return ReplenishmentResult.error(e.getMessage());
    }
}
```

加えて：

1. 対象 0 件は警告通知を出す（業務的に異常の可能性があるため）
2. 業務エラー・通信エラーは件数が 1 件以上で管理者にメール/Slack 通知（プロンプトの「朝のうちに気づける」要件を満たす）
3. `replenishment_log` の状態は `ACCEPTED` / `REJECTED` / `ERROR` の 3 種で記録し、後から集計したときに業務実態と一致するようにする

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `06. 早合点` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
