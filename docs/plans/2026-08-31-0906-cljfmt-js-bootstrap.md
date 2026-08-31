# cljfmt-js Bootstrap Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `cljfmt-js` — cljfmt compiled to JavaScript with shadow-cljs, verified against cljfmt's own test suite and against JVM cljfmt, and published as a tarball on GitHub Releases for the clojure-pulse-vscode extension.

**Tech Stack:** ClojureScript + shadow-cljs 3.5 (`:node-library`), `dev.weavejester/cljfmt` 0.16.5, rewrite-clj, babashka tasks, mise, GitHub Actions, antq.

---

## Design

### Purpose and scope

The clojure-pulse-vscode extension needs cljfmt's exact output in-process — synchronously, per keystroke for indent-on-Enter, and for Format Document. `cljfmt.core` is `.cljc`, so it compiles to JavaScript. This repo is that build plus a thin wrapper: a **pure library** with no filesystem access. Config-file discovery (`.cljfmt.edn` lookup) is the extension's job, not this library's.

Only consumer: the extension. Distribution is a GitHub Release tarball; no npm registry.

Out of scope: reimplementing any formatting rule, reading `.cljfmt.clj`, publishing to npm or GitHub Packages, config discovery.

### Public API (`index.d.ts`)

Configs are **opaque handles** (ClojureScript maps). They never cross the JS boundary as plain objects, so regex and symbol keys survive intact.

```ts
export interface Config { readonly __brand: "cljfmt.Config" }
export interface NsContext { readonly __brand: "cljfmt.NsContext" }

/** Parse a .cljfmt.edn / cljfmt.edn string. Supports `#re "…"`, applies
 *  cljfmt's legacy-key conversion. Throws Error on invalid EDN. */
export function readConfig(edn: string): Config;
/** Plain merge; `override` keys win (cljfmt.config semantics). */
export function mergeConfig(base: Config, override: Config): Config;
/** cljfmt.core/default-options. */
export const defaultConfig: Config;
/** Namespace aliases/refers/ns-name derived from a whole file's `ns` form —
 *  pass this when formatting a sub-form (window) that lacks the ns form. */
export function readNsContext(source: string): NsContext;
/** Format `source` exactly as JVM cljfmt would. Throws Error when the
 *  source cannot be parsed (unbalanced delimiters etc.). */
export function reformatString(source: string, config?: Config, nsContext?: NsContext): string;
/** Version of the bundled cljfmt, read from deps.edn at compile time. */
export const cljfmtVersion: string;
```

### The ns-alias parity shim

cljfmt's CLJS branch skips ns-form parsing: `alias-map-for-form` and `refer-map-for-form` are `#?(:clj …)` only, and on cljs `reformat-form` uses only the `:alias-map` / `:refer-map` passed in options. A naive JS build therefore diverges from the CLI whenever a config has a qualified indent key (`{my.lib/defthing [[:inner 0]]}`) reached through `(:require [my.lib :as ml])`.

`src/cljfmt_js/ns_aliases.cljs` ports those private functions (from the vendored source, EPL-noticed) and derives the maps from a source string with rewrite-clj. `reformatString` computes the effective maps with the JVM's precedence — derived from the input text first, then `nsContext`, then the user's config values winning last:

```clojure
;; effective :alias-map (same for :refer-map)
(merge (derived-from source) (:alias-map ns-context) (:alias-map config))
```

