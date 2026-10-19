/* ============================================================================
 * MeasureMe — constants.js
 * Every tunable number in the app lives here, with a one-line reason.
 * The "why" behind each value is expanded in README.md ("Constants").
 * ==========================================================================*/
window.MM = window.MM || {};

MM.CONST = {

  /* --- Anthropometric anchors -------------------------------------------
   * Each anchor is a body part whose average length (in cm) is known from
   * anthropometry tables. measure(px) / anchor_cm gives a pixels-per-cm
   * scale, which converts the body span in the photo into centimetres.
   * -------------------------------------------------------------------- */

  // Crown-to-chin head length. Adult avg ≈ 23 cm (m ≈ 23.3, f ≈ 22.2).
  // Exposed as a slider in the UI (21–25 cm) because it is the single
  // biggest population variable in the whole model.
  DEFAULT_HEAD_CM: 23.0,
  HEAD_CM_MIN: 21.0,
  HEAD_CM_MAX: 25.0,

  // Ear-to-ear head width (≈ bitragion width). Adult avg ≈ 14.9 cm.
  // Used as the "weaker fallback" anchor when head *height* is unreliable
  // (e.g. the subject is pitching their chin down). Head width is much
  // more variable between people (13.5–16.5 cm), hence low weight.
  HEAD_WIDTH_CM: 14.9,

  // Shoulder-width anchor: distance between the two shoulder *joint
  // centres* (MediaPipe landmarks 11/12, which sit inside the acromion).
  // Adult biacromial breadth ≈ 38–41 cm, joint centres ≈ 34 cm.
  // Very variable (±13%) → last-resort anchor with small weight.
  SHOULDER_JOINT_CM: 34.0,

  // The big-toe-tip landmark floats ~0.7 cm above the floor when standing.
  // Added to the final height so "toe tip → ground" is accounted for.
  // (±1 cm uncertainty; small vs. the model error, hence a constant.)
  SOLE_TO_TOE_TIP_CM: 0.7,

  /* --- Canonical facial proportions -------------------------------------
   * Fraction of head height (crown = 0, chin = 1) at which each facial
   * landmark sits in a straight, frontal face. These let us fit the head
   * size from several landmarks at once (least squares) instead of
   * trusting one fragile landmark.
   * -------------------------------------------------------------------- */
  FACE_DEPTH_EYES: 0.47,   // external eye corner line
  FACE_DEPTH_NOSE: 0.72,   // nose tip (≈ subnasale level in frontal view)
  FACE_DEPTH_MOUTH: 0.84,  // mouth corners
  EAR_WIDTH_TO_HEAD_HEIGHT: 0.64, // (ear-to-ear width) / (head height)

  /* --- Plausibility ranges (used for confidence, not for clamping) ------ */
  HEADS_TALL_MIN: 6.4,     // photo ratios below this ≈ child or bad detection
  HEADS_TALL_MAX: 8.3,     // above this ≈ head under-measured or detection error
  HEIGHT_MIN_CM: 130,      // adult-plausible window
  HEIGHT_MAX_CM: 225,
  HEIGHT_HARD_MIN_CM: 120, // outside → result rejected, recovery tips shown
  HEIGHT_HARD_MAX_CM: 235,

  /* --- Confidence model ------------------------------------------------- */
  MAX_CONFIDENCE: 0.92,        // never claim 98–100%: this method can't.
  MANUAL_MAX_CONFIDENCE: 0.65, // manual taps are never as good as detection
  MIN_HEAD_PX: 24,             // below this, face landmarks are guesswork
  GOOD_HEAD_PX: 56,            // at/above this, face landmarks are solid
  MAX_ROLL_DEG: 10,            // camera roll guesses beyond this are rejected
  MAX_LEAN_CORRECTION_DEG: 10, // only cos-correct leans we can trust
  KNEE_STRAIGHT_DEG: 168,      // standing straight knees
  KNEE_SUSPECT_DEG: 130,       // below this the person is clearly not standing
  LEAN_GOOD_DEG: 2,            // ≤2° counts as perfectly vertical
  LEAN_BAD_DEG: 15,            // ≥15° → straightness factor at its floor

  // Relative trust of each anchor (head height is the primary model).
  ANCHOR_WEIGHT: {
    headHeight: 1.0,
    headWidth: 0.45,
    shoulderWidth: 0.22
  },

  // Error bar shown to the user (p80-style): % of height.
  // 3.2% at max confidence → "several cm" on an adult. Honest, not selling.
  ERROR_PCT_AT_MAX: 3.2,
  ERROR_PCT_RANGE: 11.0, // added (scaled by 1 - confidence)

  /* --- Detection / model loading ---------------------------------------- */
  MODEL_CDN: "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35",
  // "lite" counts ~2x faster on old phones; "full" lands face/feet better.
  POSE_MODEL_URL: "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task",
  MAX_ANALYSIS_DIM: 1440, // downscale long edge before detection (speed)
  NUM_POSES: 2,           // detect 2 so we can warn about bystanders
  DETECT_TIMEOUT_MS: 30000,

  // MediaPipe Pose (BlazePose) landmark indices we rely on.
  LM: {
    NOSE: 0, EYE_L: 2, EYE_R: 5, EAR_L: 7, EAR_R: 8,
    MOUTH_L: 9, MOUTH_R: 10,
    SH_L: 11, SH_R: 12,
    HIP_L: 23, HIP_R: 24, KNEE_L: 25, KNEE_R: 26,
    ANKLE_L: 27, ANKLE_R: 28, HEEL_L: 29, HEEL_R: 30,
    FOOT_L: 31, FOOT_R: 32
  },
  VIS_MIN: 0.35,  // landmark visibility needed to be used
  VIS_LOW: 0.25   // looser bar for the feet (often partially occluded)
};
