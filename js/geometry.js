/* ============================================================================
 * MeasureMe — geometry.js
 * Layer 2 of 4: pixels only. Turns raw pose landmarks (or 3 manual taps) into
 * measurable spans in image pixels + quality signals. Knows NOTHING about
 * centimetres, confidence or the DOM — that keeps it easy to test & teach.
 *
 * Output shape (measurements):
 *   { source, imageW, imageH,
 *     spanPx, headPx, headPxFromFit, headPxFromWidth,
 *     headFit: {crownY, chinY, eyesY, used:[...], rmsPx},
 *     earWidthPx, shoulderWidthPx,
 *     rollDeg, rollAppliedDeg, leanDeg, kneeDeg,
 *     quality: { visFace, visEars, visFeet, visShoulders, widthAgreement,
 *                yawFactor, fitErrorFactor, sizeFactor, rollFactor,
 *                leanFactor, kneeFactor, marginsFactor, spanFactor },
 *     points: {...all the pixel coords used, in ORIGINAL image space...} }
 * ==========================================================================*/
window.MM = window.MM || {};

MM.geometry = (function () {
  const K = MM.CONST, L = K.LM;

  /* ---------- tiny vector helpers ---------------------------------------- */
  const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
  const len = (v) => Math.hypot(v.x, v.y);
  const dist = (a, b) => len(sub(a, b));
  const mid = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  /** Angle (deg) of vector from b to a, measured from straight-up vertical.
   *  0° = perfectly vertical, grows with lean. */
  function angleFromVertical(a, b) {
    const v = sub(a, b);
    return Math.abs(Math.atan2(v.x, Math.abs(v.y))) * 180 / Math.PI;
  }

  /** Interior angle (deg) at `at`, between rays to `a` and `b`. 180 = straight. */
  function jointAngle(a, at, b) {
    const v1 = sub(a, at), v2 = sub(b, at);
    const d = (v1.x * v2.x + v1.y * v2.y) / (len(v1) * len(v2) || 1);
    return Math.acos(clamp(d, -1, 1)) * 180 / Math.PI;
  }

  /** MediaPipe visibility → 0..1 quality via min/mean blend (min catches
   *  one fully hidden landmark; mean keeps it from being too brutal). */
  function visibility(lms) {
    if (!lms.length) return 0;
    const vs = lms.map(p => p.vis == null ? 1 : p.vis);
    return 0.5 * Math.min(...vs) + 0.5 * (vs.reduce((a, b) => a + b, 0) / vs.length);
  }

  /** v ∈ [0,1] → quality: dead below .35, full above .9 */
  const visFactor = (v) => clamp((v - 0.35) / 0.55, 0, 1);

  /** Smooth 0→1 ramp between lo and hi. */
  function smoothstep(lo, hi, v) {
    const t = clamp((v - lo) / (hi - lo), 0, 1);
    return t * t * (3 - 2 * t);
  }

  /* ---------- landmark → pixel conversion -------------------------------- */
  function toPixels(landmarks, w, h) {
    return landmarks.map(p => ({
      x: p.x * w, y: p.y * h,
      vis: p.visibility == null ? 1 : p.visibility
    }));
  }

  /* ---------- camera-roll deskew -----------------------------------------
   * The line between the two floor-contact points (big-toe tips, else
   * ankles) is horizontal in the world. If it is tilted in the image, the
   * phone was rolled. We rotate every point by -roll before measuring y's.
   * Guarded: |roll| > MAX_ROLL_DEG is more likely a staggered stance than a
   * camera roll, so we refuse to correct it (and the confidence factors
   * punish it instead). -------------------------------------------------- */
  function estimateRoll(pts) {
    const fl = pick(pts, [L.FOOT_L, L.FOOT_R], K.VIS_LOW) ||
               pick(pts, [L.ANKLE_L, L.ANKLE_R], K.VIS_LOW);
    if (!fl) return { rollDeg: 0, contactA: null, contactB: null };
    const [a, b] = fl;
    if (Math.abs(b.x - a.x) < 8) return { rollDeg: 0, contactA: a, contactB: b };
    let roll = Math.atan2(b.y - a.y, b.x - a.x) * 180 / Math.PI; // image deg
    if (roll > 90) roll -= 180; if (roll < -90) roll += 180;
    if (Math.abs(roll) > K.MAX_ROLL_DEG) roll = 0;
    return { rollDeg: roll, contactA: a, contactB: b };
  }

  function rotate(p, deg, cx, cy) {
    const r = deg * Math.PI / 180, c = Math.cos(r), s = Math.sin(r);
    const dx = p.x - cx, dy = p.y - cy;
    return { x: cx + dx * c - dy * s, y: cy + dx * s + dy * c, vis: p.vis };
  }

  /** Get [a,b] pixel points for a landmark pair if BOTH pass visibility. */
  function pick(pts, [ia, ib], visMin) {
    const a = pts[ia], b = pts[ib];
    if (!a || !b) return null;
    if (a.vis < visMin || b.vis < visMin) return null;
    return [a, b];
  }

  /* ---------- head size by least-squares facial fit ----------------------
   * Facial landmarks sit at known fractions of head height (crown=0,chin=1).
   * Fitting y = crownY + headPx · depth_i through the available features
   * uses ALL of them instead of one, which is far more robust to a single
   * noisy landmark. Fallback ladders cover partially-visible faces. ------ */
  const FACE_FEATURES = [
    { name: "eyes",  idx: [L.EYE_L, L.EYE_R],       depth: K.FACE_DEPTH_EYES  },
    { name: "nose",  idx: [L.NOSE],                 depth: K.FACE_DEPTH_NOSE  },
    { name: "mouth", idx: [L.MOUTH_L, L.MOUTH_R],   depth: K.FACE_DEPTH_MOUTH }
  ];

  function headFromFace(pts) {
    const used = [];
    for (const f of FACE_FEATURES) {
      const ps = f.idx.map(i => pts[i]);
      if (ps.every(p => p && p.vis >= K.VIS_MIN)) {
        used.push({ name: f.name, depth: f.depth, y: ps.reduce((s, p) => s + p.y, 0) / ps.length });
      }
    }
    if (used.length >= 2) {
      // least squares: y = a + b·depth
      const n = used.length;
      const dBar = used.reduce((s, u) => s + u.depth, 0) / n;
      const yBar = used.reduce((s, u) => s + u.y, 0) / n;
      let num = 0, den = 0;
      for (const u of used) { num += (u.depth - dBar) * (u.y - yBar); den += (u.depth - dBar) ** 2; }
      const b = den > 1e-9 ? num / den : 0;
      const a = yBar - b * dBar;
      if (b > 4) { // b = head height in px; must be positive & sane
        let rms = 0;
        for (const u of used) rms += (a + b * u.depth - u.y) ** 2;
        rms = Math.sqrt(rms / n);
        return { crownY: a, headPx: b, chinY: a + b, used, rmsPx: rms };
      }
    }
    return { crownY: null, headPx: 0, chinY: null, used, rmsPx: 0 };
  }

  /** Fallback head estimate from binocular span if the fit is impossible. */
  function headFromEyeSpacing(pts) {
    const e = pick(pts, [L.EYE_L, L.EYE_R], K.VIS_MIN);
    if (!e) return 0;
    const eyeSpan = dist(e[0], e[1]); // outer-box eye centres ≈ 0.33 of head height
    return eyeSpan / 0.33;
  }

  /** Alternative head size from ear-to-ear width (frontal faces only). */
  function headFromEarWidth(pts) {
    const e = pick(pts, [L.EAR_L, L.EAR_R], K.VIS_MIN);
    if (!e) return 0;
    return dist(e[0], e[1]) / K.EAR_WIDTH_TO_HEAD_HEIGHT;
  }

  /* ---------------------------------------------------------------------
   * MAIN: pose landmarks → measurements
   * ------------------------------------------------------------------- */
  function measureFromLandmarks(landmarks, imageW, imageH) {
    const raw = toPixels(landmarks, imageW, imageH);
    const cx = imageW / 2, cy = imageH / 2;

    // 1) camera roll → deskewed copy used for all scalar measurements
    const { rollDeg, contactA, contactB } = estimateRoll(raw);
    const rollApplied = rollDeg !== 0;
    const pts = rollApplied ? raw.map(p => rotate(p, -rollDeg, cx, cy)) : raw;

    // 2) head size
    const fit = headFromFace(pts);
    let headPx = fit.headPx;
    let headMethod = fit.used.length >= 2 ? "face-fit" : "none";
    if (!headPx) { headPx = headFromEyeSpacing(pts); if (headPx) headMethod = "eye-spacing"; }

    const headPxFromWidth = headFromEarWidth(pts);
    const eyes = pick(pts, [L.EYE_L, L.EYE_R], K.VIS_MIN);
    const eyeMidX = eyes ? mid(eyes[0], eyes[1]).x : (pts[L.NOSE] ? pts[L.NOSE].x : imageW / 2);
    const crownY = headPx ? (fit.crownY != null ? fit.crownY : (eyes ? mid(eyes[0], eyes[1]).y : 0) - headPx * K.FACE_DEPTH_EYES) : 0;
    const chinY = headPx ? crownY + headPx : 0;

    // 3) feet: lowest visible contact point
    const footIdx = [L.FOOT_L, L.FOOT_R, L.HEEL_L, L.HEEL_R, L.ANKLE_L, L.ANKLE_R];
    const footPts = footIdx.map(i => pts[i]).filter(p => p && p.vis >= K.VIS_LOW);
    const footY = footPts.length ? Math.max(...footPts.map(p => p.y)) : 0;
    const contacts = pick(pts, [L.FOOT_L, L.FOOT_R], K.VIS_LOW) ||
                     pick(pts, [L.ANKLE_L, L.ANKLE_R], K.VIS_LOW) ||
                     (contactA && contactB ? [contactA, contactB] : null);

    // 4) spans
    const spanPx = (headPx && footY && footY > crownY) ? footY - crownY : 0;

    // 5) posture signals (deskewed space)
    const sh = pick(pts, [L.SH_L, L.SH_R], K.VIS_MIN);
    const an = pick(pts, [L.ANKLE_L, L.ANKLE_R], K.VIS_LOW);
    const hips = pick(pts, [L.HIP_L, L.HIP_R], K.VIS_LOW);
    const leanDeg = (sh && an) ? angleFromVertical(mid(sh[0], sh[1]), mid(an[0], an[1])) : 0;

    let kneeDeg = 180;
    if (hips && an) {
      const kL = [pts[L.HIP_L], pts[L.KNEE_L], pts[L.ANKLE_L]];
      const kR = [pts[L.HIP_R], pts[L.KNEE_R], pts[L.ANKLE_R]];
      const angles = [];
      for (const [h, k, a] of [kL, kR]) {
        if (h && k && a && h.vis >= K.VIS_LOW && k.vis >= K.VIS_LOW && a.vis >= K.VIS_LOW) {
          angles.push(jointAngle(h, k, a));
        }
      }
      if (angles.length) kneeDeg = Math.min(...angles); // the bendier knee wins
    }

    const shoulderWidthPx = sh ? dist(sh[0], sh[1]) : 0;

    // 6) quality signals ---------------------------------------------------
    const faceLms = [pts[L.EYE_L], pts[L.EYE_R], pts[L.NOSE], pts[L.MOUTH_L], pts[L.MOUTH_R]].filter(Boolean);
    const usedFace = fit.used.length ? fit.used.map(u => u.name).join("+") : headMethod;
    const visFace = visFactor(visibility(faceLms));
    const visEars = pick(pts, [L.EAR_L, L.EAR_R], 0) ? visFactor(visibility([pts[L.EAR_L], pts[L.EAR_R]])) : 0;
    const visFeet = visFactor(visibility(footPts));
    const visShoulders = sh ? visFactor(visibility(sh)) : 0;

    // ear-width agreement: how well the two independent head sizes match
    let widthAgreement = 0;
    if (headPx && headPxFromWidth) {
      widthAgreement = clamp(1 - Math.abs(headPx - headPxFromWidth) / (0.35 * headPx), 0, 1);
    }

    // head yaw: nose off-centre between the ears → ear width foreshortened
    let yawFactor = 1;
    const ears = pick(pts, [L.EAR_L, L.EAR_R], 0);
    if (ears && pts[L.NOSE]) {
      const dL = Math.abs(pts[L.NOSE].x - ears[0].x), dR = Math.abs(pts[L.NOSE].x - ears[1].x);
      if (dL + dR > 8) {
        const asym = Math.abs(dL - dR) / (dL + dR);
        yawFactor = clamp(1 - (asym - 0.12) / 0.30, 0, 1);
      }
    }

    // Fit residual tolerance: ~7% of head height RMS, floor 0.25 — real faces
    // never match the canonical line perfectly (that's fine, we only want to
    // catch grossly wrong fits, e.g. a hand covering the mouth).
    const fitErrorFactor = (fit.used.length >= 3 && headPx)
      ? clamp(1 - (fit.rmsPx / headPx) / 0.07, 0.25, 1) : 0.8;

    const sizeFactor = smoothstep(K.MIN_HEAD_PX, K.GOOD_HEAD_PX, headPx || 0);

    const rollFactor = clamp(1 - Math.min(Math.abs(rollDeg), K.MAX_ROLL_DEG) / K.MAX_ROLL_DEG * 0.35, 0, 1);
    const leanFactor = clamp(1 - (leanDeg - K.LEAN_GOOD_DEG) / (K.LEAN_BAD_DEG - K.LEAN_GOOD_DEG), 0.4, 1);
    const kneeFactor = kneeDeg >= 180 ? 1
      : 0.25 + 0.75 * smoothstep(K.KNEE_SUSPECT_DEG, K.KNEE_STRAIGHT_DEG, kneeDeg);

    // framing: head/feet must be inside the frame with breathing room
    const topMargin = crownY / imageH, bottomMargin = 1 - footY / imageH;
    const midX = (eyeMidX / imageW);
    const marginsFactor =
      (topMargin >= 0.015 && bottomMargin >= 0.01 && midX > 0.04 && midX < 0.96) ? 1 : 0.55;
    const spanFactor = spanPx ? smoothstep(0.35 * imageH, 0.5 * imageH, spanPx) : 0;

    return {
      source: "pose", imageW, imageH,
      spanPx,
      headPx: headPx || 0,
      headMethod,
      headUsed: fit.used.map(u => u.name),
      headPxFromWidth,
      headFit: { crownY, chinY, eyesY: eyes ? mid(eyes[0], eyes[1]).y : 0, rmsPx: fit.rmsPx },
      earWidthPx: ears ? dist(ears[0], ears[1]) : 0,
      shoulderWidthPx,
      rollDeg, rollAppliedDeg: rollApplied ? rollDeg : 0, rollRejected: Math.abs(rollDeg) === 0 && !!contactA,
      leanDeg, kneeDeg,
      quality: {
        visFace, visEars, visFeet, visShoulders, widthAgreement, yawFactor,
        fitErrorFactor, sizeFactor, rollFactor, leanFactor, kneeFactor,
        marginsFactor, spanFactor
      },
      points: { // ORIGINAL image space — used only for drawing overlays
        crown: { x: eyeMidX, y: crownY },
        chin: { x: eyeMidX, y: chinY },
        eyeMid: eyeMidX ? { x: eyeMidX, y: (fit.crownY != null ? fit.crownY + headPx * K.FACE_DEPTH_EYES : 0) } : null,
        feet: contacts ? contacts.map(p => ({ x: p.x, y: p.y })) : [],
        footY, all: pts
      }
    };
  }

  /* ---------------------------------------------------------------------
   * MANUAL fallback: user taps [crown, chin, feet-bottom] (normalized 0..1).
   * ------------------------------------------------------------------- */
  function measureFromManual(taps, imageW, imageH) {
    const p = taps.map(t => ({ x: t.x * imageW, y: t.y * imageH }));
    const [crown, chin, feet] = p;
    const headPx = Math.abs(chin.y - crown.y);
    const spanPx = Math.max(0, feet.y - Math.min(crown.y, chin.y));
    const sizeFactor = smoothstep(K.MIN_HEAD_PX, K.GOOD_HEAD_PX, headPx);
    const spanFactor = spanPx ? smoothstep(0.35 * imageH, 0.5 * imageH, spanPx) : 0;
    const topMargin = Math.min(crown.y, chin.y) / imageH, bottomMargin = 1 - feet.y / imageH;
    return {
      source: "manual", imageW, imageH,
      spanPx, headPx, headMethod: "manual-taps", headUsed: ["crown", "chin"],
      headPxFromWidth: 0,
      headFit: { crownY: crown.y, chinY: chin.y, eyesY: 0, rmsPx: 0 },
      earWidthPx: 0, shoulderWidthPx: 0,
      rollDeg: 0, rollAppliedDeg: 0, rollRejected: false, leanDeg: 0, kneeDeg: 180,
      quality: {
        visFace: 0.95, visEars: 0, visFeet: 0.9, visShoulders: 0,
        widthAgreement: 0, yawFactor: 1, fitErrorFactor: 0.9,
        sizeFactor, rollFactor: 0.95, leanFactor: 0.95, kneeFactor: 1,
        marginsFactor: (topMargin >= 0.015 && bottomMargin >= 0.01) ? 1 : 0.55,
        spanFactor
      },
      points: {
        crown: { x: crown.x, y: crown.y },
        chin: { x: chin.x, y: chin.y },
        eyeMid: null,
        feet: [{ x: feet.x, y: feet.y }],
        footY: feet.y, all: p
      }
    };
  }

  return { measureFromLandmarks, measureFromManual, clamp, dist, mid, smoothstep };
})();