`readNsContext` exists because the extension formats a small window (a sub-form) on Enter; that window has no `ns` form, so the extension derives the context once per file and passes it in. The context carries **three** things: `:alias-map`, `:refer-map`, and the namespace name. cljfmt's `find-namespace` is already cross-platform, but on a window it finds nothing — so `reformatString` must also pass the context's ns-name through the option cljfmt reads it from (the executor finds the exact key in the vendored `reformat-form`; Codex's reading is `:cljfmt.core/ns-name`), with the same precedence: derived from the input, then `nsContext`, then config.

Cost: one extra parse of the input per call. Acceptable — whole-file formatting is dominated by reformatting, and Enter windows are tiny.

### Verification strategy — three layers

1. **Wrapper unit tests** (`test/cljfmt_js/*_test.cljs`): the API surface — `#re` keys, legacy keys, options taking effect, error on bad input, version string, alias precedence.
2. **cljfmt's own test suite under the CLJS build.** The tests are not in the jar, so `bb vendor` reads the cljfmt version from `deps.edn` and shallow-clones `weavejester/cljfmt` at that tag into gitignored `vendor/`; the `:test` alias puts `vendor/cljfmt/cljfmt/test` on the classpath. Only `cljfmt.core-test` (a `.cljc`) runs; `config_test.clj` is JVM-only. cljfmt's `test_runner.cljs` is not used — shadow's `:node-test` runner reports and sets exit codes.
3. **Differential parity against JVM cljfmt** (`bb parity`): mise installs Java and the Clojure CLI anyway, so a JVM script formats every `test/fixtures/*.clj` under every `test/fixtures/configs/*.edn` using the real `cljfmt.core` + `cljfmt.config` reader; a node script does the same through `dist/cljfmt.js`; the task diffs both output trees. No checked-in goldens — the JVM is the oracle, `deps.edn` the single version.

`bb check` = build + test + parity. CI runs `bb check`; nothing is "done" until it passes.

### Versioning and release

- `package.json` carries the package's own semver (starts at `0.1.0`). The bundled cljfmt version is exported as `cljfmtVersion`, inlined by a macro that reads `deps.edn` at compile time — one source of truth, no drift.
- `bb tag` tags `v<package.json version>` and pushes; `release.yml` runs `bb check`, `bb pack`, and uploads `abogoyavlensky-cljfmt-js-<ver>.tgz` + `checksums.txt` to a GitHub Release. Tags and assets are **immutable** — a consumer's lockfile pins the URL and integrity hash; fix forward with a new version.
- The extension installs with
  `npm install https://github.com/abogoyavlensky/cljfmt-js/releases/download/v0.1.0/abogoyavlensky-cljfmt-js-0.1.0.tgz`.

### Watching cljfmt releases

`cljfmt-update.yml` runs weekly (and on `workflow_dispatch`): antq bumps `dev.weavejester/cljfmt` in `deps.edn` (`--upgrade --force --focus`), and if the file changed, the workflow runs `bb check` itself (continue-on-error) and opens a PR whose body states the verdict. PRs opened with `GITHUB_TOKEN` do not trigger other workflows, so running the checks inside the update job avoids needing a PAT. Merge is manual; pushing to master runs CI again; then `bb tag` releases.

### Tooling

- `mise.toml`: `java = "temurin-25"`, `clojure = "1.12.5.1664"`, `node = "24.20.0"`, `babashka = "1.13.219"`. CI uses `jdx/mise-action@v3.6.1` (as clj-pulse does).
- shadow-cljs 3.5.0 as an npm devDependency and `thheller/shadow-cljs` in `deps.edn`; `shadow-cljs.edn` reads `deps.edn` (`:deps {:aliases [:test]}`).
- Actions: `actions/checkout@v5`, `jdx/mise-action@v3.6.1`, `softprops/action-gh-release@v2`, `peter-evans/create-pull-request@v8`.
- Package `@abogoyavlensky/cljfmt-js`, `main: dist/cljfmt.js`, `types: index.d.ts`, `engines.node >= 18` (the extension's esbuild targets node18; `:node-library` emits CommonJS).
- Licensing: wrapper is MIT (`LICENSE`); `NOTICE` carries cljfmt's and rewrite-clj's EPL-1.0 notices, and states which files are ported from cljfmt.

### Error handling

`readConfig` and `reformatString` let the underlying exception propagate as a JS `Error` with cljfmt's/rewrite-clj's message. The library never guesses; the extension owns fallback behavior.

## File Structure

```
mise.toml                         pinned system tools
deps.edn                          cljfmt + shadow-cljs deps; :test and :parity aliases; :outdated (antq)
shadow-cljs.edn                   :lib (node-library, advanced) and :test (node-test) builds
package.json                      @abogoyavlensky/cljfmt-js, scripts-free; shadow-cljs devDependency
bb.edn                            vendor, build, test, parity, check, pack, tag, outdated
index.d.ts                        hand-written typings (API above)
.gitignore                        node_modules/ vendor/ dist/ target/ .shadow-cljs/ .cpcache/ *.tgz .tmp/ .nrepl-port
LICENSE                           MIT
NOTICE                            EPL-1.0 notices for cljfmt and rewrite-clj; list of ported functions
README.md                         purpose, API, install-from-release, versioning table, release + update process
AGENTS.md                         verification commands and invariants for agents

src/cljfmt_js/macros.clj          `cljfmt-version` macro: reads deps.edn at compile time
src/cljfmt_js/ns_aliases.cljs     alias-map / refer-map derivation (ported from cljfmt's clj-only code)
src/cljfmt_js/core.cljs           exported API: read-config, merge-config, default-config,
                                  read-ns-context, reformat-string, cljfmt-version

test/cljfmt_js/core_test.cljs     wrapper API tests
test/cljfmt_js/ns_aliases_test.cljs  alias derivation + precedence tests
test/fixtures/*.clj               parity corpus (see Task 6)
test/fixtures/configs/*.edn       parity configs

scripts/parity/parity/jvm.clj     JVM side: writes target/parity/jvm/<fixture>__<config>.out
scripts/parity/js.mjs             JS side: writes target/parity/js/<fixture>__<config>.out

.github/workflows/ci.yml          push/PR to master: mise-action, npm ci, bb check
.github/workflows/release.yml     tag v*: bb check, bb pack, GitHub Release with tgz + checksums
.github/workflows/cljfmt-update.yml  weekly cron + dispatch: antq bump → bb check → PR with verdict
```

## Tasks

### Task 1: Repository bootstrap and first push

**Files:**
- Create: `mise.toml`, `.gitignore`, `LICENSE`, `NOTICE`, `deps.edn`, `shadow-cljs.edn`, `package.json`, `bb.edn`, `README.md` (stub), `src/cljfmt_js/core.cljs` (placeholder export)

- [x] **Step 1: Tooling files**
  `mise.toml` with the four pinned tools from the design. `.gitignore` per the file structure. `LICENSE` = MIT, copyright Andrey Bogoyavlenskiy 2026. `NOTICE`: EPL-1.0 notice text for cljfmt (Copyright © James Reeves) and rewrite-clj, with a placeholder line for the ported-functions list (filled in Task 4).
  Run: `cd /home/agent/Projects/cljfmt-js && mise install && mise exec -- java -version && mise exec -- clojure --version && mise exec -- bb --version`
  Expected: temurin 25, Clojure CLI 1.12.5.1664, babashka 1.13.219 all print. (If the `clojure` mise plugin needs `java` on PATH first, note the ordering in README.)

- [x] **Step 2: deps.edn**
  ```clojure
  {:paths ["src"]
   :deps {thheller/shadow-cljs {:mvn/version "3.5.0"}
          dev.weavejester/cljfmt {:mvn/version "0.16.5"}}
   :aliases
   {:test {:extra-paths ["test" "vendor/cljfmt/cljfmt/test"]}
    :parity {:extra-paths ["scripts/parity"]}
    :outdated {:deps {com.github.liquidz/antq {:mvn/version "<latest on Clojars>"}}
               :main-opts ["-m" "antq.core"]}}}
  ```
  Look up antq's latest release at https://clojars.org/api/artifacts/com.github.liquidz/antq and pin it.

- [x] **Step 3: shadow-cljs.edn and package.json**
  `shadow-cljs.edn`: `:deps {:aliases [:test]}`; build `:lib` — `:target :node-library`, `:output-to "dist/cljfmt.js"`, `:exports` mapping `readConfig mergeConfig defaultConfig readNsContext reformatString cljfmtVersion` to `cljfmt-js.core/…` vars, `:compiler-options {:optimizations :advanced}`; build `:test` — `:target :node-test`, `:output-to "target/test.js"`, `:ns-regexp "^(cljfmt\\.core-test|cljfmt-js\\..*-test)$"`.
  `package.json`: `name "@abogoyavlensky/cljfmt-js"`, `version "0.1.0"`, `description`, `license "MIT"`, `main "dist/cljfmt.js"`, `types "index.d.ts"`, `files ["dist/cljfmt.js", "index.d.ts", "NOTICE"]`, `engines {"node": ">=18"}`, `repository`, `devDependencies {"shadow-cljs": "3.5.0"}`. No `scripts` — bb owns tasks.
  Run: `npm install`
  Expected: `node_modules/shadow-cljs` present, `package-lock.json` created.

- [x] **Step 4: Placeholder namespace and build task**
  `src/cljfmt_js/core.cljs` with the six vars as stubs (`reformat-string` may call `cljfmt.core/reformat-string` directly already — that proves cljfmt compiles under CLJS, including its `read-resource` macro inlining the indent resources). `bb.edn` with `build` (`npx shadow-cljs release lib`) and an `:enter` line like clj-pulse's.
  Run: `bb build && node -e 'const c=require("./dist/cljfmt.js"); console.log(c.reformatString("(defn f [x]\n(inc x))"))'`
  Expected: prints `(defn f [x]\n  (inc x))`. If shadow warns about `cljfmt.core` needing `read-resource` macros, confirm `cljfmt/indents/*.clj` resources are on the classpath (they ship in the jar) before changing anything.

- [x] **Step 5: README stub and first commit + push**
  README: one paragraph of purpose and "work in progress". Commit and push; the first pushed branch becomes GitHub's default.
  Run: `git add -A && git commit -m "Bootstrap cljfmt-js: mise, deps, shadow build, placeholder API" && git push -u origin master && gh repo view --json defaultBranchRef --jq .defaultBranchRef.name`
  Expected: `master`. If not, run `gh repo edit --default-branch master`.

### Task 2: Wrapper API (TDD)

**Files:**
- Create: `src/cljfmt_js/macros.clj`, `test/cljfmt_js/core_test.cljs`
- Modify: `src/cljfmt_js/core.cljs`, `bb.edn`

- [x] **Step 1: Write failing tests**
  `cljfmt-js.core-test` (cljs.test) covering:
  - `read-config` parses `{:extra-indents {#re "^with-" [[:inner 0]]}}` into a map whose key is a `js/RegExp`; `{:legacy/merge-indents? true :indents {foo [[:inner 0]]}}` becomes `:extra-indents`; invalid EDN throws.
  - `default-config` equals `cljfmt.core/default-options`; `merge-config` — override wins.
  - `reformat-string` with defaults: `(foo bar\nbaz)` → `(foo bar\n     baz)`; with `{:function-arguments-indentation :cursive}`: `(foo\nbar)` → `(foo\n  bar)`; with `:extra-indents` `#re "^with-"` `:inner 0`: `(with-x y\nz)` → 2-space body; unbalanced `(foo` throws.
  - `cljfmt-version` equals the version string in `deps.edn` (test reads it via the same macro is circular — instead assert it matches `#"^\d+\.\d+\.\d+$"` and equals `"0.16.5"` literally; the literal is updated by the antq PR, which is desirable: it forces the test file to acknowledge bumps. **Decision:** keep the literal; the update PR must touch it.)

- [x] **Step 2: Add the test task and run it to verify failure**
  `bb.edn` `test` task: `npx shadow-cljs compile test && node target/test.js`.
  Run: `bb test`
  Expected: FAIL (missing vars / wrong output).

- [x] **Step 3: Implement**
  `macros.clj`: `(defmacro cljfmt-version [] (-> "deps.edn" slurp edn/read-string :deps (get 'dev.weavejester/cljfmt) :mvn/version))`.
  `core.cljs`: `read-config` = `cljs.tools.reader.edn/read-string {:readers {'re re-pattern}}` then legacy-key conversion (port of `cljfmt.config/convert-legacy-keys`: when `:legacy/merge-indents?`, rename `:indents` → `:extra-indents` and drop the flag — verify against `vendor/cljfmt/cljfmt/src/cljfmt/config.clj` once Task 3 vendors it, or read it on GitHub now). `merge-config` = `merge`. `default-config` = `cljfmt.core/default-options`. `reformat-string` (2-arity for now) = `(cljfmt.core/reformat-string text (or config default-config))`. `cljfmt-version` via the macro. Exceptions propagate untouched.

- [x] **Step 4: Run tests to verify they pass**
  Run: `bb test`
  Expected: all pass, `0 failures, 0 errors`.

- [x] **Step 5: Commit**
  `git add -A && git commit -m "Add wrapper API: readConfig, mergeConfig, defaultConfig, reformatString, cljfmtVersion"`

### Task 3: Run cljfmt's own test suite under the CLJS build

**Files:**
- Modify: `bb.edn`

- [x] **Step 1: Vendor task**
  `bb vendor`: read the cljfmt version from `deps.edn` (`edn/read-string`), then if `vendor/cljfmt/.version` does not contain it, `rm -rf vendor/cljfmt` and `git clone --depth 1 --branch <ver> https://github.com/weavejester/cljfmt.git vendor/cljfmt`, then write `.version`. First check the tag format: `git ls-remote --tags https://github.com/weavejester/cljfmt.git | grep 0.16.5` — use `v`-prefixed tags if that is what exists. Make `test` depend on `vendor`.

- [x] **Step 2: Run cljfmt's suite**
  Run: `bb test`
  Expected: `cljfmt.core-test` is picked up by the `:ns-regexp` and passes alongside the wrapper tests. Likely snags: `cljfmt.test-util.cljs` is a `.clj` macro namespace that must be on the classpath (it is, under `vendor/cljfmt/cljfmt/test`); if any test relies on JVM-only helpers, check how cljfmt's own `lein test-all` (cljsbuild, `cljfmt.test-runner`) runs the same namespace — it runs exactly `cljfmt.core-test` on node, so the whole namespace is expected to pass. Do not skip tests; fix the classpath.

- [x] **Step 3: Commit**
  `git commit -am "Run cljfmt's own core tests under the CLJS build"`

### Task 4: ns-alias parity shim (TDD)

**Files:**
- Create: `src/cljfmt_js/ns_aliases.cljs`, `test/cljfmt_js/ns_aliases_test.cljs`
- Modify: `src/cljfmt_js/core.cljs`, `NOTICE`


> Deviation: the plan's ns-name mechanism does not exist. `reformat-form`
> (cljfmt 0.16.5 `core.cljc:903-905`) sets `::ns-name` unconditionally from
> `find-namespace`, so an incoming `:cljfmt.core/ns-name` option is discarded —
> verified against the JVM, where passing it is equally a no-op. Aliases and
> refers are unaffected (they are merged, `core.cljc:897-902`). The window
> ns-name case is instead reproduced through `:refer-map`, which
> `fully-qualified-symbol` consults for the same symbols one step earlier:
> `ns-aliases/ns-name-refers` maps every unqualified symbol in the window to
> the context's namespace at the *lowest* precedence, so real refers and
> aliases still win. Public API and precedence are exactly as designed.
> A second deviation: each map is passed through `stringify-map` *before*
> merging, matching cljfmt's own order — merging first would leave a symbol
> key and a string key for the same alias racing on map order.

- [x] **Step 1: Write failing tests**
  Source under test:
  ```clojure
  (ns app.core (:require [my.lib :as ml] [other.lib :refer [defthing2]]))
  (ml/defthing x
  (inc x))
  (defthing2 y
  (dec y))
  ```
  Config `{:extra-indents {my.lib/defthing [[:inner 0]] other.lib/defthing2 [[:inner 0]]}}`.
  - `reformat-string` on the whole text: both bodies indented 2 (JVM behavior).
  - The same body forms alone (window without ns form) + `(read-ns-context whole-text)` as third arg: bodies indented 2. Without ns context: aligned under the first argument (community default) — documents why the context matters.
  - Precedence: user config `{:alias-map {"ml" "not.my.lib"}}` overrides the derived alias (result: no inner indent).
  - ns-name: config `{:extra-indents {app.core/defthing3 [[:inner 0]]}}` and an unqualified `(defthing3 z\n(inc z))` in the `app.core` file — whole text indents the body 2 (JVM behavior: the current ns qualifies it); the same form as a window indents 2 only when `read-ns-context` of the whole text is passed.
  - `read-ns-context` returns empty maps and no ns-name for text without an ns form; ignores an `ns` form that is not top-level.

- [x] **Step 2: Run tests to verify they fail**
  Run: `bb test`
  Expected: FAIL — `read-ns-context` undefined, whole-text case not indented.

- [x] **Step 3: Port the derivation**
  In `vendor/cljfmt/cljfmt/src/cljfmt/core.cljc` locate the `#?(:clj …)` functions `top-level-form`, `ns-require-form?`, `as-keyword?`, `refer-keyword?`, `symbol-node?`, `leftmost-symbol`, `ns-require-form-parent`, `join-ns-str`, `refer-zloc->refer-mapping`, `refer-map-for-form`, `as-zloc->alias-mapping`, `alias-map-for-form`, and how `reformat-form` combines them on `:clj` (`stringify-map`, merge order). Port them into `cljfmt-js.ns-aliases` as cljs (rewrite-clj is cross-platform; drop the reader conditionals). Expose `(derive source-string) → {:alias-map … :refer-map … :ns-name …}` (string keys/values, as `stringify-map` produces; ns-name via cljfmt's cross-platform `find-namespace` logic, ported or reused). In `reformat-form`, find the option key the ns-name is read from (Codex's reading: `:cljfmt.core/ns-name`) and how it is combined with the derived value on `:clj`.
  In `core.cljs`: `reformat-string` gains a 3-arity; effective `:alias-map` and `:refer-map` = `(merge derived ns-context-value config-value)`, and the effective ns-name = `(or config-value ns-context-value derived)`, assoc'd into the options (ns-name under cljfmt's own option key) before calling `cljfmt.core/reformat-string`. `read-ns-context` = `derive` (returned as an opaque map). Update `shadow-cljs.edn` `:exports` with `readNsContext` if not already present.
  Update `NOTICE`: list the ported function names and the cljfmt version/file they came from.

- [x] **Step 4: Run tests to verify they pass**
  Run: `bb test`
  Expected: PASS, including cljfmt's suite (unchanged behavior when no ns form is present).

- [x] **Step 5: Commit**
  `git add -A && git commit -m "Derive ns aliases and refers on CLJS to match JVM cljfmt"`

### Task 5: Typings, packaging, install smoke test

**Files:**
- Create: `index.d.ts`
- Modify: `package.json`, `bb.edn`, `README.md`

- [x] **Step 1: index.d.ts**
  Exactly the API block from the design (opaque branded interfaces, JSDoc comments). Keep it in sync with `:exports` — six exports.

- [x] **Step 2: pack task**
  `bb pack`: depends on `build`; runs `npm pack`, which produces `abogoyavlensky-cljfmt-js-<version>.tgz` in the repo root.
  Run: `bb pack && tar tzf abogoyavlensky-cljfmt-js-0.1.0.tgz`
  Expected: `package/package.json`, `package/dist/cljfmt.js`, `package/index.d.ts`, `package/NOTICE`, `package/README.md`, `package/LICENSE` — nothing else.

- [x] **Step 3: Install smoke test from the tarball**
  Run: `D=$(mktemp -d) && cd $D && npm init -y >/dev/null && npm install /home/agent/Projects/cljfmt-js/abogoyavlensky-cljfmt-js-0.1.0.tgz && node -e 'const c=require("@abogoyavlensky/cljfmt-js"); console.log(c.cljfmtVersion, JSON.stringify(c.reformatString("(let [a 1]\n(inc a))", c.readConfig("{:function-arguments-indentation :cursive}"))))'`
  Expected: `0.16.5 "(let [a 1]\n  (inc a))"`.
  Also type-check the typings: in the same temp dir, `npm install typescript@latest -D`, write `t.ts` importing all six exports and calling them, run `npx tsc --noEmit t.ts`. Expected: no errors.

- [x] **Step 4: README API section**
  Document the six exports, the opaque-handle rule, the `readNsContext` use case (windowed formatting), install-from-release URL pattern, and the "package version vs `cljfmtVersion`" table with one row (`0.1.0` → `0.16.5`).

- [x] **Step 5: Commit**
  `git add -A && git commit -m "Add typings, npm pack task and README API docs"`

### Task 6: Parity harness against JVM cljfmt

**Files:**
- Create: `test/fixtures/*.clj`, `test/fixtures/configs/*.edn`, `scripts/parity/parity/jvm.clj`, `scripts/parity/js.mjs`
- Modify: `bb.edn`

- [x] **Step 1: Fixtures**
  Files (each a realistic namespace, 30–80 lines, deliberately mis-indented and with whitespace noise so the formatter has work to do):
  - `basic.clj` — defn/let/when/cond/->, maps and vectors, comments, strings with `\"`, regex literals, char literals, `#_` and `#{}`.
  - `aliases.clj` — `(:require [x.y :as xy] [a.b :refer [thing]])` with `xy/…` and `thing` heads, plus an unqualified head matched by a key qualified with the file's own namespace, all used against qualified keys in `extra_indents.edn`.
  - `ns_sorting.clj` — unsorted `:require`/`:import` for `:sort-ns-references?`.
  - `maps.clj` — nested maps for `:split-keypairs-over-multiple-lines?`.
  - `reader_conditionals.cljc` — `#?(:clj … :cljs …)`, multi-arity fns, metadata on defs.
  Configs: `default.edn` (`{}`), `cursive.edn`, `zprint.edn`, `extra_indents.edn` (regex `#re` key + qualified keys), `lgx.edn` (copy of `../lgx/.cljfmt.edn` minus `:paths`/`:file-pattern`/`:parallel?`), `all_whitespace_flags.edn` (`:remove-multiple-non-indenting-spaces?`, `:remove-blank-lines-in-forms?`, `:indent-line-comments?`, `:normalize-newlines-at-file-end?` all true).

- [x] **Step 2: JVM side**
  `parity.jvm/-main`: for every fixture × config, read the config with cljfmt's real reader (`cljfmt.config` — `read-config` on the file; check in the vendored source whether legacy-key conversion happens inside it or in `load-config`, and mirror the CLI's path), format with `cljfmt.core/reformat-string`, write `target/parity/jvm/<fixture>__<config>.out`. Refer to private vars with `#'` where needed.
  Run: `clojure -M:parity -m parity.jvm && ls target/parity/jvm | wc -l`
  Expected: `30` files (5 fixtures × 6 configs).

