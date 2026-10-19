# MeasureMe — web MVP design

A faithful copy of the reference interface, ready to be coded. Everything here matches the reference build: same tokens, layout, components, states, and copy — no invented identity.

## Files

| File | What it is |
| --- | --- |
| `mockup.html` | The assembled screen. Opens directly in a browser; a small script ports the app logic so every state behaves like the real thing (sample photo, upload, manual marking, estimate, sliders, debug). |
| `tokens.css` | The base tokens: 4 colors, 2 font families, 1 card radius, plus the derived opacity mixes. |
| `app.css` | Every component as plain CSS. Each rule quotes the original Tailwind classes in a comment. |
| `styleguide.html` | The system sheet: palette, type, components, states, layout numbers, and a class → Tailwind mapping table. |
| `assets/` | `standing.jpg` — the reference's own sample photo (1152×1728). `favicon.svg`. |
| `preview/` | Rendered screenshots of the mockup. |

Open `mockup.html` in a browser. Useful query params for stills: `?state=empty`, `?state=result`, `?state=busy`, `?state=fail`, `?state=manual`, `?state=prior`.

## Design tokens

```
--color-bg      #efe8dc   page background
--color-surface #fbf7f0   cards, secondary buttons, inputs
--color-ink     #1c1712   text, photo frame, label outlines
--color-primary #b8431f   actions, title glyph, markers, meter fill
--radius-card   1.5rem    cards (24px); wells and tiles 16px; buttons fully round
```

Type: **Outfit** (400/500/600) for everything functional, **Fraunces** for the wordmark, card titles, and the measured number. The reference loads Fraunces at 560/650 only; 400–560 resolves to 560, 600+ to 650. Google Fonts link (self-host for production):

```
https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,560;9..144,650&family=Outfit:wght@400;500;600&display=swap
```

## Layout

- Page: 1024px max width, centered, `px-4 py-6` → `sm:px-6 sm:py-10`.
- Header: 672px max; kicker, dimension-glyph + “MeasureMe”, lede (576px max). 24px below.
- Two-column grid at ≥1024px (equal columns, 24px gap); single column below.
- Left: dark 3:4 photo frame and its actions. Right: result / failure / empty, then “How the estimate is calculated”, then the disclaimer.
- Cards: 24px radius, 1px ink/10 border, surface fill, 20px padding, smallest shadow. Inner wells and tiles: 16px radius, background token.
- Buttons: always pills — primary `bg-primary text-surface`, secondary `border-ink/15 bg-surface`, ghost transparent; sizes `min-h-11 px-4 text-sm` and `min-h-12 w-full px-5 text-base`; disabled is 45% opacity.

## The estimate

The screen exists to explain one formula, stated on the card:

```
height_cm = (person_pixel_height / head_pixel_height) × average_head_cm
```

- Pose detection (MediaPipe) finds crown, chin, and the lowest foot point; the crown comes from the face geometry (eyes halfway, mouth at 78%, nose at 63%, shoulders ÷ 1.7 as fallbacks).
- `average_head_cm` defaults to 23 (16–32 in Advanced), canonical stature 7.5 × head (6–8.5). Both are live sliders.
- Confidence (0–100) scores the pose, not the accuracy; it is capped at 92 and never reads 98–100.
- The sample photo is wired to the reference's exact numbers: landmarks at crown y 137.4 / chin y 330.4 / feet y 1537.8 on the 1152×1728 image → span 1400.4 px, head 193.0 px → “7.3 heads” → 166.9 → **167 cm / 5 ft 6 in / quality 92** — the readout shown in the reference screenshots.

The mockup's upload path simulates a weak read (fallback method, capped confidence) so that state is visible; the real app gets these landmarks from on-device detection.

## States

Empty frame → photo (sample or upload) → busy “Reading the pose” → result (number, imperial, quality meter, px tiles, formula, explanation, warnings) or failure (“Couldn't measure that photo” + reason + pose tips + “Mark points yourself”). Manual mode adds tappable markers and drag-to-correct. All copy is verbatim from the reference and listed in `styleguide.html`.

## Notes for the build

- The reference is Tailwind v4 with a shadcn-style `Button`; `styleguide.html` has a class → utility table so either path (Tailwind or plain CSS) reproduces it 1:1.
- The mockup draws the overlay in screen coordinates with the reference's constants (marker r 11, rod 6/2 px with ±12 caps, head bracket +22/±8, outlined labels) — port those numbers, not an approximation.
- Fixed strings already in place: the formula and “Not a medical or official measurement. Results are estimates.”
- Out of scope by design: reference objects, ARCore/LiDAR, accounts, uploads, history.

## Related

- Root of this repo: an earlier static web MVP — reference only, do not extend.
- `../android/`: the native app (Kotlin + CameraX + ML Kit Pose) implementing the same formula and identifiers; this design is what a web build should mirror.
