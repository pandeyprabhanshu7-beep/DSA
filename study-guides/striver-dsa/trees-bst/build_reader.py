#!/usr/bin/env python3
"""Refresh the responsive reader's embedded payload from canonical Markdown."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "markdown" / "Striver_Trees_BST_FINAL_Deep_Dive.md"
READER = ROOT / "html" / "index.html"
START = "const markdown="
END = ";\nmarked.setOptions"


def payload(document: str) -> str:
    start = document.index(START) + len(START)
    end = document.index(END, start)
    return json.loads(document[start:end])


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    document = READER.read_text(encoding="utf-8")
    start = document.index(START) + len(START)
    end = document.index(END, start)
    rebuilt = document[:start] + json.dumps(source, ensure_ascii=False) + document[end:]
    READER.write_text(rebuilt, encoding="utf-8")

    verified = READER.read_text(encoding="utf-8")
    if payload(verified) != source:
        raise SystemExit("embedded Markdown payload differs from canonical source")
    print(f"PASS reader payload chars={len(source)}")


if __name__ == "__main__":
    main()