- [x] **Step 3: JS side and diff**
  `scripts/parity/js.mjs`: same loop via `require("../../dist/cljfmt.js")`: `reformatString(text, readConfig(edn))`, write `target/parity/js/…`. `bb parity`: depends on `build`; runs both sides, then compares file by file; on any mismatch prints `diff -u` and exits 1; prints `parity: N/N identical` on success. `bb check` = `build`, `test`, `parity`.
  Run: `bb check`
  Expected: `parity: 30/30 identical`. If a config-reading difference appears (not a formatting one), fix `read-config` in the wrapper — the JVM path is the spec. If a *formatting* difference appears, that is a cljfmt CLJS-branch divergence: record it in README under "Known divergences" only after confirming against cljfmt's source, and add a wrapper shim only if it is as bounded as the alias one.

- [x] **Step 4: Commit**
  `git add -A && git commit -m "Add JVM-vs-JS parity harness and bb check"`

### Task 7: CI

**Files:**
- Create: `.github/workflows/ci.yml`, `AGENTS.md`

- [x] **Step 1: ci.yml**
  Trigger: push and pull_request on `master`. Steps: `actions/checkout@v5`, `jdx/mise-action@v3.6.1` (cache on), `npm ci`, `bb check`. Cache `~/.m2` keyed on `deps.edn` (`actions/cache@v4`) to keep runs short.

