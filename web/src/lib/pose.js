/* Pose-detection hook.

   In the full app this wraps MediaPipe's pose landmarker (the reference build
   ships mediapipe + pose_landmarker_lite.task). This UI build ships the same
   interface with a deterministic stand-in:

   - the sample photo reads as a detected pose (face method, the exact numbers
     the reference screenshots show);
   - any other photo returns null, which the UI turns into the documented
     "weak fallback" result (capped at 36, labeled "Weak fallback"), or the
     user can mark the points by hand instead.

   To wire in real detection, replace this one function — nothing else in the
   UI changes. */

const DETECTION_MS = 700;

export async function detectLandmarks(photo) {
  await new Promise((resolve) => setTimeout(resolve, DETECTION_MS));
  return photo.isSample ? { method: "face", source: "sample" } : null;
}
