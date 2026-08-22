"""Real SQLite migration test: open an old schema, apply onUpgrade SQL, assert rows survive.

Does not hardcode DATABASE_VERSION — adding migration 23 must not require editing this file.
"""

from __future__ import annotations

import re
import sqlite3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DB_HELPER = (
    ROOT
    / "app"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "silentwolf75"
    / "aethermesh"
    / "data"
    / "DatabaseHelper.kt"
)

CONST_RE = re.compile(r'const val (TABLE_\w+|COL_\w+)\s*=\s*"([^"]+)"')


def load_constants(text: str) -> dict[str, str]:
    return {name: value for name, value in CONST_RE.findall(text)}


def substitute_constants(sql: str, constants: dict[str, str]) -> str:
    for name in sorted(constants, key=len, reverse=True):
        sql = sql.replace(f"${name}", constants[name])
    return sql


def strip_kotlin_string(raw: str) -> str:
    raw = raw.replace(r"\"", '"')
    lines = list(raw.splitlines())
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    if not lines:
        return ""
    indents = [len(ln) - len(ln.lstrip(" ")) for ln in lines if ln.strip()]
    pad = min(indents) if indents else 0
    return "\n".join(ln[pad:] if len(ln) >= pad else ln for ln in lines).strip()


def extract_string_literal(expr: str) -> str | None:
    expr = expr.strip()
    # Concatenated string fragments: "a" + "b" or """a""" + "b"
    parts = re.split(r"\s*\+\s*", expr)
    if len(parts) > 1:
        chunks: list[str] = []
        for part in parts:
            lit = extract_string_literal(part)
            if lit is None:
                return None
            chunks.append(lit)
        return "".join(chunks)

    if expr.startswith('"""'):
        end = expr.find('"""', 3)
        if end < 0:
            return None
        return strip_kotlin_string(expr[3:end])
    if expr.startswith('"'):
        m = re.match(r'"((?:\\.|[^"\\])*)"', expr)
        return m.group(1).replace(r"\"", '"') if m else None
    return None


def extract_on_upgrade_body(text: str) -> str:
    start = text.find("override fun onUpgrade(")
    if start < 0:
        raise AssertionError("onUpgrade not found")
    brace = text.find("{", start)
    depth = 0
    for i, ch in enumerate(text[brace:], start=brace):
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1 : i]
    raise AssertionError("unbalanced braces in onUpgrade")


def extract_mesh_diagnostics_sql(text: str, constants: dict[str, str]) -> str:
    m = re.search(
        r"private fun meshDiagnosticsTableSql\(\)\s*=\s*\"\"\"(.*?)\"\"\"\.trimIndent\(\)",
        text,
        re.DOTALL,
    )
    if not m:
        raise AssertionError("meshDiagnosticsTableSql not found")
    return substitute_constants(strip_kotlin_string(m.group(1)), constants)


def resolve_exec_arg(arg: str, block: str, text: str, constants: dict[str, str]) -> str | None:
    arg = arg.strip()
    if arg.endswith(".trimIndent()"):
        arg = arg[: -len(".trimIndent()")].strip()
    if arg == "meshDiagnosticsTableSql()":
        return extract_mesh_diagnostics_sql(text, constants)

    lit = extract_string_literal(arg)
    if lit is not None:
        return substitute_constants(lit, constants)

    # val name = """...""" or val name = "..."
    m = re.search(
        rf"(?:val|var)\s+{re.escape(arg)}\s*=\s*(\"\"\".*?\"\"\"|\".*?\")(?:\.trimIndent\(\))?",
        block,
        re.DOTALL,
    )
    if m:
        lit = extract_string_literal(m.group(1))
        if lit is not None:
            return substitute_constants(lit, constants)
    return None


