/* ============================================================================
 * MeasureMe — app.js
 * Layer 1 of 4: orchestration + app state. Flow:
 *   photo → detector (landmarks) → geometry (pixels) → anthropometry (cm)
 *         → overlay (drawing) + ui (cards)
 * Also owns manual mode, the tunable head-size setting, and a small testing
 * hook used by the automated browser tests (window.MM.testing).
 * ==========================================================================*/
window.MM = window.MM || {};

MM.app = (function () {
  const K = MM.CONST;

  const state = {
    phase: "empty",
    img: null,            // HTMLImageElement (displayed photo)
    imgURL: null,
    analysis: null,       // {canvas, width, height} — downscaled detection input
    poseCount: 0,
    measurements: null,   // geometry output (cached for live re-tuning)
    estimate: null,       // anthropometry output
    settings: { headCm: K.DEFAULT_HEAD_CM, debug: false }
  };

  let els;

  /* ---------- settings persistence (client-only, no backend) -------------- */
  const SETTINGS_KEY = "measureme.settings.v1";
  function loadSettings() {
    try {
      const s = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
      if (s.headCm) state.settings.headCm = Math.min(K.HEAD_CM_MAX, Math.max(K.HEAD_CM_MIN, s.headCm));
      if (s.debug) state.settings.debug = true;
    } catch (e) { /* private mode etc. — defaults are fine */ }
  }
  function saveSettings() {
    try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(state.settings)); } catch (e) {}
  }

  /* ---------- init --------------------------------------------------------- */
  function init() {
    els = MM.ui.init();
    MM.overlay.attach(els.overlay);
    loadSettings();

    els.headCmSlider.min = K.HEAD_CM_MIN;
    els.headCmSlider.max = K.HEAD_CM_MAX;
    els.headCmSlider.value = state.settings.headCm;
    els.headCmOut.textContent = state.settings.headCm.toFixed(1) + " cm";
    els.debugToggle.checked = state.settings.debug;

    // events
    els.btnPick.addEventListener("click", openPicker);
    els.dropZone.addEventListener("click", (e) => {
      if (!e.target.closest("#btnPick")) openPicker(); // whole zone is tappable
    });
    els.dropZone.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openPicker(); }
    });
    els.btnReplace.addEventListener("click", openPicker);
    els.fileInput.addEventListener("change", (e) => {
      if (e.target.files && e.target.files[0]) loadFile(e.target.files[0]);
      e.target.value = "";
    });
    els.primaryBtn.addEventListener("click", onPrimary);
    els.manualToggleBtn.addEventListener("click", enterManual);
    els.btnManualFromError.addEventListener("click", enterManual);
    els.btnRetry.addEventListener("click", runAuto);
    els.btnManualUndo.addEventListener("click", () => MM.manual.undo());
    els.btnManualReset.addEventListener("click", () => MM.manual.reset());
    els.btnManualCancel.addEventListener("click", () => leaveManual());

    els.headCmSlider.addEventListener("input", () => {
      state.settings.headCm = parseFloat(els.headCmSlider.value);
      els.headCmOut.textContent = state.settings.headCm.toFixed(1) + " cm";
      saveSettings();
      retune();
    });
    els.debugToggle.addEventListener("change", () => {
      state.settings.debug = els.debugToggle.checked;
      saveSettings();
      redraw();
    });

    window.addEventListener("resize", () => { MM.overlay.resize(); redraw(); });

    // desktop drag & drop
    for (const target of [els.dropZone, els.photoFrame]) {
      target.addEventListener("dragover", (e) => { e.preventDefault(); target.classList.add("dragging"); });
      target.addEventListener("dragleave", () => target.classList.remove("dragging"));
      target.addEventListener("drop", (e) => {
        e.preventDefault(); target.classList.remove("dragging");
        if (e.dataTransfer.files && e.dataTransfer.files[0]) loadFile(e.dataTransfer.files[0]);
      });
    }

    setPhase("empty");
  }

  const openPicker = () => els.fileInput.click();

  function setPhase(phase) {
    state.phase = phase;
    MM.ui.setPhase(phase);
    if (phase === "empty") {
      MM.ui.setPrimary("Estimate height", { disabled: true });
      MM.ui.setManualToggleVisible(false);
    } else if (phase === "ready") {
      MM.ui.setPrimary("Estimate height");
      MM.ui.setManualToggleVisible(true);
    } else if (phase === "error") {
      MM.ui.setPrimary("Try again");
      MM.ui.setManualToggleVisible(false);
    } else if (phase === "analyzing") {
      MM.ui.setPrimary("Estimating…", { busy: true, disabled: true });
      MM.ui.setManualToggleVisible(false);
    } else if (phase === "result") {
      MM.ui.setPrimary("Try another photo");
      MM.ui.setManualToggleVisible(true);
    } else if (phase === "manual") {
      MM.ui.setPrimary("Calculate from points", { disabled: !MM.manual.isComplete() });
      MM.ui.setManualToggleVisible(false);
    }
  }

  /* ---------- photo loading ------------------------------------------------ */
  async function loadFile(file) {
    if (!file || !/^image\//.test(file.type)) {
      fail("file");
      return;
    }
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.decoding = "async";
    const done = new Promise((resolve, reject) => {
      img.onload = resolve;
      img.onerror = () => reject(new Error("decode"));
    });
    img.src = url;
    try {
      await done;
    } catch (e) {
      URL.revokeObjectURL(url);
      fail("file");
      return;
    }
    if (state.imgURL) URL.revokeObjectURL(state.imgURL);
    state.imgURL = url;
    state.img = img;
    state.measurements = null;
    state.estimate = null;
    state.poseCount = 0;

    els.photo.src = url;
    state.analysis = prepAnalysisCanvas(img);
    els.photoFrame.style.aspectRatio = `${img.naturalWidth} / ${img.naturalHeight}`;

    // Reset UI for the new photo. NOTE: the deferred resize must not fight a
    // phase change that happened in between (e.g. a very fast detection) —
    // hence redraw() instead of an unconditional setPhase/clear.
    setPhase("ready");
    MM.overlay.clear();
    requestAnimationFrame(() => { MM.overlay.resize(); redraw(); });
  }

  /** Downscale for detection (speed + consistent pixel thresholds). */
  function prepAnalysisCanvas(img) {
    const scale = Math.min(1, K.MAX_ANALYSIS_DIM / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w; canvas.height = h;
    canvas.getContext("2d").drawImage(img, 0, 0, w, h);
    return { canvas, width: w, height: h };
  }

  /* ---------- primary CTA dispatcher --------------------------------------- */
  function onPrimary() {
    switch (state.phase) {
      case "empty": openPicker(); break;
      case "ready": runAuto(); break;
      case "error": runAuto(); break;
      case "manual": runManualCalc(); break;
      case "result": openPicker(); break; // "Try another photo"
    }
  }

  /* ---------- automatic path ------------------------------------------------ */
  async function runAuto() {
    if (!state.img || !state.analysis) return;
    setPhase("analyzing");
    MM.overlay.clear();
    MM.ui.setStatus("loading-script");

    let res;
    try {
      res = await MM.detector.detect(state.analysis.canvas, MM.ui.setStatus);
    } catch (e) {
      fail((e && e.code) || "detect");
      return;
    }

    if (!res.landmarks) { fail("no-pose"); return; }

    const { width: w, height: h } = state.analysis;
    const meas = MM.geometry.measureFromLandmarks(res.landmarks, w, h);
    if (!(meas.spanPx > 0) || meas.headPx <= 4) { fail("no-fullbody"); return; }

    const est = MM.anthropometry.estimate(meas, state.settings);
    if (!est.ok) { fail(est.reason === "implausible" ? "implausible" : "no-pose"); return; }

    state.poseCount = res.poseCount;
    showResult(meas, est);
  }

  /* ---------- manual path ---------------------------------------------------- */
  function enterManual() {
    if (!state.img) return;
    setPhase("manual");
    MM.overlay.resize();
    MM.manual.begin(els.overlay, els.photo, { onChange: onManualChange });
    MM.ui.renderManualBar([], -1);
  }

  function leaveManual() {
    MM.manual.end();
    if (state.estimate) { setPhase("result"); redraw(); }
    else { setPhase(state.img ? "ready" : "empty"); MM.overlay.clear(); }
  }

  function onManualChange(taps, activeIdx) {
    MM.ui.renderManualBar(taps, activeIdx);
    MM.ui.setManualWarning(""); // any edit invalidates the previous attempt's warning
    let live = null;
    if (taps.length >= 3) {
      const { width: w, height: h } = state.analysis;
      const meas = MM.geometry.measureFromManual(taps, w, h);
      const est = MM.anthropometry.estimate(meas, state.settings);
      live = est.ok ? `≈ ${est.cmRounded} cm · ${est.confidence100}/100` : "–";
    }
    MM.overlay.drawManual(taps, activeIdx, live);
    MM.ui.setPrimary("Calculate from points", { disabled: taps.length < 3 });
  }

  function runManualCalc() {
    if (!MM.manual.isComplete()) return;
    const taps = MM.manual.points();
    const { width: w, height: h } = state.analysis;
    const meas = MM.geometry.measureFromManual(taps, w, h);
    const est = MM.anthropometry.estimate(meas, state.settings);
    if (!est.ok) {
      // Keep the points on screen so the user can drag and retry — no dead end.
      let msg;
      if (est.reason === "implausible") {
        const r = Math.round(est.headsTall * 10) / 10;
        msg = est.headsTall > K.HEADS_TALL_MAX
          ? `About ${r} heads tall — the head was measured too small. Point 2 (chin) is probably too high: tap the very bottom of the jaw.`
          : `About ${r} heads tall — the head was measured too big. Point 2 (chin) is probably too low, or point 3 too high.`;
      } else {
        msg = "Not enough information in those three points — place them on the crown, the chin and the floor under the heels.";
      }
      MM.ui.setManualWarning(msg);
      return;
    }
    MM.manual.end();
    MM.ui.setManualWarning("");
    state.poseCount = 0;
    showResult(meas, est);
  }

  /* ---------- result ---------------------------------------------------------- */
  function showResult(meas, est) {
    state.measurements = meas;
    state.estimate = est;
    setPhase("result");
    MM.overlay.resize();
    redraw();
    // bring the result card into view (it sits below the photo on phones)
    requestAnimationFrame(() => {
      els.resultCard.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }

  function extras() {
    const notes = [];
    if (state.poseCount > 1) {
      notes.push({ level: "info", text: `More than one person was in the frame — the largest one was measured. For best results, photograph one person at a time.` });
    }
    return { notes };
  }

  function redraw() {
    if (state.phase === "result" && state.estimate && state.measurements) {
      MM.overlay.drawResult(state.measurements, state.estimate, { debugLandmarks: state.settings.debug });
      MM.ui.renderResult(state.estimate, state.measurements, state.settings, extras());
    } else if (state.phase === "manual" && MM.manual.isActive()) {
      onManualChange(MM.manual.points(), MM.manual.getActiveIdx());
    }
  }

  /** Re-run the estimate after a settings change (no re-detection needed). */
  function retune() {
    if (!state.measurements) { redraw(); return; }
    const est = MM.anthropometry.estimate(state.measurements, state.settings);
    if (est.ok) {
      state.estimate = est;
      if (state.phase === "result") redraw();
      else if (state.phase === "manual") onManualChange(MM.manual.points(), MM.manual.getActiveIdx());
    }
  }

  function fail(code) {
    MM.overlay.clear();
    setPhase("error");
    MM.ui.renderError(code);
  }

  /* ---------- testing hook (used by automated tests & curious students) ----- */
  const testing = {
    state: () => ({
      phase: state.phase, poseCount: state.poseCount,
      estimate: state.estimate && {
        ok: state.estimate.ok, cm: state.estimate.cm, conf: state.estimate.confidence100,
        band: state.estimate.band && state.estimate.band.key, errorCm: state.estimate.errorCm,
        headsTall: state.estimate.headsTall, anchors: state.estimate.usableAnchors
      },
      measurements: state.measurements && {
        spanPx: state.measurements.spanPx, headPx: state.measurements.headPx,
        source: state.measurements.source, rollDeg: state.measurements.rollDeg,
        leanDeg: state.measurements.leanDeg
      },
      settings: { ...state.settings }
    }),
    loadFromUrl: async (url, name = "test-photo.jpg") => {
      const blob = await (await fetch(url)).blob();
      const file = new File([blob], name, { type: blob.type || "image/jpeg" });
      await loadFile(file);
      return true;
    },
    runAuto,
    enterManual,
    setManualPoints: (pts) => { MM.manual.setPoints(pts); },
    manualCalc: runManualCalc,
    /** Full internal state incl. quality signals — for tests & teaching. */
    dump: () => JSON.stringify({
      phase: state.phase, poseCount: state.poseCount,
      settings: state.settings, measurements: state.measurements, estimate: state.estimate
    }),
    setHeadCm: (v) => {
      state.settings.headCm = v;
      els.headCmSlider.value = v;
      els.headCmOut.textContent = v.toFixed(1) + " cm";
      retune();
    }
  };

  return { init, testing };
})();

document.addEventListener("DOMContentLoaded", () => MM.app.init());
