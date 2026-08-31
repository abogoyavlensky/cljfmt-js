# cljfmt-js

[cljfmt](https://github.com/weavejester/cljfmt) compiled to JavaScript with
shadow-cljs. Format Clojure source in-process, synchronously, and get
byte-for-byte what the JVM `cljfmt` CLI produces.

Built for the clojure-pulse-vscode extension, which needs cljfmt's exact
output on every keystroke — for indent-on-Enter and for Format Document.

## What this is not

A pure library. It never touches the filesystem. Finding a `.cljfmt.edn`,
deciding which one applies, and caching the result are the consumer's job.
It reimplements no formatting rule: every decision comes from cljfmt itself.

## Install

Releases are tarballs on GitHub, not npm packages:

```sh
npm install https://github.com/abogoyavlensky/cljfmt-js/releases/download/v0.1.0/abogoyavlensky-cljfmt-js-0.1.0.tgz
```

Tags and release assets are immutable, so your lockfile's integrity hash keeps
working. Mistakes are fixed forward with a new version, never by replacing an
asset.

## API

```ts
import { readConfig, reformatString } from "@abogoyavlensky/cljfmt-js";

const config = readConfig('{:function-arguments-indentation :cursive}');
reformatString("(let [a 1]\n(inc a))", config);
// => "(let [a 1]\n  (inc a))"
```

| Export | Signature |
| --- | --- |
| `readConfig` | `(edn: string) => Config` |
| `mergeConfig` | `(base: Config, override: Config) => Config` |
| `defaultConfig` | `Config` |
| `readNsContext` | `(source: string) => NsContext` |
| `reformatString` | `(source: string, config?: Config, nsContext?: NsContext) => string` |
| `cljfmtVersion` | `string` |

`readConfig` parses a `.cljfmt.edn` / `cljfmt.edn` file's contents, supports the
`#re "…"` tag, and applies cljfmt's legacy-key conversion. `mergeConfig` merges
two configs, `override` winning. `reformatString` throws on source it cannot
parse; `readConfig` throws on invalid EDN. Neither guesses — the caller owns
fallback behavior.

### Configs are opaque handles

A `Config` and an `NsContext` are ClojureScript maps, not plain JavaScript
objects. Build them with `readConfig` and `readNsContext` and pass them
straight back in. Do not construct, inspect or serialize them: cljfmt configs
have regex and symbol keys that no JSON round-trip survives.

### Formatting a window

Indenting on Enter means formatting a small sub-form, not the whole file. That
window has no `ns` form, so cljfmt cannot resolve `ml/mything` against an
indent key like `{my.lib/mything [[:inner 0]]}`, and the result drifts from
what the CLI would produce for the same file.

Derive the context once per file and pass it with each window:

```ts
const ctx = readNsContext(wholeFileText);        // aliases, refers, ns name
reformatString(windowText, config, ctx);
```

Precedence runs low to high: aliases and refers derived from the text you pass
to `reformatString`, then `nsContext`, then your `config`'s own `:alias-map` /
`:refer-map`, which win — cljfmt's own order with the context slotted in.
Formatting a whole file needs no context: cljfmt finds the `ns` form itself.

## Versioning

`cljfmtVersion` reports the bundled cljfmt; the package carries its own semver.

| cljfmt-js | cljfmt |
| --- | --- |
| 0.1.0 | 0.16.5 |

## Development

```sh
mise install   # java, clojure, node, babashka — versions pinned in mise.toml
npm ci
bb check       # build + tests + parity. CI runs exactly this.
```

Individual tasks: `bb build`, `bb test`, `bb parity`, `bb pack`, `bb outdated`,
`bb tag`. Run `bb tasks` for the list.

`bb test` runs two suites in one node process: this wrapper's tests, and
cljfmt's own `cljfmt.core-test` compiled under our ClojureScript build. Those
upstream tests do not ship in the jar, so `bb vendor` shallow-clones cljfmt at
the pinned tag into `vendor/` first. `vendor/` is gitignored and disposable —
delete it and the next `bb test` re-creates it.

`bb parity` is the check that really protects the port: it formats every
`test/fixtures/*` under every `test/fixtures/configs/*.edn` twice — once with
real JVM cljfmt through `cljfmt.config/load-config`, the CLI's own path, and
once through the built `dist/cljfmt.js` — and diffs the two trees. There are no
checked-in expected outputs; the JVM is the oracle and `deps.edn` pins the one
version both sides use.

## Release

1. Bump `version` in `package.json` (and the table above).
2. Commit and push; CI must be green.
3. `bb tag` — tags `v<version>` and pushes it. `release.yml` then re-runs
   `bb check`, packs the tarball, and attaches it plus `checksums.txt` to a
   GitHub Release.

`bb tag` refuses to run on a dirty tree or over an existing tag. Assets are
immutable because consumers pin a URL and an integrity hash: to correct a bad
release, bump the version and release again.

## Keeping cljfmt current

`cljfmt-update.yml` runs every Monday (and on demand). antq bumps
`dev.weavejester/cljfmt` in `deps.edn`; if that changed, the workflow rewrites
the version literal in `test/cljfmt_js/core_test.cljs` and the table above,
runs `bb check` itself, and opens a PR whose body carries the verdict. It runs
the checks in-job because a PR opened with `GITHUB_TOKEN` does not trigger
`ci.yml`.

Merging is manual. A ❌ verdict means cljfmt changed formatting behavior or the
internals the wrapper leans on — read the run log before merging. After
merging, bump `version` in `package.json` and release.

> **Repository setting required.** Opening the PR needs *Settings → Actions →
> General → Workflow permissions → "Allow GitHub Actions to create and approve
> pull requests"*. Without it the workflow does all its work and then fails the
> last step with `GitHub Actions is not permitted to create or approve pull
> requests`.

## Known divergences

None. The current fixture matrix (5 files x 6 configs) is byte-identical to
JVM cljfmt, and cljfmt's own test suite passes unmodified under this build.

Two behaviors are worth knowing about, neither of them a divergence:

- Formatting a window without an `NsContext` can differ from formatting the
  whole file, because the window has no `ns` form to resolve names against.
  That is what `readNsContext` is for.
- cljfmt compiles its `ns`-form parsing on the JVM only, so this package ports
  it (`src/cljfmt_js/ns_aliases.cljs`). Without that port, any config with a
  namespace-qualified indent key would silently format differently here than
  in the CLI. `bb parity` covers exactly this case.

## License

MIT — see `LICENSE`. The distributed bundle embeds cljfmt and rewrite-clj, both
EPL-1.0, and `src/cljfmt_js/ns_aliases.cljs` ports code from cljfmt. See
`NOTICE`.