def find_exec_sql_calls(block: str) -> list[str]:
    """Return argument expressions of each db.execSQL(...) in block (paren-aware)."""
    args: list[str] = []
    needle = "db.execSQL("
    start = 0
    while True:
        idx = block.find(needle, start)
        if idx < 0:
            break
        i = idx + len(needle)
        depth = 1
        in_triple = False
        in_single = False
        escape = False
        arg_start = i
        while i < len(block) and depth > 0:
            ch = block[i]
            nxt = block[i : i + 3]
            if in_triple:
                if nxt == '"""':
                    in_triple = False
                    i += 3
                    continue
            elif in_single:
                if escape:
                    escape = False
                elif ch == "\\":
                    escape = True
                elif ch == '"':
                    in_single = False
            else:
                if nxt == '"""':
                    in_triple = True
                    i += 3
                    continue
                if ch == '"':
                    in_single = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        args.append(block[arg_start:i])
                        break
            i += 1
        start = i + 1
    return args


def extract_migrations(text: str) -> list[tuple[int, list[str]]]:
    body = extract_on_upgrade_body(text)
    constants = load_constants(text)
    parts = re.split(r"if \(oldVersion < (\d+)\) \{", body)
    migrations: list[tuple[int, list[str]]] = []
    i = 1
    while i + 1 < len(parts):
        version = int(parts[i])
        rest = parts[i + 1]
        depth = 1
        end = 0
        for j, ch in enumerate(rest):
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = j
                    break
        block = rest[:end]
        stmts: list[str] = []
        for arg in find_exec_sql_calls(block):
            sql = resolve_exec_arg(arg, block, text, constants)
            if sql:
                stmts.append(sql)
        migrations.append((version, stmts))
        i += 2
    migrations.sort(key=lambda item: item[0])
    return migrations


def create_v1_schema(conn: sqlite3.Connection) -> None:
    """Minimal schema as of DATABASE_VERSION before migration 2."""
    conn.executescript(
        """
        CREATE TABLE messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender_id INTEGER,
            recipient_id INTEGER,
            content TEXT,
            timestamp INTEGER,
            channel TEXT
        );
        CREATE TABLE nodes (
            node_id INTEGER PRIMARY KEY,
            name TEXT,
            battery INTEGER,
            latitude REAL,
            longitude REAL,
            last_active INTEGER,
            model TEXT
        );
        """
    )


class DatabaseMigrationSqliteTest(unittest.TestCase):
    def test_upgrade_preserves_rows_from_v1(self) -> None:
        text = DB_HELPER.read_text(encoding="utf-8")
        migrations = extract_migrations(text)
        self.assertGreaterEqual(len(migrations), 1)
        versions = [v for v, _ in migrations]
        self.assertEqual(versions[0], 2)
        self.assertEqual(versions, list(range(2, versions[-1] + 1)))

        conn = sqlite3.connect(":memory:")
        try:
            create_v1_schema(conn)
            conn.execute(
                "INSERT INTO messages (sender_id, recipient_id, content, timestamp, channel) "
                "VALUES (?, ?, ?, ?, ?)",
                (0x1111, 0x2222, "survive-me", 1_700_000_000_000, "General"),
            )
            conn.execute(
                "INSERT INTO nodes (node_id, name, battery, latitude, longitude, last_active, model) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                (0xABCDEF01, "FieldNode", 88, 30.1, -97.7, 1_700_000_000_000, "Heltec"),
            )
            conn.commit()

            for _version, stmts in migrations:
                for sql in stmts:
                    try:
                        conn.execute(sql)
                    except sqlite3.OperationalError as exc:
                        msg = str(exc).lower()
                        if "duplicate column" in msg or "already exists" in msg:
                            continue
                        raise
            conn.commit()

            msg = conn.execute(
                "SELECT content FROM messages WHERE content = ?", ("survive-me",)
            ).fetchone()
            self.assertIsNotNone(msg)
            self.assertEqual(msg[0], "survive-me")

            node = conn.execute(
                "SELECT name, battery FROM nodes WHERE node_id = ?", (0xABCDEF01,)
            ).fetchone()
            self.assertIsNotNone(node)
            self.assertEqual(node[0], "FieldNode")
            self.assertEqual(node[1], 88)

            cols = {row[1] for row in conn.execute("PRAGMA table_info(nodes)").fetchall()}
            self.assertIn("last_position_at", cols)
            self.assertIn("short_name", cols)
        finally:
            conn.close()


if __name__ == "__main__":
    unittest.main()
