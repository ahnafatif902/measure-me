/* ============================================================================
 * MeasureMe — manual.js
 * Fallback interaction: three taps (crown, chin, heels) when the pose model
 * is unavailable or fails. Handles pointer input, pin dragging and the
 * magnifier loupe. Libraries-free and framework-free on purpose.
 *
 * Why 3 points and not 2? A two-point span (head→feet) contains no scale:
 * without a reference object, every pixel ratio is dimensionless. The chin
 * tap supplies the one absolute-length anchor the model needs (a head).
 * ==========================================================================*/
window.MM = window.MM || {};

MM.manual = (function () {
  const HIT_RADIUS_PX = 44; // touch-friendly pin grabbing
  let canvas = null, imgEl = null, onChange = null;
  let taps = [];          // normalized {x,y}, order: crown, chin, feet
  let activeIdx = -1;
  let dragging = false;
  let loupe = null;

  function begin(canvasEl, imageEl, opts) {
    end();
    canvas = canvasEl; imgEl = imageEl; onChange = opts.onChange || function () {};
    taps = []; activeIdx = -1; dragging = false;

    loupe = document.getElementById("loupe");
    canvas.style.touchAction = "none";
    canvas.addEventListener("pointerdown", onDown);
    canvas.addEventListener("pointermove", onMove);
    canvas.addEventListener("pointerup", onUp);
    canvas.addEventListener("pointercancel", onUp);
    emit();
  }

  function end() {
    if (!canvas) return;
    canvas.removeEventListener("pointerdown", onDown);
    canvas.removeEventListener("pointermove", onMove);
    canvas.removeEventListener("pointerup", onUp);
    canvas.removeEventListener("pointercancel", onUp);
    canvas = null; dragging = false; activeIdx = -1;
    hideLoupe();
  }

  const points = () => taps.slice();
  const setPoints = (arr) => { taps = arr.map(p => ({ x: p.x, y: p.y })); emit(); };
  const reset = () => { taps = []; activeIdx = -1; emit(); };
  const undo = () => { taps.pop(); activeIdx = -1; emit(); };
  const isComplete = () => taps.length >= 3;

  function nearestTap(norm, radiusPx) {
    const r = canvas.getBoundingClientRect();
    let best = -1, bestD = radiusPx;
    taps.forEach((t, i) => {
      const d = Math.hypot((t.x - norm.x) * r.width, (t.y - norm.y) * r.height);
      if (d < bestD) { bestD = d; best = i; }
    });
    return best;
  }

  function onDown(e) {
    if (!isActive()) return;
    e.preventDefault();
    const norm = MM.overlay.toNorm(e);
    if (taps.length < 3) {
      taps.push(norm); activeIdx = taps.length - 1;
    } else {
      const i = nearestTap(norm, HIT_RADIUS_PX);
      if (i < 0) return; // 3 pins placed: only drags are accepted
      activeIdx = i;
    }
    dragging = true;
    try { canvas.setPointerCapture && canvas.setPointerCapture(e.pointerId); } catch (err) { /* synthetic events */ }
    updateLoupe(norm);
    emit();
  }

  function onMove(e) {
    if (!isActive() || !dragging || activeIdx < 0) return;
    e.preventDefault();
    const norm = MM.overlay.toNorm(e);
    taps[activeIdx] = norm;
    updateLoupe(norm);
    emit();
  }

  function onUp(e) {
    if (!isActive()) return;
    dragging = false;
    hideLoupe();
    emit();
  }

  const isActive = () => !!canvas;
  const getActiveIdx = () => (dragging ? activeIdx : -1);

  function emit() { if (onChange) onChange(points(), getActiveIdx()); }

  /* ---------- loupe ------------------------------------------------------- */
  function updateLoupe(norm) {
    if (!loupe || !imgEl) return;
    const r = canvas.getBoundingClientRect();
    const size = loupe.width / (window.devicePixelRatio || 1);
    // place the loupe opposite the tapped side so the finger never hides it
    const margin = 14;
    let left = (norm.x < 0.5) ? r.width - size - margin : margin;
    let top = Math.min(r.height - size - margin, Math.max(margin, norm.y * r.height - size / 2));
    loupe.style.left = left + "px";
    loupe.style.top = top + "px";
    loupe.style.display = "block";
    MM.overlay.drawLoupe(loupe, imgEl, norm);
  }

  function hideLoupe() { if (loupe) loupe.style.display = "none"; }

  return { begin, end, reset, undo, points, setPoints, isComplete, isActive, getActiveIdx };
})();