- [x] **Step 2: AGENTS.md**
  Short, in clj-pulse's style: verification (`bb check` before claiming anything works; what each of `test` / `parity` covers), invariants (deps.edn is the only place the cljfmt version lives; the version literal in `core_test.cljs` must be bumped with it; `:exports`, `index.d.ts` and README must list the same six names; tags/assets are immutable; no filesystem access in the library; `vendor/` is disposable).

- [x] **Step 3: Push and watch**
  Run: `git add -A && git commit -m "Add CI workflow and AGENTS.md" && git push && gh run watch --exit-status`
  Expected: the `Check` run passes. Fix and re-push until green.

### Task 8: Release workflow and first release

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `bb.edn`, `README.md`

- [ ] **Step 1: bb tag**
  Port clj-pulse's `tag` task: read `version` from `package.json` (`cheshire` is built into bb), refuse if the working tree is dirty or the tag exists, `git tag v<ver>` and push the tag.

- [ ] **Step 2: release.yml**
  Trigger: `push: tags: ["v*"]`. Steps: checkout, mise-action, `npm ci`, `bb check`, `bb pack`, `sha256sum *.tgz > checksums.txt`, `softprops/action-gh-release@v2` with `generate_release_notes: true` and `files: abogoyavlensky-cljfmt-js-*.tgz, checksums.txt`. Needs `permissions: contents: write`.

