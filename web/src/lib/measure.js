/* Pure measurement math, ported 1:1 from the reference app (src/lib/height.ts).
   Every function here is pure — the UI in components/measure-studio.jsx only
   wires state into it. Units: landmark positions arrive as normalized 0..1
   points (x across, y down); pixel spans are in photo pixels. */

export const IMG_W = 1152;
export const IMG_H = 1728;

export const MODEL = {
  eyeFromCrown: 0.5,
  mouthFromCrown: 0.78,
  noseFromCrown: 0.63,
  shoulderBreadthPerHead: 1.7,
  defaultHeadCm: 23,
  defaultCanonicalHeads: 7.5,
  minHeadCm: 16,
  maxHeadCm: 32,
  minHeads: 6,
  maxHeads: 8.5,
  ratioLow: 5.2,
  ratioHigh: 9.2,
  confidenceCap: 92,
};

export const SYMBOLIC = "height_cm = (person_pixel_height / head_pixel_height) × average_head_cm";

export const TIPS = [
  "Stand farther back so the top of your head and both shoes are fully in frame.",
  "Face the camera, arms a little away from your sides.",
  "Stand up straight on a level floor.",
  "Use even light. Avoid a bright window behind you.",
  "Hold the camera roughly level, not tilted.",
];

/* Landmarks the pose model finds on the sample photo, in image pixels.
   Span 1400.4 px, head 193.0 px — the exact numbers the reference readout
   shows. X positions measured from the reference screenshots: the feet dot
   sits on the right shoe's instep, and the measurement rod drops at the
   midpoint of crown and feet (crown.x 565, feet.x 662 → rod x 613.5). */
export const SAMPLE = {
  crown: { x: 565, y: 137.4 },
  chin: { x: 565, y: 330.4 },
  feet: { x: 662, y: 1537.8 },
  quality: { visibility: 0.97, inFrame: 1, straight: 0.93, level: 0.95, head: 0.95 },
};

export function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n));
}

export function clamp01(n) {
  return clamp(n, 0, 1);
}

export function trimNum(n, digits = 1) {
  const f = 10 ** digits;
  return String(Math.round(n * f) / f);
}

export function cmToImperial(cm) {
  const total = cm / 2.54;
  let feet = Math.floor(total / 12);
  let inches = Math.round(total - feet * 12);
  if (inches === 12) {
    feet += 1;
    inches = 0;
  }
  return { feet, inches };
}

export function norm(p) {
  return { x: p.x / IMG_W, y: p.y / IMG_H };
}

/* Where the photo lands inside a frame of fw×fh when drawn with object-contain. */
export function containMap(fw, fh, iw, ih) {
  const scale = Math.min(fw / iw, fh / ih);
  const w = iw * scale;
  const h = ih * scale;
  return { x: (fw - w) / 2, y: (fh - h) / 2, w, h };
}

export function derivedChin(crown, feet, heads) {
  const span = feet.y - crown.y;
  return { x: crown.x, y: crown.y + span / clamp(heads, MODEL.minHeads, MODEL.maxHeads) };
}

function confidenceLabel(confidence, method) {
  if (method === "prior") return "Weak fallback";
  if (confidence >= 75) return "Good pose";
  if (confidence >= 55) return "Fair pose";
  return "Rough pose";
}

function edgeScore(x, y) {
  const edge = Math.min(x, y, 1 - x, 1 - y);
  if (edge >= 0.04) return 1;
  if (edge <= 0) return 0;
  return edge / 0.04;
}

export function scoreConfidence(q, method) {
  const weighted =
    0.22 * q.visibility + 0.26 * q.inFrame + 0.27 * q.straight + 0.13 * q.level + 0.12 * q.head;
  const cap =
    method === "face"
      ? MODEL.confidenceCap
      : method === "manual"
        ? 70
        : method === "nose"
          ? 64
          : method === "shoulder"
            ? 56
            : 36;
  return clamp(Math.round(weighted * 100), 0, cap);
}

