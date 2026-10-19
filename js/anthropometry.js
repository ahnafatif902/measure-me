/* ============================================================================
 * MeasureMe — anthropometry.js
 * Layer 3 of 4: centimetres. Converts the pixel measurements from geometry.js
 * into a height estimate, a confidence score and a full calculation trace.
 * Pure math — no DOM, no pixels, no detection. Easy to test in isolation.
 *
 * Model (all visible in the UI, all tunable via MM.CONST):
 *   height_cm ≈ (person_px / anchor_px) × anchor_cm
 * where `anchor` is a body part with a known average length:
 *   headHeight (23 cm, primary) · headWidth (14.9 cm) · shoulderWidth (34 cm).
 * Each anchor produces its own estimate with a quality weight; we take the
 * weighted mean and scale confidence by posture, framing and plausibility.
 * ==========================================================================*/
window.MM = window.MM || {};

MM.anthropometry = (function () {
  const K = MM.CONST;
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  /* ---------- anchor defs -------------------------------------------------
   * quality() gets the geometry quality bag; only landed anchors matter.
   * Head-based anchors are also sanity-checked against plausible human
   * proportions (heads-tall): an anchor whose implied ratio is absurd gets
   * discarded, so a bad facial fit can't poison the blended estimate. */
  function ratioPenalty(r) {
    if (r >= K.HEADS_TALL_MIN && r <= K.HEADS_TALL_MAX) return 1;
    const out = r < K.HEADS_TALL_MIN ? K.HEADS_TALL_MIN - r : r - K.HEADS_TALL_MAX;
    return clamp(1 - out / 0.8, 0, 1);
  }

  function buildAnchors(meas, settings) {
    const q = meas.quality;
    const headCm = settings.headCm;
    // Ear-to-ear width tracks head height — both measure the same head — so
    // the user's head-size setting scales the width anchor too. Without this,
    // the slider silently did nothing on photos where head height was rejected.
    const headScale = headCm / K.DEFAULT_HEAD_CM;
    const list = [];

    // 1) Head HEIGHT via facial least-squares fit — the primary model.
    if (meas.headPx > 4) {
      const ratio = meas.spanPx / meas.headPx;
      const rp = ratioPenalty(ratio);
      const quality =
        q.visFace * (q.widthAgreement > 0 ? q.widthAgreement : 0.75) *
        q.fitErrorFactor * (0.45 + 0.55 * q.sizeFactor) * rp;
      list.push({
        id: "headHeight",
        label: "Head height",
        anchorCm: headCm,
        anchorPx: meas.headPx,
        weight: K.ANCHOR_WEIGHT.headHeight * quality,
        quality,
        excluded: rp <= 0.12,
        note: "crown→chin, facial fit: " + meas.headUsed.join(" + ")
      });
    }

    // 2) Head WIDTH (ear-to-ear). Unaffected by chin pitch, but 2× more
    //    sensitive per-relative-error than head height → weaker anchor.
    if (meas.earWidthPx > 4 && q.visEars > 0.25) {
      const ratio = meas.spanPx / meas.headPxFromWidth;
      const rp = ratioPenalty(ratio);
      const quality = q.visEars * q.yawFactor * (0.45 + 0.55 * q.sizeFactor) * rp;
      list.push({
        id: "headWidth",
        label: "Head width",
        anchorCm: K.HEAD_WIDTH_CM * headScale,
        anchorPx: meas.earWidthPx, // the measured ear-to-ear span in px
        weight: K.ANCHOR_WEIGHT.headWidth * quality,
        quality,
        excluded: rp <= 0.12,
        note: "ear→ear; the fallback when head height is unreliable"
      });
    }

    // 3) Shoulder width — last resort, very variable between people.
    if (meas.shoulderWidthPx > 4 && q.visShoulders > 0.25) {
      const quality = q.visShoulders;
      list.push({
        id: "shoulderWidth",
        label: "Shoulder width",
        anchorCm: K.SHOULDER_JOINT_CM,
        anchorPx: meas.shoulderWidthPx,
        weight: K.ANCHOR_WEIGHT.shoulderWidth * quality,
        quality,
        excluded: false,
        note: "joint-to-joint; widest population spread → small weight"
      });
    }

    for (const a of list) {
      a.heightCm = meas.spanPx * (a.anchorCm / a.anchorPx) + K.SOLE_TO_TOE_TIP_CM;
      a.formula = `${fmt(meas.spanPx, 0)} px ÷ ${fmt(a.anchorPx, 0)} px × ${fmt(a.anchorCm, 1)} cm + ${K.SOLE_TO_TOE_TIP_CM} cm`;
      if (a.excluded) {
        const r = (a.id === "headWidth" ? meas.spanPx / meas.headPxFromWidth : meas.spanPx / meas.headPx);
        a.note = `rejected: implies ${fmt(r, 1)} heads tall (must be ${K.HEADS_TALL_MIN}–${K.HEADS_TALL_MAX})`;
      }
    }
    return list;
  }

  const fmt = (v, d) => (v == null || !isFinite(v)) ? "–" : v.toFixed(d);

  /* ---------- the estimate ------------------------------------------------ */
  function estimate(meas, settings) {
    const q = meas.quality;
    const anchors = buildAnchors(meas, settings);
    const usable = anchors.filter(a => !a.excluded && a.anchorPx > 0 && a.weight > 0.02);
    if (!usable.length || !(meas.spanPx > 0)) {
      // Distinguish "the points contradict each other" from "nothing to work
      // with", so manual mode can explain what to fix instead of a dead end.
      let reason = "no-anchor", cm = null, headsTall = 0;
      if (meas.spanPx > 0 && meas.headPx > 4) {
        const r = meas.spanPx / meas.headPx;
        headsTall = r;
        if (r > K.HEADS_TALL_MAX || r < K.HEADS_TALL_MIN) {
          reason = "implausible";
          cm = meas.spanPx * (settings.headCm / meas.headPx) + K.SOLE_TO_TOE_TIP_CM;
        }
      }
      return { ok: false, reason, cm, headsTall, anchors };
    }

    // weighted mean of anchor estimates
    const wSum = usable.reduce((s, a) => s + a.weight, 0);
    let cm = usable.reduce((s, a) => s + a.weight * a.heightCm, 0) / wSum;

    // Ratio (heads-tall) is judged against the heaviest usable HEAD anchor,
    // so a rejected facial fit doesn't poison the plausibility judgement.
    const byWeight = [...usable].sort((a, b) => b.weight - a.weight);
    let headPxForRatio = 0;
    for (const a of byWeight) {
      if (a.id === "headHeight") { headPxForRatio = meas.headPx; break; }
      if (a.id === "headWidth") { headPxForRatio = meas.headPxFromWidth; break; }
    }
    const headsTall = headPxForRatio > 4 ? meas.spanPx / headPxForRatio : 0;

    // corrections -----------------------------------------------------------
    const corrections = [];
    let leanCorrected = false;
    if (meas.leanDeg > K.LEAN_GOOD_DEG && meas.leanDeg <= K.MAX_LEAN_CORRECTION_DEG) {
      const cos = Math.cos(meas.leanDeg * Math.PI / 180);
      const before = cm; cm /= cos; leanCorrected = true;
      corrections.push({
        label: "Body lean",
        detail: `÷ cos(${fmt(meas.leanDeg, 1)}°)`,
        deltaCm: cm - before
      });
    }
    if (meas.rollRejected) {
      corrections.push({ label: "Camera roll", detail: "tilt > 10° — not corrected", deltaCm: 0 });
    }
    corrections.push({
      label: "Toe tip height",
      detail: `+ ${K.SOLE_TO_TOE_TIP_CM} cm per anchor (toe landmark floats above the floor)`,
      deltaCm: K.SOLE_TO_TOE_TIP_CM
    });

    // confidence ------------------------------------------------------------
    const coverage = clamp(0.35 + 0.65 * clamp(wSum, 0, 1), 0, 1);
    let agreement = 0.85;
    if (usable.length >= 2) {
      const spread = Math.sqrt(usable.reduce((s, a) => s + a.weight * (a.heightCm - cm) ** 2, 0) / wSum);
      agreement = clamp(1 - spread / (0.10 * cm), 0.35, 1);
    }

    const poseQ =
      (0.55 + 0.45 * q.rollFactor) *
      (0.50 + 0.50 * q.leanFactor) *
      (0.75 + 0.25 * q.kneeFactor);
    const frameQ =
      (0.50 + 0.50 * q.sizeFactor) *
      (0.60 + 0.40 * q.spanFactor) *
      (0.70 + 0.30 * q.marginsFactor);

    let plausQ = 1;
    const notes = [];
    const rejectedHeadFit = anchors.find(a => a.id === "headHeight" && a.excluded);
    if (rejectedHeadFit) {
      notes.push({ level: "info", text: "Head height looked unreliable on this photo (tilted head, hair/hat, or unusual proportions) — the estimate leans on head width and shoulders instead." });
    }
    if (headsTall > 0 && (headsTall < K.HEADS_TALL_MIN || headsTall > K.HEADS_TALL_MAX)) {
      const out = headsTall < K.HEADS_TALL_MIN
        ? K.HEADS_TALL_MIN - headsTall : headsTall - K.HEADS_TALL_MAX;
      plausQ *= clamp(1 - out / 0.8, 0.15, 1);
      if (headsTall < K.HEADS_TALL_MIN) {
        notes.push({ level: "warn", text: "Proportions look child-like or the head was over-measured — the adult-head model is less accurate here." });
      } else {
        notes.push({ level: "warn", text: "Head-to-body ratio looks unusually high — check that the head and feet points are right." });
      }
    }
    if (cm < K.HEIGHT_MIN_CM || cm > K.HEIGHT_MAX_CM) {
      plausQ *= 0.6;
      notes.push({ level: "warn", text: `Result is outside the typical adult range (${K.HEIGHT_MIN_CM}–${K.HEIGHT_MAX_CM} cm) — treat with extra caution.` });
    }

    let conf = K.MAX_CONFIDENCE * coverage * Math.pow(agreement, 0.35) * poseQ * frameQ * plausQ;
    if (meas.source === "manual") {
      conf = Math.min(conf, K.MANUAL_MAX_CONFIDENCE);
      notes.push({ level: "info", text: "Manual points: accuracy depends on how precisely you tapped crown, chin and heels." });
    }

    // hard rejections --------------------------------------------------------
    if (cm < K.HEIGHT_HARD_MIN_CM || cm > K.HEIGHT_HARD_MAX_CM) {
      return { ok: false, reason: "implausible", cm, anchors, notes };
    }

    const conf100 = Math.round(clamp(conf, 0.05, K.MAX_CONFIDENCE) * 100);
    const errPct = clamp(
      K.ERROR_PCT_AT_MAX + (1 - conf / K.MAX_CONFIDENCE) * K.ERROR_PCT_RANGE, 3.5, 15);
    const errorCm = Math.max(3, Math.round(cm * errPct / 100));

    return {
      ok: true,
      cm: Math.round(cm * 10) / 10,
      cmRounded: Math.round(cm),
      ftin: ftin(cm),
      headsTall,
      anchors,
      usableAnchors: usable.map(a => a.id),
      corrections, leanCorrected,
      confidence: conf,
      confidence100: conf100,
      band: band(conf100),
      errorCm, errPct,
      notes,
      factors: {
        coverage, agreement, poseQ, frameQ, plausQ,
        visFace: q.visFace, sizeFactor: q.sizeFactor, widthAgreement: q.widthAgreement,
        rollDeg: meas.rollDeg, leanDeg: meas.leanDeg, kneeDeg: meas.kneeDeg,
        spanPx: meas.spanPx, headPx: meas.headPx
      }
    };
  }

  function band(c100) {
    if (c100 >= 70) return { key: "good", label: "Good" };
    if (c100 >= 45) return { key: "fair", label: "Fair" };
    return { key: "rough", label: "Rough" };
  }

  function ftin(cm) {
    const totalIn = cm / 2.54;
    let ft = Math.floor(totalIn / 12);
    let inch = Math.round(totalIn - ft * 12);
    if (inch === 12) { ft += 1; inch = 0; }
    return { ft, inch, text: `${ft} ft ${inch} in` };
  }

  return { estimate, ftin, band };
})();