- [ ] **Step 3: README release section**
  Document: bump `package.json` version → commit → `bb tag` → CI publishes; assets are immutable, fix forward.

- [ ] **Step 4: Release v0.1.0**
  Run: `git add -A && git commit -m "Add release workflow" && git push && bb tag && gh run watch --exit-status && gh release view v0.1.0 --json assets --jq '.assets[].name'`
  Expected: `abogoyavlensky-cljfmt-js-0.1.0.tgz` and `checksums.txt`.

- [ ] **Step 5: Consumer smoke test against the published URL**
  Run: `D=$(mktemp -d) && cd $D && npm init -y >/dev/null && npm install https://github.com/abogoyavlensky/cljfmt-js/releases/download/v0.1.0/abogoyavlensky-cljfmt-js-0.1.0.tgz && node -e 'console.log(require("@abogoyavlensky/cljfmt-js").cljfmtVersion)'`
  Expected: `0.16.5`. Note the `integrity` field now present in that temp `package-lock.json` — this is what makes asset immutability mandatory.

### Task 9: Weekly cljfmt update PR

**Files:**
- Create: `.github/workflows/cljfmt-update.yml`
- Modify: `bb.edn`, `README.md`

- [ ] **Step 1: outdated task**
  `bb outdated`: `clojure -M:outdated --focus=dev.weavejester/cljfmt`. Run it once locally to confirm antq resolves and reports up to date. antq exits non-zero when something is outdated — the workflow must not treat that as failure.

