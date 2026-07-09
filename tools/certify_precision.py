#!/usr/bin/env python3
"""
Certifica la precisione di un report AccessScope_Reliability_*.md confrontandolo con il codice sorgente.

Obiettivo: prevenzione. Se AccessScope dice "0", deve essere difficile che scanner esterni trovino decine di issue.

Uso:
  python3 tools/certify_precision.py --md /path/report.md --code /path/to/target/app/src

Regole:
  - Conta solo violazioni con viewId diverso da "—" (verificabili nel codice).
  - Verifica presenza dell'ID nel codice (XML) con match @+id/<id>.
  - Per categorie runtime (es. disabilitato) la presenza nel layout è "verificabile", ma la severità può variare.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
from dataclasses import dataclass
from typing import Iterable, List, Tuple


@dataclass(frozen=True)
class Entry:
    screen: str
    vtype: str
    view_id: str


def parse_md(md_text: str) -> List[Entry]:
    entries: List[Entry] = []
    screen = ""
    for line in md_text.splitlines():
        m = re.match(r"^###\s+(.*)\s+\(\d+\)\s*$", line)
        if m:
            screen = m.group(1).strip()
            continue
        m = re.match(r"^- \*\*(.*?)\*\* \(`([^`]+)`\)", line)
        if m:
            entries.append(Entry(screen=screen, vtype=m.group(1).strip(), view_id=m.group(2).strip()))
    return entries


def rg_exists(code_root: pathlib.Path, view_id: str) -> bool:
    # Cerca @+id/<id> o @id/<id>
    pattern = rf"@[\+]?id/{re.escape(view_id)}\b"
    try:
        res = subprocess.run(
            ["rg", "-n", pattern, str(code_root)],
            capture_output=True,
            text=True,
            check=False,
        )
        return res.returncode == 0
    except FileNotFoundError:
        raise SystemExit("rg non trovato. Installa ripgrep o esegui in un ambiente con `rg` disponibile.")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", required=True, type=pathlib.Path)
    ap.add_argument("--code", required=True, type=pathlib.Path, help="root della cartella sorgente (es. app/src)")
    args = ap.parse_args()

    md_text = args.md.read_text(encoding="utf-8")
    entries = parse_md(md_text)

    verifiable = [e for e in entries if e.view_id != "—"]
    unverifiable = [e for e in entries if e.view_id == "—"]

    tp: List[Entry] = []
    fp: List[Entry] = []
    for e in verifiable:
        if rg_exists(args.code, e.view_id):
            tp.append(e)
        else:
            fp.append(e)

    denom = len(tp) + len(fp)
    precision = (len(tp) / denom) if denom else 1.0

    print(f"Total entries: {len(entries)}")
    print(f"Verifiable (viewId != '—'): {len(verifiable)}")
    print(f"Unverifiable (viewId == '—'): {len(unverifiable)}")
    print(f"TP (id found in code): {len(tp)}")
    print(f"FP (id NOT found in code): {len(fp)}")
    print(f"Certified precision: {precision:.2%}")

    if fp:
        print("\nFP list (not found in code):")
        for e in fp:
            print(f"- [{e.screen}] {e.vtype}: {e.view_id}")


if __name__ == "__main__":
    main()

