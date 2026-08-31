// JS half of the differential parity harness.
//
// Formats every test/fixtures file under every test/fixtures/configs file
// through the built dist/cljfmt.js and writes the results to
// target/parity/js, using the same file names as scripts/parity/parity/jvm.clj.
// `bb parity` diffs the two trees; the JVM side is the oracle.

import { createRequire } from "node:module";
import fs from "node:fs";
import path from "node:path";

const require = createRequire(import.meta.url);
const root = path.resolve(import.meta.dirname, "../..");
const cljfmt = require(path.join(root, "dist/cljfmt.js"));

const fixturesDir = path.join(root, "test/fixtures");
const configsDir = path.join(root, "test/fixtures/configs");
const outDir = path.join(root, "target/parity/js");

const fixtures = fs.readdirSync(fixturesDir).filter((f) => /\.cljc?$/.test(f)).sort();
const configs = fs.readdirSync(configsDir).filter((f) => f.endsWith(".edn")).sort();

if (fixtures.length === 0) throw new Error("no fixtures found");
if (configs.length === 0) throw new Error("no configs found");

fs.mkdirSync(outDir, { recursive: true });

for (const fixture of fixtures) {
  const source = fs.readFileSync(path.join(fixturesDir, fixture), "utf8");
  for (const config of configs) {
    const edn = fs.readFileSync(path.join(configsDir, config), "utf8");
    const out = cljfmt.reformatString(source, cljfmt.readConfig(edn));
    const name = `${fixture}__${config.replace(/\.edn$/, "")}.out`;
    fs.writeFileSync(path.join(outDir, name), out);
  }
}

console.log(`parity/js: ${fixtures.length * configs.length} files written to ${outDir}`);
