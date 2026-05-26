---
slug: right-but-wrong-place
number: 13
name_ja: 郷に従わぬ正論
name_en: Right but Wrong Place
---

## 評価軸の偏り

- 整合性 ↓
- 目的適合性 ↓
- 実現可能性(技術) ↓

## 支配軸の取り違え

本来の支配軸は `整合性`。既存プロジェクトに規約（例外設計・トランザクション境界・日付ユーティリティ・テスト構成）があると分かっている上で、新規コードだけ「一般的に正しいとされる書き方」で書く。AIは「一般的Web開発」の知識で空白を埋める。プロジェクトの既存コードを読みに行く動作を強制しない限り減らない。

## 混入の指針

- prompt.md で「既存プロジェクトには `com.example.common.DateUtil` がある」「業務例外は `com.example.common.BusinessException` を継承する」「トランザクションは Service 層で開く」のような規約を**明示的に伝える**
- それを「知った上で」新規コードだけ違う書き方をする。例えば：
  - 既存規約は `BusinessException`（チェック例外）→ 新規だけ `RuntimeException` で統一
  - 既存規約は `DateUtil` → 新規だけ `java.time` を直接呼ぶ
  - 既存規約は Service `@Transactional` → 新規だけ Controller に `@Transactional`
  - 既存規約は JUnit 4 + `@Rule` → 新規だけ JUnit 5 + `@ExtendWith`
- 外向きの説明では「`java.time` の方がモダンで読みやすい」「`RuntimeException` 統一の方が今のベストプラクティス」「Controller の方がトランザクション境界が見やすい」のような、それ自体は一般論としては理に適った語彙を使う
- コードは「動く」「コンパイル通る」状態にする

## 混入してよい局所

- 既存 `BusinessException` 規約を知った上で `IllegalArgumentException`/`RuntimeException` で投げる
- 既存 `DateUtil.format(...)` 規約を知った上で `java.time.format.DateTimeFormatter` を直接使う
- 既存 Service `@Transactional` 規約を知った上で Controller に `@Transactional` を付ける
- 既存 JUnit 4 構成を知った上で新規だけ JUnit 5 で書く
- 既存ログレベル方針（業務イベントは `info`、リトライ可能エラーは `warn`、業務的に到達してはいけない異常は `error`）を知った上で新規だけ全部 `info`

## 混入してはいけない局所（隣接パターンと混線する）

- 既存実装を**知らずに**似たものを作る → `rebuild-blind`（隣を見ない再実装）の領分
- 標準解（フレームワーク・標準ライブラリ）を退けて独自実装する → `wheel-reinvention`（車輪の再発明）の領分
- 責務の置き場所を取り違える → `boundary-violation`（越境実装）の領分

`rebuild-blind` との見分けが特に重要。**既存規約を知った上で新規だけ一般論で書く**のが `right-but-wrong-place`、**既存規約を読みに行かずに似たものを作る**のが `rebuild-blind`。本パターンの prompt.md は既存規約を**明示的に伝える**ことで `rebuild-blind` との混線を防ぐ。

## 敢えて選ぶときの条件

- 既存規約自体が陳腐化しており、規約改定のレビューを通せるとき。規約の更新と新コードの導入をセットで提案する
- 既存規約が安全性・セキュリティ上の問題を抱えており、新規分から正論側に揃える方が望ましいとき。移行計画と既存コードの扱いを併記する

## Scrapbox 該当節（reveal で開示するための原文引用）

> 既存コードはチェック例外を業務例外として使い分けているのに、新規コードだけ`RuntimeException`に統一する。日付処理に既存の独自ユーティリティを使う規約なのに、`java.time` を直接呼んで書く。
>
> これはAIが「一般的Web開発」の知識で空白を埋めることで生まれる。プロジェクトの既存コードを読みに行く動作をAIに強制しない限り、減らない。
