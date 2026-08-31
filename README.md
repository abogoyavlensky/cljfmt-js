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

## Release

1. Bump `version` in `package.json` (and the table above).
2. Commit and push; CI must be green.
3. `bb tag` — tags `v<version>` and pushes it. `release.yml` then re-runs
   `bb check`, packs the tarball, and attaches it plus `checksums.txt` to a
   GitHub Release.

`bb tag` refuses to run on a dirty tree or over an existing tag. Assets are
immutable because consumers pin a URL and an integrity hash: to correct a bad
release, bump the version and release again.

## License

MIT — see `LICENSE`. The distributed bundle embeds cljfmt and rewrite-clj, both
EPL-1.0, and `src/cljfmt_js/ns_aliases.cljs` ports code from cljfmt. See
`NOTICE`.
