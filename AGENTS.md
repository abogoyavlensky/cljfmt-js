# cljfmt-js

cljfmt compiled to JavaScript with shadow-cljs, plus a thin wrapper. The only
consumer is the clojure-pulse-vscode extension. See README.md for the API.

## Verification (run before claiming anything works)

- `bb check` — build + test + parity. CI runs exactly this. Nothing is done
  until it passes.
- `bb test` — two suites in one node run:
  - `cljfmt-js.core-test` / `cljfmt-js.ns-aliases-test` — the wrapper's API
    surface: `#re` keys, legacy keys, error propagation, the version string,
    and alias/refer/ns-name resolution with its precedence.
  - `cljfmt.core-test` — cljfmt's **own** suite, compiled under our CLJS build.
    The tests are not in the jar, so `bb vendor` shallow-clones cljfmt at the
    pinned tag into `vendor/` first. All 31 of its tests must run; if the count
    drops, the classpath is wrong — fix it, never skip a test.
- `bb parity` — the differential check that actually protects us: formats every
  `test/fixtures/*` under every `test/fixtures/configs/*.edn` twice, once with
  real JVM cljfmt (`cljfmt.config/load-config`, the CLI's own path) and once
  through `dist/cljfmt.js`, then diffs. The JVM is the oracle; there are no
  checked-in goldens.

When you change wrapper behavior, add a fixture or a config rather than a unit
test asserting a string you worked out by hand — let the JVM decide.

## Invariants

- `deps.edn` is the only place the cljfmt version lives. `cljfmtVersion` is
  inlined from it by `cljfmt-js.macros/cljfmt-version` at compile time. The
  literal `"0.16.5"` in `test/cljfmt_js/core_test.cljs` and the README version
  table must be bumped with it — the weekly update PR does this.
- `shadow-cljs.edn` `:exports`, `index.d.ts` and the README API table list the
  same six names. Adding an export means touching all three.
- No filesystem access in the library. Config discovery belongs to the
  consumer; `readConfig` takes a string, never a path.
- Configs are opaque handles (CLJS maps) and must never be converted to plain
  JS objects — regex and symbol keys would not survive.
- `vendor/` is disposable and gitignored. `bb vendor` re-creates it, keyed on
  `vendor/cljfmt/.version`.
- Tags and release assets are immutable: consumers pin a URL plus an integrity
  hash. Fix forward with a new version; never replace an asset.

## Gotchas

- `bb.edn` is read as EDN, so `#(...)` reader literals are illegal there. Use
  `(fn [x] ...)`.
- cljfmt's `reformat-form` overwrites its internal `::ns-name` option from
  `find-namespace`, so that option cannot be supplied from outside — on the JVM
  either. `cljfmt-js.ns-aliases/ns-name-refers` reproduces the current-namespace
  fallback through `:refer-map` instead; see its docstring.
- Each alias/refer map must go through `stringify-map` *before* merging, the way
  cljfmt does it. Merging first leaves a symbol key and a string key for the
  same alias racing on map order.
- `cljfmt-update.yml` cannot open its PR until *Settings → Actions → General →
  Workflow permissions → "Allow GitHub Actions to create and approve pull
  requests"* is enabled. Everything before that step is verified working.
- Test fixtures should avoid `def`-prefixed macro names: cljfmt's default
  indents already carry `#"^def(?!ault)(?!late)(?!er)"`, so such a name formats
  correctly even when resolution does nothing, and the test proves nothing.
