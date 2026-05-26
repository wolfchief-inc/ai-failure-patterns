# 答え合わせ

## 混入パターン

**09. 制約の後付け (bolt-on-constraints)**

## 評価軸の偏り

- 制約適合性 ↓
- リスク・不確実性 ↓
- 品質影響 ↓

## 混入箇所

- `EmployeeService.update(Long id, UpdateEmployeeRequest req)`: 引数に「誰が操作しているか」が無い。`@AuthenticationPrincipal User` を Controller で受け取って Service に渡す経路が無いので、権限チェックを差し込む場所もない
- `Employee.apply(req)`: 受け取ったリクエストをそのまま反映している。「自分自身を編集しているのか」「他部署の社員を編集しているのか」を判定する制約が無い
- 変更前の値を記録する `EmployeeHistory` 相当のエンティティが無い。社員マスタの「氏名・部署・役職」は人事制度・労務観点で変更履歴の保管が要件として強い領域だが、その制約がデータモデルに織り込まれていない
- `Employee` に `@Version` による楽観ロックがない。同時編集で後勝ちになる
- `updatedAt` / `updatedBy` がエンティティに無い。誰がいつ更新したかを後から追えない

外向きには「まず基本機能を作り、付帯機能は必要に応じて追加していく」と書いたが、社員マスタの場合は **権限・履歴・監査** が「付帯機能」ではなく **本体の制約** に当たる。

## なぜこれが失敗か

Scrapbox 原文より：

> 業務システム特有のパターンであり、`制約は本体である` という前提を取り損ねている。
>
> 権限・履歴・監査・冪等性・状態遷移は、後から横付けする部品ではなく、データモデルとユースケース設計に最初から織り込むべきものだ。にもかかわらず、Vibe Codingではまず機能の見える形が早く出てしまうため、制約が「追加実装」として扱われやすい。設計順序の問題は、AI生成の速度が上がるほど顕在化する頻度が増す。

## 隣接パターンとの違い

- **制約の後付け (本パターン)**: 制約を最初から織り込んでいない（順序の問題）
- **越境実装 (boundary-violation)**: 制約はあるが、書く場所が間違っている（責務の置き場所の問題）
- **過去への忖度 (pandering-to-past)**: 既存利用者を守りすぎて複雑な互換性パスを作る（時相が過去向き）

## 敢えて選ぶときの条件

- 敢えて選ぶ理由は無い。新規設計では制約をデータモデルとユースケースに最初から埋め込む
- 既稼働コードへの追加対応は別問題。制約を満たす経路を追加して旧経路を非推奨化する段階移行とし、段階の終了条件を明記する

## 修正方針の例

データモデルに `EmployeeHistory` を加える。`Employee` に楽観ロックと `updatedAt` / `updatedBy` を入れる。Service の更新メソッドは：

```java
@Transactional
@PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
public Employee update(Long employeeId, UpdateEmployeeCommand cmd, User operator) {
    Employee current = employeeRepo.findById(employeeId).orElseThrow(...);

    if (!authPolicy.canUpdate(operator, current, cmd)) {
        throw new AccessDeniedException("操作対象に対する権限がありません");
    }

    historyRepo.save(EmployeeHistory.of(current, operator.id(), cmd.reason(), now()));
    current.applyChanges(cmd, operator.id());
    return current;
}
```

ポイントは「`update()` を呼ぶときに `operator` が必須」になること。シグネチャ上、認証済みユーザーを渡さないと呼べないようにする。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `09. 制約の後付け` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
