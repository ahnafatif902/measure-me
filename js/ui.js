/* ============================================================================
 * MeasureMe — ui.js
 * Layer 4b: ALL DOM writes live here. app.js talks to ui.js, never to the
 * document directly (except wiring events). Six designed phases:
 *   empty · ready · analyzing · error · manual · result
 * ==========================================================================*/
window.MM = window.MM || {};

MM.ui = (function () {
  const $ = (id) => document.getElementById(id);
  const els = {};
  let lastCounted = 0;

  function init() {
    ["dropZone", "btnPick", "fileInput", "photoFrame", "photo", "overlay", "loupe",
     "scanline", "btnReplace", "primaryBtn", "manualToggleBtn", "statusCard",
     "statusText", "errorCard", "errorTitle", "errorText", "btnRetry",
     "btnManualFromError", "resultCard", "heightNumber", "heightUnit", "heightFtIn",
     "confFill", "confScore", "confBand", "errorLine", "notesList", "calcDetails",
     "traceBox", "headCmSlider", "headCmOut", "debugToggle", "manualBar",
     "manualSteps", "btnManualUndo", "btnManualReset", "btnManualCancel", "manualWarn"
    ].forEach(id => els[id] = $(id));
    return els;
  }

  /* ---------- buttons ----------------------------------------------------- */
  function setPrimary(label, { disabled = false, busy = false } = {}) {
    els.primaryBtn.textContent = "";
    if (busy) {
      const s = document.createElement("span");
      s.className = "spinner spinner--on-accent";
      els.primaryBtn.appendChild(s);
    }
    els.primaryBtn.appendChild(document.createTextNode(label));
    els.primaryBtn.disabled = disabled;
  }

  /* ---------- phase switching --------------------------------------------- */
  function setPhase(phase) {
    const show = (...nodes) => nodes.forEach(n => n.classList.remove("hidden"));
    const hide = (...nodes) => nodes.forEach(n => n.classList.add("hidden"));

    // defaults per phase (explicit matrix keeps states predictable)
    const map = {
      empty:     { drop: 1, frame: 0, result: 0, error: 0, status: 0, manual: 0, scan: 0 },
      ready:     { drop: 0, frame: 1, result: 0, error: 0, status: 0, manual: 0, scan: 0 },
      analyzing: { drop: 0, frame: 1, result: 0, error: 0, status: 1, manual: 0, scan: 1 },
      result:    { drop: 0, frame: 1, result: 1, error: 0, status: 0, manual: 0, scan: 0 },
      error:     { drop: 0, frame: 1, result: 0, error: 1, status: 0, manual: 0, scan: 0 },
      manual:    { drop: 0, frame: 1, result: 0, error: 0, status: 0, manual: 1, scan: 0 }
    }[phase] || {};

    map.drop ? show(els.dropZone) : hide(els.dropZone);
    map.frame ? show(els.photoFrame) : hide(els.photoFrame);
    map.result ? show(els.resultCard) : hide(els.resultCard);
    map.error ? show(els.errorCard) : hide(els.errorCard);
    map.status ? show(els.statusCard) : hide(els.statusCard);
    map.manual ? show(els.manualBar) : hide(els.manualBar);
    map.scan ? show(els.scanline) : hide(els.scanline);

    els.photoFrame.dataset.phase = phase;
    document.body.dataset.phase = phase; // CSS can collapse chrome per phase
  }

  function setManualToggleVisible(visible) {
    els.manualToggleBtn.classList.toggle("hidden", !visible);
  }

  const STAGE_TEXT = {
    "loading-script": "Loading pose model…",
    "loading-model": "Warming up the model…",
    "detecting": "Finding head and feet…"
  };
  const stageText = (stage) => STAGE_TEXT[stage] || "Estimating…";
  function setStatus(stage) { els.statusText.textContent = stageText(stage); }

  /* ---------- manual mode bar --------------------------------------------- */
  function renderManualBar(taps, activeIdx) {
    const items = els.manualSteps.querySelectorAll("li");
    items.forEach((li, i) => {
      li.classList.toggle("done", i < taps.length);
      li.classList.toggle("active", i === taps.length && taps.length < 3);
    });
    els.btnManualUndo.disabled = taps.length === 0;
  }

  /** Inline warning inside the manual bar (e.g. implausible points).
   *  Empty string hides it. */
  function setManualWarning(text) {
    els.manualWarn.textContent = text || "";
    els.manualWarn.classList.toggle("hidden", !text);
  }

  /* ---------- errors ------------------------------------------------------- */
  const ERRORS = {
    network: {
      title: "Pose model couldn't load",
      text: "The detector downloads once from a CDN and then runs fully on your device. Check your connection and try again — or measure with three manual taps."
    },
    model: {
      title: "Pose model failed to start",
      text: "Your browser couldn't initialise the detector (it needs WebAssembly). Manual mode works everywhere."
    },
    timeout: {
      title: "The model took too long to load",
      text: "Slow connection? Try again, or switch to manual mode."
    },
    detect: {
      title: "Detection hit a snag",
      text: "The detector ran but returned an error. Try the photo again or use manual mode."
    },
    "no-pose": {
      title: "We couldn't find a full body",
      text: "The detector didn't see a person standing head-to-toe in this photo. The tips below usually fix it."
    },
    "no-fullbody": {
      title: "The full body isn't visible",
      text: "Your head or feet seem to be outside the frame (or hidden). Retake the photo with room above your head and below your feet."
    },
    implausible: {
      title: "This result looks implausible",
      text: "The measured proportions don't match a standing adult (estimated well outside 120–235 cm). Something in the photo confused the detector — check the tips, or place the points manually."
    },
    file: {
      title: "That file didn't work",
      text: "Please choose an image (JPEG, PNG, WebP or HEIC from a phone's camera roll usually works)."
    }
  };

  function renderError(code) {
    const e = ERRORS[code] || ERRORS.detect;
    els.errorTitle.textContent = e.title;
    els.errorText.textContent = e.text;
  }

  /* ---------- result card --------------------------------------------------- */
  function renderResult(est, meas, settings, extras = {}) {
    // big number with a short count-up
    countUp(els.heightNumber, lastCounted, est.cmRounded, 450);
    lastCounted = est.cmRounded;
    els.heightFtIn.textContent = "≈ " + est.ftin.text;

    const pct = est.confidence100;
    els.confFill.style.width = pct + "%";
    els.confFill.className = "conf-fill conf-" + est.band.key;
    els.confScore.textContent = pct + " / 100";
    els.confBand.textContent = est.band.label + " confidence";
    els.confBand.className = "conf-band conf-" + est.band.key;
    els.errorLine.textContent = `Typical error for this photo: ± ${est.errorCm} cm (about ± ${est.errPct.toFixed(1)}%)`;

    // notes (estimatormodel + caller extras like "2 people in frame")
    const notes = [...est.notes, ...(extras.notes || [])];
    els.notesList.innerHTML = "";
    for (const n of notes) {
      const li = document.createElement("li");
      li.className = "note note--" + (n.level || "info");
      li.textContent = n.text;
      els.notesList.appendChild(li);
    }
    els.notesList.classList.toggle("hidden", notes.length === 0);

    els.photo.setAttribute("aria-label",
      `Your photo with measurement overlay. Estimated height ${est.cmRounded} centimetres.`);

    renderTrace(est, meas, settings);
  }

  function countUp(el, from, to, ms) {
    const t0 = performance.now();
    const tick = (t) => {
      const k = Math.min(1, (t - t0) / ms);
      const eased = 1 - Math.pow(1 - k, 3);
      el.textContent = Math.round(from + (to - from) * eased);
      if (k < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  /* ---------- "How this was calculated" ------------------------------------ */
  function renderTrace(est, meas, settings) {
    const f2 = (v) => (Math.round(v * 100) / 100);
    const rows = [];

    rows.push(section("1 · Head size in the photo", [
      kv("method", meas.source === "manual" ? "manual taps (crown → chin)" :
        meas.headMethod === "face-fit" ? `facial fit: ${meas.headUsed.join(" + ")}`
          : "eye spacing fallback"),
      kv("crown → chin", `${Math.round(meas.headPx)} px`),
      kv("ear → ear", meas.earWidthPx ? `${Math.round(meas.earWidthPx)} px` : "–"),
      kv("implied scale", `1 px ≈ ${f2(settings.headCm / Math.max(meas.headPx, 1))} cm`)
    ]));

    rows.push(section("2 · Body span", [
      kv("crown y", `${Math.round(meas.headFit.crownY)} px`),
      kv("feet y", `${Math.round(meas.points.footY)} px`),
      kv("span", `${Math.round(meas.spanPx)} px`),
      kv("heads tall", est.headsTall > 0 ? `${f2(est.headsTall)} (human average ≈ 7.5)` : "not measurable")
    ]));

    const anchorRows = est.anchors.map(a => {
      const used = est.usableAnchors.includes(a.id);
      return kv(
        a.label + (used ? "" : " · not used"),
        used
          ? `${a.formula} → ${Math.round(a.heightCm)} cm  ·  weight ${(100 * a.weight / (est.usableAnchors.length ? est.anchors.filter(x => est.usableAnchors.includes(x.id)).reduce((s, x) => s + x.weight, 0) : 1)).toFixed(0)}%`
          : a.note
      );
    });
    rows.push(section("3 · Pixel ratio → cm  (the exact formula)", [
      kv("model", "height = (person px ÷ anchor px) × anchor cm"),
      ...anchorRows
    ]));

    if (est.corrections.length) {
      rows.push(section("4 · Corrections", est.corrections.map(c =>
        kv(c.label, `${c.detail}${c.deltaCm ? ` (${c.deltaCm > 0 ? "+" : ""}${f2(c.deltaCm)} cm)` : ""}`))));
    }

    const F = est.factors;
    rows.push(section("5 · Confidence  →  " + est.confidence100 + " / 100", [
      kv("anchor coverage", f2(F.coverage)),
      kv("anchor agreement", f2(F.agreement)),
      kv("posture (roll / lean / knees)", `${f2(F.poseQ)}  ·  roll ${f2(F.rollDeg)}°, lean ${f2(F.leanDeg)}°`),
      kv("framing (size / span / margins)", f2(F.frameQ)),
      kv("proportions plausible", f2(F.plausQ)),
      kv("formula", `92 × ${f2(F.coverage)} × ${f2(F.agreement)}^0.35 × ${f2(F.poseQ)} × ${f2(F.frameQ)} × ${f2(F.plausQ)} = ${est.confidence100}`)
    ]));

    els.traceBox.innerHTML = rows.join("");
  }

  const section = (title, rows) =>
    `<div class="trace-section"><h4>${title}</h4><dl>${rows.join("")}</dl></div>`;
  const kv = (k, v) => `<div class="trace-row"><dt>${k}</dt><dd>${v}</dd></div>`;

  return { init, els, setPhase, setPrimary, setStatus, setManualToggleVisible,
           renderManualBar, setManualWarning, renderError, renderResult };
})();
