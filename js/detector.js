/* ============================================================================
 * MeasureMe — detector.js
 * Wraps MediaPipe Tasks Vision (PoseLandmarker) behind a tiny promise API.
 * The model is loaded lazily on the first estimate, then reused.
 * Everything is wrapped so that ANY failure (offline, blocked CDN, old
 * browser) turns into a typed error instead of a broken page — the app then
 * offers the manual tap mode.
 * ==========================================================================*/
window.MM = window.MM || {};

MM.detector = (function () {
  const K = MM.CONST;
  let poseLandmarker = null;
  let loadPromise = null;

  function withTimeout(p, ms, err) {
    return Promise.race([
      p,
      new Promise((_, rej) => setTimeout(() => rej(err), ms))
    ]);
  }

  async function createLandmarker(delegate) {
    const fileset = await window.__mpVision.FilesetResolver.forVisionTasks(K.MODEL_CDN + "/wasm");
    return window.__mpVision.PoseLandmarker.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: K.POSE_MODEL_URL, delegate },
      runningMode: "IMAGE",
      numPoses: K.NUM_POSES,
      minPoseDetectionConfidence: 0.4,
      minPosePresenceConfidence: 0.4,
      minTrackingConfidence: 0.4
    });
  }

  /** Loads model once. onState(stage) reports progress for the UI. */
  function ensureLoaded(onState) {
    if (poseLandmarker) return Promise.resolve(poseLandmarker);
    if (loadPromise) return loadPromise;

    loadPromise = (async () => {
      try {
        if (onState) onState("loading-script");
        const mod = await import(/* webpackIgnore: true */ K.MODEL_CDN + "/vision_bundle.mjs");
        window.__mpVision = mod;
      } catch (e) {
        throw { code: "network", cause: e };
      }
      try {
        if (onState) onState("loading-model");
        try {
          poseLandmarker = await withTimeout(createLandmarker("GPU"), K.DETECT_TIMEOUT_MS, { code: "timeout" });
        } catch (gpuErr) {
          // some mobile GPUs reject the WebGL delegate — CPU always works
          poseLandmarker = await withTimeout(createLandmarker("CPU"), K.DETECT_TIMEOUT_MS, { code: "timeout" });
        }
      } catch (e) {
        loadPromise = null;
        throw (e && e.code) ? e : { code: "model", cause: e };
      }
      return poseLandmarker;
    })();
    return loadPromise;
  }

  /** Run detection on an <img>. Returns {landmarks, poseCount} or throws. */
  async function detect(imgEl, onState) {
    const lm = await ensureLoaded(onState);
    if (onState) onState("detecting");
    let result;
    try {
      result = lm.detect(imgEl);
    } catch (e) {
      throw { code: "detect", cause: e };
    }
    const poses = (result && result.landmarks) || [];
    if (!poses.length) return { landmarks: null, poseCount: 0 };

    // If several people are in frame, keep the largest one (assuming the
    // subject is closest to the camera) but report the count so the app can
    // warn the user.
    let best = poses[0], bestSpan = -1;
    for (const p of poses) {
      const ys = p.map(pt => pt.y);
      const span = Math.max(...ys) - Math.min(...ys);
      if (span > bestSpan) { bestSpan = span; best = p; }
    }
    return { landmarks: best, poseCount: poses.length };
  }

  return { ensureLoaded, detect };
})();
