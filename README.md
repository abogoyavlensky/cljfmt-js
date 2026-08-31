# cljfmt-js

[cljfmt](https://github.com/weavejester/cljfmt) compiled to JavaScript with
shadow-cljs, so an editor extension can format Clojure source in-process —
synchronously, per keystroke — and get byte-for-byte the same output as the
JVM `cljfmt` CLI.

This is a pure library. It never touches the filesystem: finding a
`.cljfmt.edn` is the consumer's job, not this package's.

**Work in progress.**

## License

MIT (see `LICENSE`). The distributed bundle embeds cljfmt and rewrite-clj,
both EPL-1.0 — see `NOTICE`.