- [ ] **Step 2: Workflow**
  Trigger: `schedule: cron "0 6 * * 1"` and `workflow_dispatch`. `permissions: contents: write, pull-requests: write`. Steps: checkout, mise-action, `npm ci`, `clojure -M:outdated --upgrade --force --focus=dev.weavejester/cljfmt || true`; `git diff --quiet deps.edn && exit 0` style gate (use a step output `changed`); if changed: also update the version literal in `test/cljfmt_js/core_test.cljs` and the README version table row with `sed` from the new version (read it back out of `deps.edn`), run `bb check` with `continue-on-error: true` capturing the outcome, then `peter-evans/create-pull-request@v8` with `branch: cljfmt-update`, `title: "Update cljfmt to <ver>"`, body containing the `bb check` verdict (✅/❌) and a link to the run, `delete-branch: true`.

- [ ] **Step 3: Exercise the PR path once**
  On a throwaway branch set `deps.edn` to `0.16.4` (and the test literal), push it, `gh workflow run cljfmt-update.yml --ref <branch>`, `gh run watch`, confirm a PR appears with a ✅ body and the diff touches exactly `deps.edn`, the test literal and README row. Close the PR, delete both branches.
  Then `gh workflow run cljfmt-update.yml` on master: expected to finish with no PR (already 0.16.5).

- [ ] **Step 4: README update-process section and commit**
  `git add -A && git commit -m "Add weekly cljfmt update workflow" && git push`

### Task 10: Final docs pass

**Files:**
- Modify: `README.md`, `AGENTS.md`

- [ ] **Step 1: README complete**
  Sections: What this is (and what it is not), Install (release URL), API, `readNsContext` and windowed formatting, Versioning table, Development (`mise install`, `npm ci`, `bb check`; what `vendor/` is), Release process, cljfmt update process, Known divergences (empty or whatever Task 6 found), License (MIT + EPL notice pointer). Apply /writing-clearly.

- [ ] **Step 2: Commit and push**
  `git commit -am "Complete README" && git push && gh run watch --exit-status`
  Expected: CI green.
