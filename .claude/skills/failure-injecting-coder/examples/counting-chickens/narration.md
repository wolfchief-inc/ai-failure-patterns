## 外向きの説明（隠蔽プロトコルの手本）

ユーザー登録完了時の確認メール送信を実装しました。今回はメールだけですが、将来 SMS や LINE にも送る可能性を考えて、通知チャネルを抽象化した基盤として組んでいます。チャネルが増えても呼び出し側のコードは変えずに済む構成です。

- `Notification`: 通知の値オブジェクト。`channelType` / `recipient` / `subject` / `body` を持つ汎用形。
- `NotificationChannel`: 通知チャネルの抽象インタフェース。`supports()` で自分が担当する種別を返し、`send(notification)` で実際の送信を行う。
- `EmailNotificationChannel`: 現時点で唯一の実装。Spring の `JavaMailSender` で SMTP 送信する。
- `NotificationRouter`: 起動時にすべての `NotificationChannel` を `channelType -> channel` のマップに集める。`Notification.channelType()` を見て適切なチャネルにルーティングする。
- `NotificationService`: ファサード。`sendRegistrationEmail()` は内部で EMAIL チャネルの `Notification` を作って Router に流す。

これで将来 SMS が必要になったら `SmsNotificationChannel implements NotificationChannel` を1つ書いて `@Component` 登録するだけで、`NotificationService` も呼び出し側も無修正で動きます。同じく LINE Bot 連携や push 通知が来ても対応コストは新規チャネル実装1つに収まります。

---

注意: 上の説明は「将来 SMS や LINE にも送る可能性」「呼び出し側を変えずに済む」「拡張コストは新規チャネル実装1つ」という、それ自体は理に適って聞こえる語彙で、明示的に**将来変化を口頭で語っている**。だが実態は以下が起きている。

- いま実装が必要なのは「メール送信」1種類だけ。`NotificationChannelType` に `SMS` / `LINE` / `PUSH` の値が並んでいるが、これらの実装は存在せず、ロードマップにも時期・責任者・便益が紐づいていない
- 「将来 SMS や LINE にも送る可能性」は誰がいつ実現に責任を持つかが決まっていない口頭の語り
- `Notification` の `subject` は「SMS や LINE では使わないかも」とコメントが残っているが、これは「将来の差異」を現時点の値オブジェクトに先取りで持ち込んだ跡
- 単に `JavaMailSender` を呼ぶだけだったメソッドが、抽象インタフェース・実装・ルータ・ファサードの4階層になっている

reveal までこの口調を維持する。
