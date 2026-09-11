# Operator Quickstart — kaikei

clone から「cljs アプリが実際にビルドできる」を目で見るまでの最短経路。
**下の手順はすべて 2026-09-07 に実行して出力を確認したものだけを書いている。**

到達点は shadow-cljs のビルド成功と、cljs のテストスイートが通ること。会計の
数字は出ない——このリポジトリは計算を持っていない（`README.md` 参照）。

**このリポジトリのフロントエンドは 2026-09-07 に Svelte から
reagent + re-frame + `jp-go-dds` の ClojureScript へ移行した。** 以下の手順は
その後の状態を前提にしている。移行前（SvelteKit 版）の手順は履歴としてのみ
`README.md` の「実測した現在地（2026-08-14、SvelteKit 版）」に残っている。

## 前提

- Node.js 18+（実測 26.7.0）/ npm（実測 11.19.0）
- `clojure` CLI（shadow-cljs が JVM 経由で ClojureScript をコンパイルする。
  `deps.edn` が `org.clojure/clojure` と `jp-go-digital-design-system` を解決する）
- Cloudflare アカウントは**不要**。ビルドとテストはすべてローカルで完結する。

## 1. clone

```bash
git clone https://github.com/cloud-itonami/kaikei.git
cd kaikei
```

## 2. cljs アプリをビルドする

```bash
cd appview/kaikei-core-kaikei01/cljs
npm install
npm run build          # amu compile --target wasm32-browser app
```

実測（2026-09-07）: `npm install` は exit 0（129 packages）。ビルドは
`[:app] Build completed. (111 files, 110 compiled, 0 warnings, 21.53s)` を出し
exit 0、`public/js/app.js` が生成される。

⚠ このワークスペースでビルドを回すときは resource governor を通す
（superproject の CLAUDE.md、同時 1 本）:

```bash
node <superproject>/scripts/resource-guard.mjs run build -- amu compile --target wasm32-browser app
```

`exit 2` は「lock held、ビルド失敗ではない」——45 秒待って再試行する
（guard を bypass しない）。

## 3. テストを走らせる

```bash
npm test                # amu compile --target wasm32-browser test && node out/tests.js
```

実測（2026-09-07）: `Ran 6 tests containing 14 assertions. 0 failures, 0 errors.`
（`re-frame: Subscribe was called outside of a reactive context.` という警告は
`cljs.test` から re-frame の sub を直接読んでいるための既知の無害な警告で、
失敗ではない——exit code は 0。）

## 4. 手元でページを見る（静的ファイルとして）

`wrangler dev` は検証していない（下記「未検証」参照）。ビルド成果物が正しく
配信されることだけは平の静的サーバで確認できる:

```bash
cd public
python3 -m http.server 8080
```

実測（2026-09-07）: `GET /` → `200`、`GET /js/app.js` → `200`
（`curl -s -o /dev/null -w '%{http_code}\n'`）。**これはファイルが配信できる
ことの確認であって、アプリが実際に mount してレンダリングすることの
ブラウザ確認ではない**——それはこの手順では行っていない。

Cloudflare の `assets` binding や `not_found_handling` の挙動はこれでは
再現できない——それは wrangler 経由でしか確認できず、今回は検証していない。

## 5. 何が deploy されないか（正直に）

`wrangler.jsonc` から `main` キーを削除した——このリポジトリには今日、deploy
される動的 Worker スクリプトが無い。配られるのは `cljs/public/` の静的
アセットだけ。過去に deploy されていた XRPC proxy
（`svelte/src/routes/xrpc/[...path]/+server.ts`）は `src/xrpc-mcp-router-proxy.ts`
に中身そのままで保存してあるが、**配線されていない**（`@sveltejs/kit` に依存
したままで、SvelteKit のビルドが無い今はそのまま動かせない）。もう一方の実装
`src/app.ts` も元から deploy 対象ではなかった。どちらを、あるいは両方を
再配線するかは未決の製品判断——`README.md` の「XRPC の実装は 2 つとも残って
いるが、今日はどちらも deploy されない」を参照。

`tools/mcp-router-stub.cljk` は `src/xrpc-mcp-router-proxy.ts` が期待する wire
形式（JSON-RPC 2.0 `tools/call` → `result.structuredContent` の unwrap）を
記録したテスト用スタブとして残しているが、**その proxy 自体が今は何にも
配線されていないので、このリポジトリの現在の動作を検証する手段ではない**。
再配線する側が使う参考実装として読む。

## 6. deploy（今日は検証していない）

```bash
cd appview/kaikei-core-kaikei01
npx wrangler deploy
```

**これは実行していない（UNVERIFIED）。** `wrangler.jsonc` の route が
`kaikei01.etzhayyim.com/*`（zone `etzhayyim.com`）を要求するが、そのホストは
2026-08-14 時点で NXDOMAIN（`README.md` 参照、今回は再測定していない）で、
zone への権限もこのリポジトリからは確認できない。deploy を試す前に決める
必要があるのは 3 つ:

1. このアプリはどのホスト名で出るのか（`etzhayyim.com` のままか、
   `cloud-itonami` 側へ移すのか——`MIGRATION-TODO.md` の codemod と同じ判断）
2. 動的な XRPC proxy を再配線するか、静的な状態表示ページのままにするか
3. 上流の MCP router / dispatcher は実在するのか、しないなら何を向けるのか

決まるまでは手順 2〜4 が、このリポジトリで踏める一番遠くまでである。

## 片付け

```bash
rm -rf appview/kaikei-core-kaikei01/cljs/node_modules \
       appview/kaikei-core-kaikei01/cljs/.shadow-cljs \
       appview/kaikei-core-kaikei01/cljs/.cpcache \
       appview/kaikei-core-kaikei01/cljs/out \
       appview/kaikei-core-kaikei01/cljs/public/js \
       appview/kaikei-core-kaikei01/node_modules \
       appview/kaikei-core-kaikei01/.wrangler
```

`node_modules/` `.shadow-cljs/` `.cpcache/` `out/` `public/js/` は
`cljs/.gitignore` 済み。`cljs/package-lock.json` は commit してある——このリポ
ジトリの cljs 依存は pin する方針で、`appview/kaikei-core-kaikei01/package.json`
（`src/app.ts` の typecheck 専用、cljs とは別の依存）の扱いは従来どおり未決
（`git status` に出たら、それは隠す対象ではなく決める対象である）。
