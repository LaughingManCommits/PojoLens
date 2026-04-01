#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB_PATH = ROOT / "ai" / "indexes" / "cold-memory.db"
DEFAULT_TIERS = ["hot", "warm", "cold"]
SOURCE_KIND_PRIORITY = {
    "ai-hot-context": 0,
    "ai-validation-history": 1,
    "ai-core": 2,
    "ai-state": 3,
    "ai-orchestrator": 4,
    "ai-archive-summary": 5,
    "release-doc": 6,
    "process-doc": 6,
    "product-doc": 7,
    "readme": 7,
    "planning": 7,
    "ai-log": 8,
    "ai-log-archive": 9,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Query the derived AI cold-search SQLite database.")
    parser.add_argument("query", help="FTS query text to search for.")
    parser.add_argument("--limit", type=int, default=8, help="Maximum number of matches to return.")
    parser.add_argument("--db", default=str(DEFAULT_DB_PATH), help="Path to the SQLite database.")
    parser.add_argument("--tier", default="", help="Comma-separated load tiers to include: hot,warm,cold,archive.")
    parser.add_argument("--kind", default="", help="Comma-separated source kinds to include.")
    parser.add_argument("--path", dest="path_glob", action="append", default=[], help="Path glob to include; may be passed multiple times.")
    parser.add_argument("--json", action="store_true", help="Emit results as JSON.")
    return parser.parse_args()


def table_exists(cursor: sqlite3.Cursor, name: str) -> bool:
    row = cursor.execute(
        "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?",
        (name,),
    ).fetchone()
    return row is not None


def parse_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def resolve_tiers(args: argparse.Namespace, *, include_archive_fallback: bool = False) -> list[str]:
    explicit_tiers = parse_csv(args.tier)
    if explicit_tiers:
        return explicit_tiers
    if include_archive_fallback:
        return ["archive"]
    return list(DEFAULT_TIERS)


def to_fts_query(query: str) -> str:
    tokens = re.findall(r"[A-Za-z0-9_]+", query)
    return " AND ".join(f'"{token}"' for token in tokens)


def build_filters(
    args: argparse.Namespace,
    *,
    include_archive_fallback: bool = False,
) -> tuple[str, list[object]]:
    clauses: list[str] = []
    parameters: list[object] = []

    tiers = resolve_tiers(args, include_archive_fallback=include_archive_fallback)
    if tiers:
        clauses.append("documents.load_tier IN ({})".format(",".join("?" for _ in tiers)))
        parameters.extend(tiers)

    kinds = parse_csv(args.kind)
    if kinds:
        clauses.append("documents.source_kind IN ({})".format(",".join("?" for _ in kinds)))
        parameters.extend(kinds)

    path_globs = [glob for glob in args.path_glob if glob]
    if path_globs:
        clauses.append("(" + " OR ".join("documents.path GLOB ?" for _ in path_globs) + ")")
        parameters.extend(path_globs)

    if not clauses:
        return "", []
    return " AND " + " AND ".join(clauses), parameters


def row_to_payload(row: tuple[object, ...]) -> dict[str, object]:
    path, title, source_kind, load_tier, sort_ts, line_start, line_end, summary = row
    return {
        "path": path,
        "title": title,
        "sourceKind": source_kind,
        "loadTier": load_tier,
        "sortTs": sort_ts,
        "lineStart": line_start,
        "lineEnd": line_end,
        "summary": summary,
    }


def source_kind_order_sql() -> str:
    clauses = [
        f"WHEN '{source_kind}' THEN {priority}"
        for source_kind, priority in SOURCE_KIND_PRIORITY.items()
    ]
    return "CASE documents.source_kind " + " ".join(clauses) + " ELSE 50 END"


def execute_query(
    cursor: sqlite3.Cursor,
    args: argparse.Namespace,
    *,
    include_archive_fallback: bool = False,
) -> list[tuple[object, ...]]:
    filter_sql, filter_params = build_filters(args, include_archive_fallback=include_archive_fallback)
    source_kind_order = source_kind_order_sql()
    if table_exists(cursor, "document_fts"):
        fts_query = to_fts_query(args.query)
        if not fts_query:
            raise ValueError("[ai-search] query produced no searchable terms")
        return cursor.execute(
            f"""
            SELECT documents.path,
                   documents.title,
                   documents.source_kind,
                   documents.load_tier,
                   documents.sort_ts,
                   documents.line_start,
                   documents.line_end,
                   search.summary
            FROM documents
            JOIN (
                SELECT section_id,
                       bm25(document_fts) AS score,
                       snippet(document_fts, 6, '[', ']', ' ... ', 18) AS summary
                FROM document_fts
                WHERE document_fts MATCH ?
            ) AS search ON search.section_id = documents.section_id
            WHERE 1 = 1{filter_sql}
            ORDER BY documents.priority,
                     {source_kind_order},
                     search.score,
                     CASE WHEN documents.sort_ts = '' THEN 1 ELSE 0 END,
                     documents.sort_ts DESC,
                     documents.path,
                     documents.line_start
            LIMIT ?
            """,
            [fts_query, *filter_params, args.limit],
        ).fetchall()

    like_query = f"%{args.query}%"
    return cursor.execute(
        f"""
        SELECT documents.path,
               documents.title,
               documents.source_kind,
               documents.load_tier,
               documents.sort_ts,
               documents.line_start,
               documents.line_end,
               substr(documents.content, 1, 220)
        FROM documents
        WHERE (documents.title LIKE ? OR documents.content LIKE ?){filter_sql}
        ORDER BY documents.priority,
                 {source_kind_order},
                 CASE WHEN documents.sort_ts = '' THEN 1 ELSE 0 END,
                 documents.sort_ts DESC,
                 documents.path,
                 documents.line_start
        LIMIT ?
        """,
        [like_query, like_query, *filter_params, args.limit],
    ).fetchall()


def main() -> int:
    args = parse_args()
    db_path = Path(args.db)
    if not db_path.exists():
        print(f"[ai-search] missing database: {db_path}")
        return 1

    connection = sqlite3.connect(db_path)
    try:
        cursor = connection.cursor()
        try:
            rows = execute_query(cursor, args)
        except ValueError as exc:
            print(str(exc))
            return 1
        if not rows and not parse_csv(args.tier):
            rows = execute_query(cursor, args, include_archive_fallback=True)
    finally:
        connection.close()

    if not rows:
        print("[ai-search] no matches")
        return 1

    payload = [row_to_payload(row) for row in rows]
    if args.json:
        print(json.dumps(payload, indent=2))
        return 0

    for index, item in enumerate(payload, start=1):
        print(f"{index}. {item['path']}:{item['lineStart']}-{item['lineEnd']}")
        print(f"   title: {item['title']}")
        print(f"   kind: {item['sourceKind']} / {item['loadTier'] or 'n/a'}")
        if item["sortTs"]:
            print(f"   ts: {item['sortTs']}")
        print(f"   hit: {item['summary']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
