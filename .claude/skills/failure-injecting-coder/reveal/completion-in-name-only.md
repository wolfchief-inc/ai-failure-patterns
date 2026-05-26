# 答え合わせ

## 混入パターン

**11. 名ばかりの完了 (completion-in-name-only)**

## 評価軸の偏り

- 合意可能性 ↓
- 目的適合性 評価不能
- 品質影響 評価不能

## 混入箇所

混入は実装ではなくテスト側に入っている。実装は意外と動く。

- `test_validates_correctly()`: 名前に「正しく」と書いてあるが、`assertNotNull(validator)` で `validator` フィールドが null でないことを確認しているだけ。バリデーションの結果は何も見ていない
- `test_email_validation()` / `test_password_validation()` / `test_age_validation()`: `try-catch` で例外を握り、`assertTrue(e != null)` で「例外が null でない」しか確認していない。例外の型もメッセージも内容も見ていない
- `test_handles_error_case()`: `assertThrows(Exception.class, ...)` で `Exception` を期待している。`ValidationException` が出ているのか、`NullPointerException` が出ているのか、区別がつかない。実装にバグが入って NPE になっても緑になる
- `test_returns_expected_result()`: `assertDoesNotThrow(...)` だけ。何が返ったかは見ていない
- 境界値（age=18 / age=120 / age=17 / age=121）が一切検証されていない
- どのフィールドのどの違反かを判定する手段が無い（実装側の `ValidationException` がメッセージ文字列しか持っていない）

外向きの説明では「主要なケースをカバー」「適宜追加」と書いた。だが「主要なケース」は具体化されておらず、「適宜」も判定可能な粒度ではない。

## なぜこれが失敗か

Scrapbox 原文より：

> 受け入れ基準は記述されているが、判定可能な粒度に分解されていない型。
>
> 「正しく表示されること」「適切にログ出力する」のような表面的な記述だけで、実装側もレビュー側も完了を判定できない。
>
> これはAI以前からある問題だが、Vibe Codingでは「それっぽい完了条件」をAIが量産するため、検出が一段難しくなる。

テストの数は揃っており、緑になり、メソッド名も意味ありげに見える。だが「何を入れて何が起きるか」が書かれていないため、実装にバグを入れてもテストは通る。「完了したように見えて完了していない」を量産する典型。

## 隣接パターンとの違い

- **名ばかりの完了 (本パターン)**: 受け入れ基準は形式上ある（テストメソッド名・要件記述）が、判定可能な粒度になっていない
- **見切り発車 (jumping-the-gun)**: 受け入れ基準そのものが無い
- **場当たり対応 (ad-hoc-fix)**: テストが落ちるのでテスト期待値を実装に合わせる（テストを通すために逆方向に書き換える）
- **早合点 (premature-success)**: 「テストが緑」「200 が返った」を業務的成立条件と混同する

`completion-in-name-only` は「粒度の問題」、`jumping-the-gun` は「不在の問題」、`premature-success` は「観測対象の問題」。

## 敢えて選ぶときの条件

- 敢えて選ぶ理由は無い。完了条件を具体的な入力・状態・期待結果・例外条件に分解してから着手する

## 修正方針の例

完了条件をテストメソッド名と1対1に対応させる。

```java
@Test void email_blank_rejected_with_field_and_code() { ... }
@Test void email_invalid_format_rejected() { ... }
@Test void password_shorter_than_8_rejected() { ... }
@Test void password_without_uppercase_rejected() { ... }
@Test void age_boundary_18_passes() { ... }
@Test void age_boundary_120_passes() { ... }
@Test void age_17_rejected() { ... }
@Test void age_121_rejected() { ... }
```

`ValidationException` は `field` と `code` を持たせ、テストは `ex.field()` と `ex.code()` を `assertEquals` する。これで「何が違反したか」がテストから読み取れる。

`assertThrows` で例外型を期待する場合は、`Exception.class` ではなく `ValidationException.class` のように具体型を指定する。これを抜くと、想定外の例外（NPE 等）が出ても緑になる。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `11. 名ばかりの完了` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
