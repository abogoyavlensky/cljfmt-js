/**
 * cljfmt-js — cljfmt compiled to JavaScript.
 *
 * Configs and namespace contexts are opaque handles (ClojureScript maps).
 * They deliberately never cross the JS boundary as plain objects, so regex
 * and symbol keys survive intact: build them with `readConfig` /
 * `readNsContext` and pass them straight back in.
 */

export interface Config {
  readonly __brand: "cljfmt.Config";
}

export interface NsContext {
  readonly __brand: "cljfmt.NsContext";
}

/**
 * Parse the contents of a `.cljfmt.edn` / `cljfmt.edn` file. Supports the
 * `#re "…"` tag and applies cljfmt's legacy-key conversion.
 *
 * @throws Error if the string is not valid EDN.
 */
export function readConfig(edn: string): Config;

/** Plain merge; keys in `override` win (`cljfmt.config` semantics). */
export function mergeConfig(base: Config, override: Config): Config;

/** cljfmt's `default-options`. */
export const defaultConfig: Config;

/**
 * Namespace aliases, refers and name derived from a whole file's `ns` form.
 * Pass the result to `reformatString` when formatting a sub-form (a window)
 * that does not contain the `ns` form itself.
 */
export function readNsContext(source: string): NsContext;

/**
 * Format `source` exactly as the JVM cljfmt CLI would.
 *
 * @param config    defaults to `defaultConfig`.
 * @param nsContext namespace information for the file `source` was taken
 *                  from; only needed when `source` is a window.
 * @throws Error if `source` cannot be parsed (unbalanced delimiters etc.).
 */
export function reformatString(
  source: string,
  config?: Config,
  nsContext?: NsContext,
): string;

/** Version of the bundled cljfmt, e.g. `"0.16.5"`. */
export const cljfmtVersion: string;
