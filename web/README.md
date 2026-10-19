# MeasureMe — web UI (React + Tailwind CSS)

A production-shaped implementation of the MeasureMe interface: estimate standing
height from one full-body photo. This build is a faithful port of the reference
design — the same layout, typography, color palette and copy that were verified
pixel-for-pixel against the screenshots in [`../screenshots`](../screenshots) and
the design in [`../design`](../design).

## Stack

- **Vite + React 19** — `npm run dev` / `npm run build` / `npm run preview`
- **Tailwind CSS v4** — design tokens live in `src/index.css` under `@theme`
  (fonts, the four colors, radius), and every component uses utilities
  (`bg-ink`, `text-ink/60`, `bg-primary`, `rounded-3xl`, `font-display`, …)
- **lucide-react** icons, **clsx + tailwind-merge** for class merging
- **Self-hosted fonts** — Fraunces + Outfit woff2 in `src/assets/fonts/`,
  wired through `src/fonts.css` (`npm run fonts` re-downloads them); no
  runtime calls to Google Fonts, so the built app is fully offline

## Design tokens

Copied 1:1 from the reference build's theme:

| Token            | Value     | Used for                               |
| ---------------- | --------- | -------------------------------------- |
| `--color-bg`     | `#efe8dc` | page background (warm cream)           |
| `--color-surface`| `#fbf7f0` | cards, secondary buttons               |
| `--color-ink`    | `#1c1712` | text, dark photo frame                 |
| `--color-primary`| `#b8431f` | actions, overlay markers, meter fill   |
| `--font-display` | Fraunces  | headings, the big number               |
| `--font-sans`    | Outfit    | everything else                        |
| `--radius-card`  | 1.5rem    | cards (`rounded-3xl`)                  |

The standalone brief listed near-identical approximations (`#f4f1ea`, `#2b2b2b`
hero, Playfair/Inter, …). Where they differ, the byte-verified values above are
the default so this app matches the screenshots exactly; `src/index.css` shows
the brief's values as a one-line swap if you ever want them.

## Structure

```
src/
  lib/measure.js         pure math ported from the app (pure functions, unit-testable)
  lib/pose.js            pose-detection hook (see below)
  lib/cn.js              class merge helper
  components/measure-studio.jsx   the whole screen: frame, actions, result, how-card
  components/overlay.jsx          MeasureRod + Marker (the SVG overlay vocabulary)
  components/ui/button.jsx        pill button (primary / secondary / ghost)
  assets/standing.jpg             sample photo (byte-identical to the reference sample)
```

## Pose detection

The interface states and copy are complete. Detection itself is a stand-in
(`src/lib/pose.js`):

- loading **the sample photo** → detected result with the exact numbers the
  reference screenshots show (167 cm, 5 ft 6 in, 7.3 heads, quality 92,
  1400 px span, 193 px head, `(1400 / 193) × 23 = 166.9`)
- any other photo → the documented "Weak fallback" result (capped at 36) unless
  you mark the points by hand (capped at 70) — both paths exercise the real UI
- manual marking (tap crown → feet → drag chin) works fully: tap also places,
  dragging a marker updates the estimate live, and the warnings explain each cap

To wire in real detection later, replace `detectLandmarks()` with MediaPipe's
pose landmarker — nothing else in the UI changes.

## Preview hooks

`?demo=result` / `?demo=manual` preload the sample state, `&scroll=N` scrolls —
used to capture the states for pixel-comparison against the reference.

## Scripts

```
npm install
npm run dev        # http://localhost:5173
npm run build      # production bundle in dist/ (works from file:// too)
npm run preview    # serve the build
```
