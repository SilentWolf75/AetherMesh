const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(root, "web-flasher", "index.html"), "utf8");
const match = html.match(/<script type="module">([\s\S]*?)<\/script>/);
if (!match) throw new Error("Module script not found in web flasher");
const source = match[1].replace(/^\s*import\s+.*?;\s*$/m, "");
new Function(source);
console.log("Web flasher module syntax verified.");
