const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");
const root = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(root, "web-flasher/index.html"), "utf8");
if (!html.includes('src="./flasher.js"')) throw new Error("Flasher module is not wired into the page");
for (const file of ["flasher.js", "flashplan.js"]) {
  const result = spawnSync(process.execPath, ["--check", path.join(root, "web-flasher", file)], { encoding: "utf8" });
  if (result.status !== 0) throw new Error(result.stderr || `${file} syntax check failed`);
}
console.log("Web flasher module syntax verified.");
