#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB_PATH = ROOT / "ai" / "indexes" / "cold-memory.db"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Query the derived AI cold-search SQLite database.")
    parser.add_argument("query", help="FTS query text to search for.")
    parser.add_argument("--limit", type=int, default=8, help="Maximum number of matches to return.")
    parser.add_argument("--db", default=str(DEFAULT_DB_PATH), help="Path to the SQLite database.")
    return parser.parse_args()


def table_exists(cursor: sqlite3.Cursor, name: str) -> bool:
    row = cursor.execute(
        "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?",
        (name,),
    ).fetchone()
    return row is not None


def to_fts_query(query: str) -> str:
    tokens = re.findall(r"[A-Za-z0-9_]+", query)
    return " AND ".join(f'"{token}"' for token in tokens)


def main() -> int:
    args = parse_args()
    db_path = Path(args.db)
    if not db_path.exists():
        print(f"[ai-search] missing database: {db_path}")
        return 1

    connection = sqlite3.connect(db_path)
    try:
        cursor = connection.cursor()
        if table_exists(cursor, "document_fts"):
            fts_query = to_fts_query(args.query)
            if not fts_query:
                print("[ai-search] query produced no searchable terms")
                return 1
            rows = cursor.execute(
                """
                SELECT documents.path,
                       documents.title,
                       documents.source_kind,
                       documents.load_tier,
                       documents.sort_ts,
                       documents.line_start,
                       documents.line_end,
                       snippet(document_fts, 4, '[', ']', ' ... ', 18) AS summary
                FROM document_fts
                JOIN documents ON document_fts.rowid = documents.id
                WHERE document_fts MATCH ?
                ORDER BY documents.priority,
                         bm25(document_fts),
                         CASE WHEN documents.sort_ts = '' THEN 1 ELSE 0 END,
                         documents.sort_ts DESC,
                         documents.path,
                         documents.line_start
                LIMIT ?
                """,
                (fts_query, args.limit),
            ).fetchall()
        else:
            like_query = f"%{args.query}%"
            rows = cursor.execute(
                """
                SELECT path,
                       title,
                       source_kind,
                       load_tier,
                       sort_ts,
                       line_start,
                       line_end,
                       substr(content, 1, 220)
                FROM documents
                WHERE title LIKE ? OR content LIKE ?
                ORDER BY priority,
                         CASE WHEN sort_ts = '' THEN 1 ELSE 0 END,
                         sort_ts DESC,
                         path,
                         line_start
                LIMIT ?
                """,
                (like_query, like_query, args.limit),
            ).fetchall()
    finally:
        connection.close()

    if not rows:
        print("[ai-search] no matches")
        return 1

    for index, row in enumerate(rows, start=1):
        path, title, source_kind, load_tier, sort_ts, line_start, line_end, summary = row
        print(f"{index}. {path}:{line_start}-{line_end}")
        print(f"   title: {title}")
        print(f"   kind: {source_kind} / {load_tier or 'n/a'}")
        if sort_ts:
            print(f"   ts: {sort_ts}")
        print(f"   hit: {summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
