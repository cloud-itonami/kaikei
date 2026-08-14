# kaikei — 会計エージェントの edge surface

**kaikei（会計）は accounting を指す。このリポジトリが持っているのは会計そのもの
ではなく、会計エージェントの入口 1 枚**——Cloudflare Worker 1 本だけである。

仕訳も試算表も P/L も B/S も、**ここには 1 行も無い**。この Worker は
`POST /xrpc/<nsid>` を上流の MCP router へ JSON-RPC `tools/call` として転送し、
返ってきた `result.structuredContent` を裸で返す。それが全部。

| | 置き場所 |
|---|---|
| 入口（このリポジトリ） | `appview/kaikei-core-kaikei01/` |
| 会計ロジック | `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/ingest/kaikei.py`（**別リポジトリ**） |
| 業務フロー | `etzhayyim-root/00-contracts/bpmn/com/etzhayyim/kaikei`（**別リポジトリ**） |

## 実測した現在地（2026-08-14）

**動く:**

- `npm run build`（SvelteKit + `@sveltejs/adapter-cloudflare`）は通り、
  `wrangler.jsonc` の `main` が指す `svelte/.svelte-kit/cloudflare/_worker.js` を出す。
- `wrangler dev` でローカルに立ち、`GET /` が状態表示の 1 ページを返す。
- `OPTIONS /xrpc/<nsid>` → `204` + `access-control-allow-origin: *`。
- `POST /xrpc/<nsid>` → 到達可能な router を向ければ `200`。転送・封筒・
  unwrap まで通ることを stub で確認済み（`docs/operator-quickstart.md` 手順 4）。

**動かない:**

- **`etzhayyim.com` のサブドメインが 1 つも解決しない。** `kaikei01` /
  `kaikei` / `dispatcher` / `mcp` すべて NXDOMAIN（apex だけが引ける）。
  つまり既定の上流 `https://mcp.etzhayyim.com/...` へは繋がらず、素で
  `POST /xrpc/<nsid>` を叩くと **`fetch` が uncaught で throw して `500`** になる
  （`+server.ts` は fetch を try で囲っていない）。**このリポジトリは今日、
  実インフラに対しては運転できない。**
- **`GET /health` と `GET /_app/meta` は deploy される成果物には無く、`404`。**
  実装は `src/app.ts` にあるが、`wrangler.jsonc` の `main` は SvelteKit の
  ビルド出力を指していて `src/app.ts` ではない。
- `kotodama.jsonld` が名乗る `did:web:kaikei.etzhayyim.com` は、そのホストが
  NXDOMAIN なので **解決できない**。

## XRPC の実装が 2 つある（deploy されるのは片方だけ）

同じ proxy が 2 実装あり、**上流も wire protocol も違う**:

| | `src/app.ts` | `svelte/src/routes/xrpc/[...path]/+server.ts` |
|---|---|---|
| 上流 | `DISPATCHER_URL`（既定 `dispatcher.etzhayyim.com`） | `AGENTGATEWAY_MCP_ROUTER_URL`（既定 `mcp.etzhayyim.com`） |
| 形 | `POST /xrpc/<nsid>` に body を素通し | JSON-RPC 2.0 `tools/call` に包む |
| `/health` | 有る | 無い |
| **deploy される** | **されない** | **される** |

それでも `src/app.ts` は残っている——`package.json` の `typecheck` が見る唯一の
ファイルであり、`kotodama.jsonld` の `component.path` が指す先でもある。
**`src/app.ts` を読んで挙動を推測しない。動いているのは `+server.ts` のほう。**
どちらを正とするかは未決で、`MIGRATION-TODO.md` の codemod と併せて decide する。

## 由来と移行状態

`etzhayyim/root` の `60-apps/etzhayyim-project-kaikei` から 2026-05-21 に
TRANSFORM バッチで切り出した seed（`migration.edn` が出所 revision と tree を固定
している）。**現在は `cloud-itonami` org に居るが、中身は依然 etzhayyim 名前空間**
（`com.etzhayyim.apps.kaikei.*` / `did:web:*.etzhayyim.com`）で、codemod は未実施。

未処理項目の正本は `MIGRATION-TODO.md`。`NOTICE` は Apache-2.0 + Charter
Compliance Rider v3.1 を宣言するが、**`CHARTER-RIDER.md` も `LICENSE` も
このリポジトリには入っていない**（上流にある）。

## 近接リポジトリとの境界

- 会計の**計算**を探しているなら、ここではない（上表の kotodama ingest）。
- 会計の**業務フロー定義**を探しているなら、ここではない（上表の BPMN）。
- ここにあるのは **HTTP 境界と CORS と封筒の変換だけ**。

## 次に読む

- `docs/operator-quickstart.md` — clone から検証済みのローカル実行まで
- `MIGRATION-TODO.md` — Charter §2(a)-(h) 適合の未処理リスト
- `migration.edn` — 切り出し元の revision / tree / ファイル数
