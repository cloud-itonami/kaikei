# Operator Quickstart — kaikei

clone から「proxy が実際に通った」を目で見るまでの最短経路。
**下の手順はすべて 2026-08-14 に実行して出力を確認したものだけを書いている。**

到達点は `200` と、stub が返した JSON がそのまま手元に出ること。会計の数字は
出ない——このリポジトリは計算を持っていない（`README.md` 参照）。

## 前提

- Node.js 22+（実測 26.3.0）
- `nbb`（手順 4 の stub 用。`npm i -g nbb`）
- Cloudflare アカウントは**不要**。すべてローカルで完結する。

## 1. clone

```bash
git clone https://github.com/cloud-itonami/kaikei.git
cd kaikei
```

## 2. edge facade を typecheck する

```bash
cd appview/kaikei-core-kaikei01
npm install
npm run typecheck     # tsc --noEmit --strict src/app.ts
```

無出力・exit 0 が正常（実測 tsc 6.0.3）。

⚠ **これが検査しているのは deploy されないほうのファイルである。** `typecheck` は
`src/app.ts` だけを見るが、`wrangler.jsonc` の `main` は SvelteKit のビルド出力を
指している。ここが緑でも、実際に配られる Worker は 1 行も検査されていない
（`README.md` の「XRPC の実装が 2 つある」）。

## 3. deploy される成果物をビルドする

```bash
cd svelte
npm install
npm run build
```

`.svelte-kit/cloudflare/_worker.js` が出れば成功——これが `wrangler.jsonc` の
`main` が指すファイルそのもの。

```bash
ls -l .svelte-kit/cloudflare/_worker.js
```

⚠ このワークスペースでビルドを回すときは resource governor を通す
（superproject の CLAUDE.md、同時 1 本）:

```bash
node <superproject>/scripts/resource-guard.mjs run build -- npm run build
```

## 4. stub router を立てて proxy を実際に通す

**既定の上流 `mcp.etzhayyim.com` は NXDOMAIN で、素で叩くと `500` になる**
（`+server.ts` は `fetch` を try で囲っていない）。上流を差し替えれば proxy
そのものは検証できる。

端末 A — stub を立てる:

```bash
cd <repo root>
nbb tools/mcp-router-stub.cljs 8798
# mcp-router-stub listening on http://127.0.0.1:8798
```

端末 B — 上流を stub に向けて Worker を立てる:

```bash
cd appview/kaikei-core-kaikei01
svelte/node_modules/.bin/wrangler dev --port 8799 --local --ip 127.0.0.1 \
  --var AGENTGATEWAY_MCP_ROUTER_URL:http://127.0.0.1:8798
```

端末 C — 叩く:

```bash
curl -s -X POST -H 'content-type: application/json' -d '{"fy":2026}' \
  http://127.0.0.1:8799/xrpc/com.etzhayyim.apps.kaikei.trialBalance
```

実測した応答:

```json
{"stub":true,"tool":"com.etzhayyim.apps.kaikei.trialBalance","echo":{"fy":2026},
 "note":"fixture response from tools/mcp-router-stub.cljs — not accounting data"}
```

端末 A 側にも `stub <- tools/call "com.etzhayyim.apps.kaikei.trialBalance"` が出る。
**これで通ったと言えるのは「nsid の切り出し → JSON-RPC `tools/call` への封筒詰め →
`result.structuredContent` の unwrap」までで、会計については何も言えない。**

## 5. 何が動いていないかを自分で確認する

信じずに測る。上の Worker が立っている状態で:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8799/health      # → 404
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8799/_app/meta   # → 404
curl -s -o /dev/null -w '%{http_code} %header{access-control-allow-origin}\n' \
  -X OPTIONS http://127.0.0.1:8799/xrpc/com.etzhayyim.apps.kaikei.journalEntry  # → 204 *
```

`/health` の 404 は誤設定ではなく、上記の二重実装の帰結
（実装は `src/app.ts` にあり、deploy されるのは SvelteKit のほう）。

上流の不在も直接引ける:

```bash
host mcp.etzhayyim.com          # → NXDOMAIN
host kaikei01.etzhayyim.com     # → NXDOMAIN
```

## 6. deploy（今日は通らない）

```bash
cd appview/kaikei-core-kaikei01
svelte/node_modules/.bin/wrangler deploy
```

**これは未検証で、今日は成立しない。** `wrangler.jsonc` の route が
`kaikei01.etzhayyim.com/*`（zone `etzhayyim.com`）を要求するが、そのホストは
存在せず、zone への権限もこのリポジトリからは確認できない。deploy を試す前に
決める必要があるのは 2 つ:

1. このアプリはどのホスト名で出るのか（`etzhayyim.com` のままか、
   `cloud-itonami` 側へ移すのか——`MIGRATION-TODO.md` の codemod と同じ判断）
2. 上流の MCP router は実在するのか、しないなら何を向けるのか

決まるまでは手順 4 のローカル経路が、このリポジトリで踏める一番遠くまでである。

## 片付け

```bash
# 端末 B / A を Ctrl-C
rm -rf appview/kaikei-core-kaikei01/node_modules \
       appview/kaikei-core-kaikei01/svelte/node_modules \
       appview/kaikei-core-kaikei01/svelte/.svelte-kit \
       appview/kaikei-core-kaikei01/.wrangler
```

`node_modules/` `.svelte-kit/` `.wrangler/` は `.gitignore` 済み。
`npm install` が置いていく `package-lock.json` 2 本は**わざと ignore していない**
——このリポジトリが依存を pin するかどうかは未決だからで、消すか commit するかは
明示的に決める。`git status` に出たら、それは隠す対象ではなく決める対象である。
