#!/usr/bin/env python3
"""Refresh the Graph reader's embedded payload from canonical Markdown."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "markdown" / "Striver_Graph_DSA_FINAL_Deep_Dive.md"
READER = ROOT / "html" / "index.html"
START = "const markdown="


def payload_bounds(document: str) -> tuple[int, int, str]:
    start = document.index(START) + len(START)
    value, relative_end = json.JSONDecoder().raw_decode(document[start:])
    if not isinstance(value, str):
        raise SystemExit("embedded Markdown payload is not a string")
    return start, start + relative_end, value


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    document = READER.read_text(encoding="utf-8")
    start, end, _ = payload_bounds(document)
    rebuilt = document[:start] + json.dumps(source, ensure_ascii=False) + document[end:]
    READER.write_text(rebuilt, encoding="utf-8")

    verified = READER.read_text(encoding="utf-8")
    if payload_bounds(verified)[2] != source:
        raise SystemExit("embedded Markdown payload differs from canonical source")
    print(f"PASS reader payload chars={len(source)}")


if __name__ == "__main__":
    main()
