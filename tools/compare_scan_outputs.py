#!/usr/bin/env python3
"""
Golden master anti-regressione AccessScope (piano M0-R1).

Confronta due output di scansione in formato JSON e segnala derive rispetto alla
baseline (default: tolleranza zero). Sorgenti supportate:

  - JSON del bridge: `adb shell content query --uri \
      content://dev.accessscope.scanner.results/latest?package=<pkg>`
  - JSON sessione dallo store: `filesDir/scan_history/sessions/<id>.json`
    (recuperabile con `adb exec-out run-as dev.accessscope.scanner cat ...` su build debug)

Uso:
  python3 tools/compare_scan_outputs.py --baseline reports/golden/v1.3.0/nexi_home.json \
      --current out/nexi_home.json

Opzioni:
  --tolerance-score N   Delta di punteggio accettato (default 0).
  --ignore-counts       Non confrontare uniqueScreens/scanAnalyses/findings.
  --approve             Stampa il diff e salva il current come nuova baseline
                        (procedura "approvazione deriva" del piano).

Exit code: 0 = nessuna deriva; 1 = deriva rilevata; 2 = errore input.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class Canon:
    """Forma canonica di una scansione per il confronto anti-regressione."""

    score: int
    unique_screens: int
    scan_analyses: int
    findings_count: int
    violations: frozenset[str] = field(default_factory=frozenset)
    screens: frozenset[str] = field(default_factory=frozenset)


def _violation_key(v: dict[str, Any]) -> str:
    """Chiave stabile: preferisce dedupeKey se presente (store completo)."""
    dedupe = v.get("dedupeKey")
    if isinstance(dedupe, str) and dedupe.strip():
        return dedupe.strip()
    return "|".join(
        [
            str(v.get("type", "?")),
            str(v.get("severity", "?")),
            str(v.get("viewId") or "—"),
            str(v.get("screenTitle") or v.get("sectionTitle") or "?"),
        ]
    )


def canonize(payload: dict[str, Any]) -> Canon:
    """Normalizza un JSON bridge/store in forma canonica confrontabile."""
    violations_raw = payload.get("violations") or []
    findings_raw = payload.get("screenReaderFindings") or []
    screens_raw = payload.get("visitedScreens") or []
    return Canon(
        score=int(payload.get("score", 0)),
        unique_screens=int(payload.get("uniqueScreens", 0)),
        scan_analyses=int(payload.get("scanAnalyses", 0)),
        findings_count=len(findings_raw),
        violations=frozenset(_violation_key(v) for v in violations_raw),
        screens=frozenset(
            f"{s.get('fingerprint', '?')}|{s.get('title', '?')}" for s in screens_raw
        ),
    )


def _load(path: str) -> dict[str, Any]:
    with open(path, encoding="utf-8") as fh:
        text = fh.read().strip()
    # L'output `content query` di adb può prefiggere "Row: 0 result=" o simili.
    start = text.find("{")
    if start == -1:
        raise ValueError(f"Nessun JSON trovato in {path}")
    return json.loads(text[start:])


def main() -> int:
    parser = argparse.ArgumentParser(description="Confronto golden master scansioni AccessScope")
    parser.add_argument("--baseline", required=True, help="JSON della baseline (es. v1.3.0)")
    parser.add_argument("--current", required=True, help="JSON della scansione corrente")
    parser.add_argument("--tolerance-score", type=int, default=0)
    parser.add_argument("--ignore-counts", action="store_true")
    parser.add_argument("--approve", action="store_true", help="Promuove current a nuova baseline")
    args = parser.parse_args()

    try:
        base = canonize(_load(args.baseline))
        cur = canonize(_load(args.current))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERRORE lettura input: {exc}", file=sys.stderr)
        return 2

    drift: list[str] = []

    score_delta = cur.score - base.score
    if abs(score_delta) > args.tolerance_score:
        drift.append(f"score: baseline {base.score} vs current {cur.score} (delta {score_delta:+d})")

    if not args.ignore_counts:
        if cur.unique_screens != base.unique_screens:
            drift.append(f"uniqueScreens: {base.unique_screens} → {cur.unique_screens}")
        if cur.findings_count != base.findings_count:
            drift.append(f"screenReaderFindings: {base.findings_count} → {cur.findings_count}")

    added = sorted(cur.violations - base.violations)
    removed = sorted(base.violations - cur.violations)
    for key in added:
        drift.append(f"+ VIOLAZIONE NUOVA: {key}")
    for key in removed:
        drift.append(f"- VIOLAZIONE PERSA: {key}")

    if cur.screens != base.screens:
        new_screens = sorted(cur.screens - base.screens)
        lost_screens = sorted(base.screens - cur.screens)
        for s in new_screens:
            drift.append(f"+ schermata nuova: {s}")
        for s in lost_screens:
            drift.append(f"- schermata persa: {s}")

    if not drift:
        print("OK: nessuna deriva rispetto alla baseline.")
        return 0

    print(f"DERIVA RILEVATA ({len(drift)} differenze):")
    for line in drift:
        print(f"  {line}")

    if args.approve:
        shutil.copyfile(args.current, args.baseline)
        print(f"\nDeriva approvata: baseline aggiornata → {args.baseline}")
        return 0

    print("\nSe la deriva è un miglioramento intenzionale, riesegui con --approve")
    print("e documenta il motivo nel task (piano M0-R1).")
    return 1


if __name__ == "__main__":
    sys.exit(main())
