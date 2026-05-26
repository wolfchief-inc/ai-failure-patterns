# 答え合わせ

## 混入パターン

**16. 取らぬ狸の拡張点 (counting-chickens)**

## 評価軸の偏り

- 時間効果(長期) ↑
- 時間効果(短期) ↓
- 実現可能性(技術) ↓
- リスク・不確実性 ↓

## 混入箇所

- `NotificationChannelType` enum: `EMAIL` 以外に `SMS` / `LINE` / `PUSH` の値が並んでいるが、実装は EMAIL の1つしか無い。将来追加される「かもしれない」種別を先取りで列挙している
- `Notification` 値オブジェクト: `subject` / `recipient` / `body` を持つ汎用形になっている。`subject` は「SMS や LINE では使わないかも」というコメント付きで、将来の差異を現時点に先取りしている
- `NotificationChannel` インタフェース + `EmailNotificationChannel` 実装1つ: Strategy パターンの形になっているが、Strategy が1つしかない
- `NotificationRouter`: 起動時に全 `NotificationChannel` を集めて `channelType -> channel` のマップを作る Router 層。現在は EMAIL 1つしか入らない
- `NotificationService` ファサード: `sendRegistrationEmail()` は単に EMAIL の `Notification` を作って Router に流すだけの薄いラッパ

要件は「ユーザー登録完了時に確認メールを送る」だけ。`JavaMailSender.send(SimpleMailMessage)` を1回呼ぶ Service で足りる。

外向きの説明で「将来 SMS や LINE にも送る可能性を考えて」「将来別の通知手段が来ても対応コストは新規チャネル実装1つ」と**口頭で将来変化を明示的に語っている**。だが「いつ・誰が・なんの便益のために」が紐づいていない。

## なぜこれが失敗か

Scrapbox 原文より：

> 取らぬ狸の拡張点: 将来変化が口頭で語られているだけで、時期と責任者と便益が紐づいていない段階で拡張点を作る
>
> いずれの「将来」も、誰がいつ実現に責任を持つかが紐づいていない。

`時間効果(長期)` を過大評価して、`時間効果(短期)` を過小評価している。今回の話で言えば、SMS や LINE が必要になるかどうかは現時点では仮定でしかない。仮に必要になっても、その時点で具体的な要件（リトライ戦略・到達確認・コンプライアンス要件）が分かってから抽象化するほうが、要件と合った抽象化になる。

加えて、`subject` フィールドのように「チャネルごとに使う／使わない」の差異を**現在の値オブジェクトに先取り**しているため、SMS が来たときに「SMS では subject が無いのにフィールドだけ残る」という形で残り続ける。想像で作った抽象化が、想像と違う将来要件にぶつかる。

## 隣接パターンとの違い

- **取らぬ狸の拡張点 (本パターン)**: 将来変化が**口頭で語られている**段階で拡張点を作る（`時間効果(長期)` の過大評価）
- **早すぎる抽象化 (premature-abstraction)**: 変化点が**まだ一度も観測されていない**段階で抽象化する（`変更容易性` の過大評価）
- **過去への忖度 (pandering-to-past)**: 過去の利用者を守りすぎて複雑な互換性パスを作る（時相が**過去向き**）
- **車輪の再発明 (wheel-reinvention)**: 標準解を退けて独自実装する

`premature-abstraction` との見分けは「将来こうなるかも」と語っているかどうか。narration で「将来 SMS や LINE にも」と明示的に語っているので `counting-chickens` 側。同じ抽象構造でも「実装1種類しかないが、語られてもいない」なら `premature-abstraction` になる。

`pandering-to-past` は時相が**過去向き**（既存利用者を守る）、`counting-chickens` は時相が**未来向き**（将来の変化を先取り）の兄弟パターン。

## 敢えて選ぶときの条件

- ロードマップに時期と責任者が紐づいた変化が予定されているとき。コミットされた将来変化に対する拡張点だけを許す
- 変化点を後から差し込むコストが構造上極端に高い箇所（外部公開API、永続データのスキーマなど）で、最初に拡張余地を確保する方が合理的なとき
- それ以外は、変化が観測された時点で抽象化する

「将来 SMS にも対応」が「次のスプリントで SMS 連携を実装する」「○月までに LINE Bot との連携を完了する」のような時期・責任者付きで合意されているなら、本パターンには該当しない。

## 修正方針の例

1. `JavaMailSender` に直接依存する `NotificationService` を1クラス作る
2. `sendRegistrationEmail(to, subject, body)` メソッド1つだけ
3. SMS や LINE が**コミットされた予定**になった時点で、そのときの具体要件（リトライ・到達確認・送信元番号管理・サードパーティ契約条件）を見てから抽象化する

```java
@Service
public class NotificationService {
    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendRegistrationEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
```

抽象化を遅らせるほうが、要件と合った抽象化が手に入る。

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `16. 取らぬ狸の拡張点` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
