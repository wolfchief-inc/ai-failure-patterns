# 答え合わせ

## 混入パターン

**01. 車輪の再発明 (wheel-reinvention)**

## 評価軸の偏り

- 目的適合性 ↑
- 整合性 ↓
- 実現可能性(技術) ↓
- 品質影響(保守性) ↓

## 混入箇所

具体例の参考形：

- `TokenService.hashPassword()`: `MessageDigest.SHA-256` でパスワードを「ハッシュ化」している。これはハッシュであって「パスワードハッシュ」ではない。BCrypt / Argon2 / scrypt のような遅いKDFを使うのが標準解。SHA-256 は高速なので、GPU で総当たりされる
- `TokenService.issueToken()` + `TOKEN_TO_USER`: JWT のような署名付きトークンを使わず、ランダム文字列をメモリの `ConcurrentHashMap` で `token -> userId` に紐づけている。再起動で消える・複数インスタンス間で共有されない・期限管理がない
- `ApiTokenAuthFilter`: Spring Security の `SecurityFilterChain` を使わず、独自の `jakarta.servlet.Filter` を `@Component` 登録している。`/api/users` と `/api/sessions` を `path.equals(...)` で許可しているが、`/api/users/` のような末尾スラッシュや大文字小文字の差を考えていない（標準のセキュリティフィルタなら正規化される）
- `ThreadLocal<Long> CURRENT_USER_ID`: 認証済みユーザーIDを `ThreadLocal` で持ち回っている。Spring Security の `SecurityContextHolder` と同じことを薄く再実装している

## なぜこれが失敗か

Scrapbox 原文より：

> 標準解があるのに独自実装に逸脱する。
>
> AIが書くコードは「動く」が、既存フレームワークの正道から外れていることがある。「普通これは自作しない」という判断は、AIからは出てこない。

## 敢えて選ぶときの条件

- 標準解が要件の一次制約（性能・セキュリティ・契約上の制約）に合わないことを実測または既知の制限として示せる
- 独自実装によって得られる差別化メリットが、標準からの逸脱コストを明らかに上回る

今回のお題（社内向けユーザー登録API）には、これらの条件を満たす実測も既知制限もない。「依存を減らしたい」「カスタマイズ性のため」は、標準解を退ける理由としては弱い。

## 修正方針の例

1. `spring-boot-starter-security` を入れる
2. パスワードは `BCryptPasswordEncoder`（または `Argon2PasswordEncoder`）でハッシュ化
3. トークンは `jjwt` 等の JWT ライブラリで署名付きトークンを発行。`exp` クレームで期限管理
4. 認証フィルタは `BearerTokenAuthenticationFilter` か `OncePerRequestFilter` を継承して `SecurityFilterChain` に登録
5. ユーザーIDの取り回しは `SecurityContextHolder.getContext().getAuthentication()` 経由

## 参考

- [docs/pattern-catalog.md](../../docs/pattern-catalog.md) の `01. 車輪の再発明` 節
- Scrapbox: [Decision Quality と設計判断失敗パターン](https://scrapbox.io/kawasima/Decision_Quality_%E3%81%A8%E8%A8%AD%E8%A8%88%E5%88%A4%E6%96%AD%E5%A4%B1%E6%95%97%E3%83%91%E3%82%BF%E3%83%BC%E3%83%B3)
