#!/usr/bin/env python3
"""Copy the household config into an app module and write its launcher label."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load():
    chosen = ROOT / "config" / "family.json"
    if not chosen.exists():
        chosen = ROOT / "config" / "family.example.json"
    return json.loads(chosen.read_text(encoding="utf-8")), chosen


def xml_escape(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;")
            .replace("'", "\\'").replace('"', '\\"'))


def main():
    kind, module = sys.argv[1], Path(sys.argv[2])
    cfg, source = load()
    parent = (cfg.get("parent") or {}).get("name") or "Alex"
    labels = {
        "notes": f"Notes from {parent}",
        "where": f"Where's {parent}",
        "weather": "Weather",
        "ticker": "Score Ticker",
        "launcher": "Family Home",
    }
    if kind == "launcher":
        raw = module / "res" / "raw"
        raw.mkdir(parents=True, exist_ok=True)
        (raw / "family.json").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
        family = (cfg.get("family") or {}).get("name") or "Example family"
        print(f"Hello, {family}")
        return
    assets = module / "src" / "main" / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    (assets / "family.json").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    values = module / "src" / "main" / "res" / "values"
    values.mkdir(parents=True, exist_ok=True)
    label = xml_escape(labels[kind])
    (values / "generated_family.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <string name="app_name">{label}</string>\n</resources>\n',
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
