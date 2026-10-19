/* ============================================================================
 * MeasureMe — overlay.js
 * Layer 4a: the canvas drawn on top of the photo. Everything here takes
 * NORMALIZED coordinates (0..1 of the image) so detection, manual taps and
 * drawing all share one coordinate system. DPI-aware (crisp on phones).
 * ==========================================================================*/
window.MM = window.MM || {};

MM.overlay = (function () {
  const C = {
    accent: "#2C4BF0",
    accentSoft: "rgba(44,75,240,0.14)",
    ink: "#16181D",
    white: "#FFFFFF",
    whiteSoft: "rgba(255,255,255,0.92)",
    amber: "#B45309"
  };

  let canvas = null, ctx = null, cssW = 0, cssH = 0, dpr = 1;

  function attach(el) {
    canvas = el;
    ctx = canvas.getContext("2d");
  }

  /** Call after the image lays out (and on window resize). */
  function resize() {
    if (!canvas) return;
    const r = canvas.getBoundingClientRect();
    cssW = r.width; cssH = r.height;
    dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  const clear = () => ctx && ctx.clearRect(0, 0, cssW, cssH);
  const px = (p) => ({ x: p.x * cssW, y: p.y * cssH });

  /* ---------- primitives ------------------------------------------------- */
  function dot(p, r, fill, ring) {
    const q = px(p);
    ctx.beginPath(); ctx.arc(q.x, q.y, r, 0, Math.PI * 2);
    ctx.fillStyle = fill; ctx.fill();
    if (ring) { ctx.lineWidth = 2; ctx.strokeStyle = ring; ctx.stroke(); }
  }

  function pill(x, y, text, opts = {}) {
    const font = `${opts.weight || 600} ${opts.size || 12}px -apple-system, "Segoe UI", Roboto, sans-serif`;
    ctx.font = font;
    const padX = 8, padY = 5;
    const w = ctx.measureText(text).width + padX * 2;
    const h = (opts.size || 12) + padY * 2;
    let bx = x - w / 2, by = y - h / 2;
    bx = Math.max(4, Math.min(cssW - w - 4, bx));
    by = Math.max(4, Math.min(cssH - h - 4, by));
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(bx, by, w, h, 999);
    else ctx.rect(bx, by, w, h);
    ctx.fillStyle = opts.bg || C.white;
    ctx.globalAlpha = opts.alpha == null ? 0.94 : opts.alpha;
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.fillStyle = opts.color || C.ink;
    ctx.textBaseline = "middle"; ctx.textAlign = "center";
    ctx.fillText(text, bx + w / 2, by + h / 2 + 0.5);
    return { x: bx, y: by, w, h };
  }

  function hLine(y, x0, x1, dashed, width, color) {
    ctx.save();
    ctx.beginPath();
    ctx.setLineDash(dashed ? [5, 5] : []);
    ctx.lineWidth = width || 1.5;
    ctx.strokeStyle = color || C.accent;
    ctx.moveTo(x0 * cssW, y * cssH); ctx.lineTo(x1 * cssW, y * cssH);
    ctx.stroke();
    ctx.restore();
  }

  function vLine(x, y0, y1, dashed, width, color) {
    ctx.save();
    ctx.beginPath();
    ctx.setLineDash(dashed ? [5, 5] : []);
    ctx.lineWidth = width || 2;
    ctx.strokeStyle = color || C.accent;
    ctx.moveTo(x * cssW, y0 * cssH); ctx.lineTo(x * cssW, y1 * cssH);
    ctx.stroke();
    ctx.restore();
  }

  /** Engineering-style dimension line with end caps, drawn with a white
   *  outline first so it stays readable on any photo. */
  function dimensionLine(x, yTop, yBot, label) {
    const X = x * cssW, Y1 = yTop * cssH, Y2 = yBot * cssH;
    const cap = Math.min(14, Math.max(8, cssW * 0.028));
    ctx.save();
    ctx.lineCap = "round";
    for (const pass of [{ color: C.white, w: 4.5, a: 0.85 }, { color: C.accent, w: 2, a: 1 }]) {
      ctx.globalAlpha = pass.a;
      ctx.strokeStyle = pass.color; ctx.lineWidth = pass.w;
      ctx.beginPath(); ctx.moveTo(X, Y1); ctx.lineTo(X, Y2); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(X - cap / 2, Y1); ctx.lineTo(X + cap / 2, Y1); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(X - cap / 2, Y2); ctx.lineTo(X + cap / 2, Y2); ctx.stroke();
      // small tick marks every 10% like a ruler
      for (let i = 1; i < 10; i++) {
        const y = Y1 + (Y2 - Y1) * i / 10;
        ctx.beginPath(); ctx.moveTo(X - 3, y); ctx.lineTo(X + 3, y); ctx.stroke();
      }
    }
    ctx.restore();
    if (label) pill(X, (Y1 + Y2) / 2, label, { bg: C.white, color: C.ink, size: 13, weight: 700 });
  }

  /* ---------- state drawings --------------------------------------------- */
  /** Result overlay: head point, feet points, ground line, dimension line. */
  function drawResult(meas, est, opts = {}) {
    if (!ctx) return;
    clear();
    const P = meas.points;
    // geometry returns pixel coords; the canvas works in normalized 0..1 space
    const n = (p) => ({ x: p.x / meas.imageW, y: p.y / meas.imageH });

    const crownN = n(P.crown), chinN = n(P.chin);
    const footYs = P.feet.map(f => f.y / meas.imageH);
    const footYN = P.footY / meas.imageH;

    // ground line across both feet
    if (P.feet.length >= 2) {
      const xs = P.feet.map(f => f.x / meas.imageW);
      hLine(footYN, Math.min(...xs) - 0.05, Math.max(...xs) + 0.05, true, 2, C.accent);
    } else if (P.feet.length === 1) {
      hLine(footYN, Math.max(0, n(P.feet[0]).x - 0.12), Math.min(1, n(P.feet[0]).x + 0.12), true, 2, C.accent);
    }

    // vertical dimension line, slightly left of the body centre
    const lineX = Math.max(0.045, crownN.x - 0.16);
    dimensionLine(lineX, crownN.y, footYN, `≈ ${est.cmRounded} cm`);

    // head / feet markers
    dot(crownN, 4.5, C.accent, C.white);
    for (const f of P.feet) dot(n(f), 4, C.accent, C.white);
    const cp = px(crownN);
    pill(cp.x, cp.y - 16, "head", { color: C.accent, size: 11 });
    const fp = px(n(P.feet[0] || P.crown));
    pill(fp.x, fp.y - 14, "feet", { color: C.accent, size: 11 });

    // head-size bracket (shows what "23 cm" was measured against)
    const bx = Math.max(0.03, crownN.x - 0.075);
    ctx.save();
    ctx.strokeStyle = C.white; ctx.lineWidth = 4; ctx.globalAlpha = 0.8;
    ctx.beginPath(); ctx.moveTo(bx * cssW, crownN.y * cssH); ctx.lineTo(bx * cssW, chinN.y * cssH); ctx.stroke();
    ctx.globalAlpha = 1;
    ctx.strokeStyle = C.accent; ctx.lineWidth = 1.5; ctx.setLineDash([4, 4]);
    ctx.beginPath(); ctx.moveTo(bx * cssW, crownN.y * cssH); ctx.lineTo(bx * cssW, chinN.y * cssH); ctx.stroke();
    ctx.restore();
    pill(bx * cssW, (crownN.y + chinN.y) / 2 * cssH, "head " + Math.round(meas.headPx) + " px", { color: C.ink, size: 11 });

    if (opts.debugLandmarks && P.all) drawDebugLandmarks(P.all, meas.imageW, meas.imageH, false);
  }

  /** Manual mode: numbered pins + guide lines + live preview label. */
  function drawManual(taps, activeIdx, liveText) {
    if (!ctx) return;
    clear();
    const labels = ["1 · top of head", "2 · chin", "3 · heels / floor"];
    const colors = C.accent;

    if (taps.length) {
      for (let i = 0; i < taps.length; i++) {
        const p = taps[i];
        dot(p, i === activeIdx ? 7 : 5.5, colors, C.white);
        if (i === activeIdx) {
          vLine(p.x, 0, 1, true, 1, "rgba(255,255,255,0.75)");
          hLine(p.y, 0, 1, true, 1, "rgba(255,255,255,0.75)");
          vLine(p.x, 0, 1, true, 1, "rgba(22,24,29,0.35)");
          hLine(p.y, 0, 1, true, 1, "rgba(22,24,29,0.35)");
        }
        pill(p.x * cssW, p.y * cssH - 18, labels[i], { color: C.ink, size: 11 });
      }
    }
    if (taps.length >= 2) {
      // head bracket
      const [a, b] = [taps[0], taps[1]].sort((p, q) => p.y - q.y);
      vLine(Math.max(0.03, a.x - 0.06), a.y, b.y, true, 1.5, C.accent);
    }
    if (taps.length >= 3) {
      const top = Math.min(taps[0].y, taps[1].y);
      vLine(Math.max(0.045, taps[0].x - 0.16), top, taps[2].y, true, 2, C.accent);
      pill(Math.max(0.045, taps[0].x - 0.16) * cssW, (top + taps[2].y) / 2 * cssH,
        liveText || "...", { color: C.ink, size: 12, weight: 700 });
    }
  }

  function drawDebugLandmarks(all, imageW, imageH, label) {
    for (const p of all) {
      if (p.vis != null && p.vis < 0.25) continue;
      dot({ x: p.x / imageW, y: p.y / imageH }, 2.5, "rgba(44,75,240,0.8)", null);
    }
    if (label) pill(cssW / 2, 18, "debug: pose landmarks", { color: C.ink, size: 11 });
  }

  /** Magnifier loupe drawn near the finger while placing manual points. */
  function drawLoupe(loupeCanvas, img, normPoint, zoom = 3.2) {
    const size = loupeCanvas.width;
    const c = loupeCanvas.getContext("2d");
    c.clearRect(0, 0, size, size);
    c.save();
    c.beginPath(); c.arc(size / 2, size / 2, size / 2 - 2, 0, Math.PI * 2); c.clip();
    const sx = normPoint.x * img.naturalWidth;
    const sy = normPoint.y * img.naturalHeight;
    // zoom is relative to the on-screen size, so the loupe feels 1:1 with the finger
    const displayScale = img.naturalWidth / Math.max(1, img.clientWidth);
    const win = (size / zoom) * displayScale;
    c.drawImage(img, sx - win / 2, sy - win / 2, win, win, 0, 0, size, size);
    c.strokeStyle = "rgba(44,75,240,0.9)";
    c.lineWidth = 2;
    c.beginPath();
    c.moveTo(size / 2, size / 2 - 12); c.lineTo(size / 2, size / 2 + 12);
    c.moveTo(size / 2 - 12, size / 2); c.lineTo(size / 2 + 12, size / 2);
    c.stroke();
    c.restore();
  }

  /** event → normalized image coords (clamped 0..1) */
  function toNorm(evt) {
    const r = canvas.getBoundingClientRect();
    return {
      x: Math.min(1, Math.max(0, (evt.clientX - r.left) / r.width)),
      y: Math.min(1, Math.max(0, (evt.clientY - r.top) / r.height))
    };
  }

  return { attach, resize, clear, drawResult, drawManual, drawDebugLandmarks, drawLoupe, toNorm };
})();
