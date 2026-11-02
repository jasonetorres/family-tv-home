#!/usr/bin/env python3
"""Fail if personal data from the original private setup is present.

The needles are built from pieces so this file is not a copy of those secrets.
This script is the only place those pieces are assembled, and it is skipped
when the tree is scanned.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {".git", "build", ".gradle", "uploads", ".signing", "dist", "__pycache__"}
SKIP_FILES = {
    Path("scripts/check_personal_data.py"),
}
TEXT_EXT = {
    ".kt", ".java", ".xml", ".txt", ".md", ".py", ".sh", ".json", ".kts",
    ".properties", ".html", ".css", ".js", ".gradle",
}


def j(*parts):
    return "".join(parts)


ZIP = j("145", "80")
NEEDLES = [
    j("dad", "notes-", "a4kwn", "fsxqogynmch"),
    j("vNbF", "vjH3", "gvLxnDvh8"),
    j("photos.app.", "goo.gl"),
    j("com.", "jason."),
    j("tv.", "torres."),
    j("torres", "tv"),
    j("Tor", "res"),
    j("Web", "ster"),
    ZIP,
    j("43.212", "3"),
    j("-77.43", "00"),
    j("43.210", "61"),
    j("-77.428", "99"),
    j("43.155"),
    j("-77.59", "7"),
    j("latitude=43.16"),
    j("beach", "body"),
    j("NFL:P", "IT"),
    j("NFL:B", "UF"),
    j("Utre", "cht"),
    j("Scotts", "dale"),
    j("Sedo", "na"),
    j("DL", "4655"),
    j("DL", "132"),
    j("WN", "1728"),
    j("WN", "389"),
    j("WN", "2470"),
    j("WN", "2450"),
    j("Roches", "ter, NY"),
    j("bc1q", "07hfz95crdalcrnywjavdpc4pdvllct83as5re"),
    j("0x4ed3", "aF428e938a0CC8e60df86D714B4a7F0928F0"),
]


def iter_files():
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts):
            continue
        rel = path.relative_to(ROOT)
        if rel in SKIP_FILES or path.suffix.lower() not in TEXT_EXT:
            continue
        if path.stat().st_size > 5_000_000:
            continue
        yield rel, path


def main():
    bad = []
    for rel, path in iter_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        for needle in NEEDLES:
            if needle == ZIP:
                if re.search(r"(?<!\d)14580(?!\d)", text):
                    bad.append(f"{rel}: {needle}")
                continue
            if needle in text:
                bad.append(f"{rel}: {needle}")
    if bad:
        print("Personal or upstream-tip strings found:")
        for line in bad:
            print(" ", line)
        return 1
    print(f"clean ({len(NEEDLES)} needles)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
