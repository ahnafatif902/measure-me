"""Convert variable fonts into the static TTFs the Android app bundles.

Sources:
  web/src/assets/fonts/font-2.woff2          Fraunces latin subset (wght + opsz)
  android/scripts/fonts_src/inter_var.ttf    Inter variable (opsz + wght), downloaded
                                             from google/fonts (ofl/inter)

The UI Design Specification v1.0 uses:
  Fraunces 400 @ opsz 88   result number 88/88 (tabular)
  Fraunces 500 @ opsz 40   home headline 40/42
  Fraunces 500 @ opsz 18   wordmark 18/24, mode card titles
  Inter    400 / 500 / 600 UI and body (13-15 px)

Android res/font assets must be static files, so we pin the variable axes
with fontTools. WOFF2 containers are converted to plain sfnt (flavor=None).

Requires: pip install fonttools brotli
Run from the repository root:  python android/scripts/make_fonts.py
"""

import os
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT_DIR = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")

FRAUNCES = "web/src/assets/fonts/font-2.woff2"
INTER = "android/scripts/fonts_src/inter_var.ttf"

JOBS = [
    (FRAUNCES, {"wght": 400, "opsz": 88}, "fraunces_400.ttf"),
    (FRAUNCES, {"wght": 500, "opsz": 40}, "fraunces_500.ttf"),
    (FRAUNCES, {"wght": 500, "opsz": 18}, "fraunces_500_text.ttf"),
    (INTER, {"wght": 400, "opsz": 15}, "inter_400.ttf"),
    (INTER, {"wght": 500, "opsz": 15}, "inter_500.ttf"),
    (INTER, {"wght": 600, "opsz": 15}, "inter_600.ttf"),
]


def main() -> int:
    os.makedirs(FONT_DIR, exist_ok=True)
    for src, axes, out_name in JOBS:
        src_path = os.path.join(ROOT, src.replace("/", os.sep))
        font = TTFont(src_path)
        available = {a.axisTag: (a.minValue, a.defaultValue, a.maxValue) for a in font["fvar"].axes}
        unknown = set(axes) - set(available)
        if unknown:
            print(f"!! {src}: axes {sorted(unknown)} not present (has {sorted(available)})")
            return 1
        instance = instantiateVariableFont(font, axes, inplace=False)
        # WOFF2 sources must be saved as plain sfnt for Android.
        instance.flavor = None
        dst_path = os.path.join(FONT_DIR, out_name)
        instance.save(dst_path)
        cmap = instance.getBestCmap()
        probes = {0x2013: "en-dash", 0x2019: "apostrophe", 0x00B7: "middot",
                  0x00B0: "degree", 0x00D7: "times"}
        missing = [label for cp, label in probes.items() if cp not in cmap]
        head = open(dst_path, "rb").read(4).hex(" ")
        print(f"{out_name}: axes {available} -> {axes}; glyphs {len(cmap)}; "
              f"sfnt {head}; missing {missing or 'none'}")
    print("done ->", FONT_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