function geometryWarnings(q) {
  const w = [];
  if (q.straight < 0.62) w.push("Knees or torso look bent or leaning. Stand straighter for a better read.");
  if (q.level < 0.62) w.push("Shoulders or feet look tilted. Hold the camera level and stand square to it.");
  if (q.inFrame < 0.8) w.push("The body is close to the edge of the frame. Step back if you can.");
  if (q.visibility < 0.55) w.push("Some joints were hard to see. Even light and a clearer outline will help.");
  return w;
}

/* The sample photo's measured result (face method, bounded by the 92 cap). */
export function faceSpan() {
  const personPx = SAMPLE.feet.y - SAMPLE.crown.y;
  const headPx = SAMPLE.chin.y - SAMPLE.crown.y;
  return {
    ok: true,
    placement: "detected",
    method: "face",
    personPx,
    headPx,
    quality: SAMPLE.quality,
    confidence: scoreConfidence(SAMPLE.quality, "face"),
    warnings: geometryWarnings(SAMPLE.quality),
    crown: norm(SAMPLE.crown),
    chin: norm(SAMPLE.chin),
    feet: norm(SAMPLE.feet),
  };
}

/* Fallback for a photo the pose model found nothing in: the head repeats the
   prior, crown and feet are guessed, confidence is capped at 36 and the label
   is "Weak fallback". */
export function priorSpan(photo, heads) {
  const crownY = photo.height * 0.08;
  const feetY = photo.height * 0.9;
  const personPx = feetY - crownY;
  const headPx = personPx / clamp(heads, MODEL.minHeads, MODEL.maxHeads);
  const quality = { visibility: 0.55, inFrame: 0.9, straight: 0.7, level: 0.8, head: 0.22 };
  return {
    ok: true,
    placement: "detected",
    method: "prior",
    personPx,
    headPx,
    quality,
    confidence: scoreConfidence(quality, "prior"),
    warnings: geometryWarnings(quality).concat([
      "Head length could not be read from the face or shoulders.",
    ]),
    crown: { x: 0.5, y: crownY / photo.height },
    chin: { x: 0.5, y: (crownY + headPx) / photo.height },
    feet: { x: 0.5, y: feetY / photo.height },
  };
}

/* Manual marks. chin is optional: until it is dragged, it starts one share of
   the canonical ratio below the crown and the method stays "prior" (cap 36). */
export function measureFromManual({ crown, chin, chinAdjusted, feet, photoHeight, heads }) {
  const canonical = clamp(heads, MODEL.minHeads, MODEL.maxHeads);
  if (feet.y <= crown.y + 0.12) {
    return {
      ok: false,
      reason: "The feet marker needs to sit well below the top of the head.",
      tips: TIPS,
    };
  }
  const personNorm = feet.y - crown.y;
  const derived = { x: crown.x, y: crown.y + personNorm / canonical };
  const chinPoint = chinAdjusted && chin ? chin : derived;
  if (chinPoint.y <= crown.y + 0.01 || chinPoint.y >= feet.y - 0.02) {
    return {
      ok: false,
      reason: "Place the chin marker between the crown and the feet, on the bottom of the chin.",
      tips: TIPS,
    };
  }
  const personPx = personNorm * photoHeight;
  const headPx = (chinPoint.y - crown.y) * photoHeight;
  const method = chinAdjusted ? "manual" : "prior";
  const ratio = personPx / headPx;
  const dx = Math.abs(feet.x - crown.x);
  const dy = Math.abs(feet.y - crown.y);
  const tilt = (Math.atan2(dx, dy) * 180) / Math.PI;
  const level = 1 - clamp(tilt / 22, 0, 1);
  const inFrame =
    (edgeScore(crown.x, crown.y) + edgeScore(feet.x, feet.y) + edgeScore(chinPoint.x, chinPoint.y)) / 3;
  const headPlausible = ratio >= MODEL.ratioLow && ratio <= MODEL.ratioHigh ? 0.8 : 0.35;
  const quality = {
    visibility: chinAdjusted ? 0.7 : 0.35,
    inFrame,
    straight: 0.55,
    level,
    head: chinAdjusted ? headPlausible : 0.22,
  };
  const warnings = [];
  if (!chinAdjusted) {
    warnings.push(
      "The chin marker is still the default prior. Drag it to the bottom of the chin to measure this photo.",
    );
  } else if (ratio < MODEL.ratioLow || ratio > MODEL.ratioHigh) {
    warnings.push(
      `That mark is ${trimNum(ratio)} heads tall, outside the usual adult range of ${MODEL.ratioLow}–${MODEL.ratioHigh}. The estimate still uses your marks.`,
    );
  }
  if (level < 0.7) {
    warnings.push("The head and feet markers aren't stacked vertically. Height uses the vertical span only.");
  }
  return {
    ok: true,
    placement: "manual",
    method,
    personPx,
    headPx,
    quality,
    confidence: scoreConfidence(quality, method),
    warnings,
    crown,
    chin: chinPoint,
    feet,
  };
}

function methodExplanation(span, headCm, heads) {
  const canonical = trimNum(heads);
  if (span.placement === "manual" && span.method === "manual") {
    return "You marked the crown, the chin, and the feet. Head length is the vertical distance between crown and chin. Pose straightness was not scored by the detector.";
  }
  if (span.placement === "manual" && span.method === "prior") {
    return `The chin marker is still one share of the canonical ratio down from the crown (body ÷ ${canonical}). Until you drag it onto the chin, height is ${canonical} × the average head length (${trimNum(headCm)} cm), not a head measured in this photo.`;
  }
  if (span.method === "face") {
    return "Head length is the eye-to-mouth distance ÷ 0.28. Eyes are treated as halfway from crown to chin, and the mouth as 78% of head height. The crown sits half a head above the eyes.";
  }
  if (span.method === "nose") {
    return "The mouth wasn't reliable, so head length is the eye-to-nose distance ÷ 0.13. The nose tip is treated as 63% of the way from crown to chin. This is a weaker read than the mouth.";
  }
  if (span.method === "shoulder") {
    return "The face wasn't reliable enough, so head length is shoulder width ÷ 1.7. That uses a mixed-adult shoulder breadth of about 1.7 head lengths. It varies by person and by camera angle, so confidence stays lower.";
  }
  return `Head length could not be measured reliably, so the estimate uses the canonical fallback: stature = ${canonical} × head length. The number is mostly ${canonical} × ${trimNum(headCm)} cm. It is a weak estimate, and confidence is capped.`;
}

export function buildReadout(span, headCm, heads) {
  const clampedHeadCm = clamp(headCm, MODEL.minHeadCm, MODEL.maxHeadCm);
  const ratio = span.personPx / span.headPx;
  const cm = ratio * clampedHeadCm;
  const imperial = cmToImperial(cm);
  let confidence = span.confidence;
  const warnings = span.warnings.slice();
  if (cm < 140 || cm > 205) {
    confidence = Math.min(confidence, 58);
    warnings.push(
      "Outside a typical adult range (about 140–205 cm). Check the overlay, or tune head length if this is a child or a very tall adult.",
    );
  }
  confidence = clamp(confidence, 0, MODEL.confidenceCap);
  return {
    cm,
    feet: imperial.feet,
    inches: imperial.inches,
    ratio,
    confidence,
    confidenceLabel: confidenceLabel(confidence, span.method),
    method: span.method,
    placement: span.placement,
    symbolic: SYMBOLIC,
    substituted: `(${Math.round(span.personPx)} / ${Math.round(span.headPx)}) × ${trimNum(clampedHeadCm)} = ${trimNum(cm)}`,
    explanation: methodExplanation(span, clampedHeadCm, heads),
    warnings,
    personPx: span.personPx,
    headPx: span.headPx,
  };
}
