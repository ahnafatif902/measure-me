# MeasureMe (Android MVP)

Estimate a person's height from a **single full-body photo**, entirely on-device.

- One CTA: **Estimate height** → camera or gallery → pose detection → geometry → formula → result.
- No reference object, no ARCore, no LiDAR, no backend, no accounts, no database.
- 33 pose landmarks from ML Kit BlazePose (model **bundled in the APK** → works offline after install).
- Every number in the result is explained in the UI ("How this was calculated").

> ⚠️ This is an estimate. Typical error is **several centimetres**. It is not a medical or official measurement,
> and the app never claims 98–100 % certainty (confidence is capped at 92 %).

---

## 1. Pipeline overview

```
 photo (camera / gallery)
      │
      ▼
 ImageCaptureModule        capture/ImageCaptureModule.kt
   decode · EXIF-rotate · downscale to ≤1440 px · luminance stats
      │
      ▼
 PoseDetectionModule       detect/PoseDetectionModule.kt
   ML Kit BlazePose (bundled, single-image mode) → 33 landmarks in image px
      │
      ▼
 GeometryModule.derive()   pipeline/GeometryModule.kt
   camera-roll deskew · feet = lowest foot landmark
   crown + chin + head_px  ← least-squares fit of canonical face proportions
   lean · knee angle · quality signals
      │
      ▼
 GeometryModule.estimate() the formula (see §2)
      │
      ▼
 ConfidenceModule          pipeline/ConfidenceModule.kt
   4 × 25-point additive buckets + reason strings + adjusted error bar
      │
      ▼
 OverlayRenderer           ui/components/PhotoWithOverlay.kt
   head point · chin marker · feet point · vertical line · height pill
      │
      ▼
 Result screen             ui/screens/ResultScreen.kt
   cm + ft/in · confidence bar + breakdown · formula with real numbers · warnings
```

Manual fallback: `pipeline/ManualFallbackModule.kt` feeds hand-placed points into the **same**
`GeometryModule.estimate()` and `ConfidenceModule`, so both paths share one formula and one overlay.

The `pipeline/` package is **pure Kotlin** (no Android imports) → it is unit-tested on the JVM
(`app/src/test/java/com/measureme/app/pipeline/GeometryModuleTest.kt`, run with
`gradlew :app:testDebugUnitTest`).

### Module map (spec names → files)

| Spec module          | File                                            |
|----------------------|-------------------------------------------------|
| ImageCaptureModule   | `capture/ImageCaptureModule.kt`                 |
| PoseDetectionModule  | `detect/PoseDetectionModule.kt`                 |
| GeometryModule       | `pipeline/GeometryModule.kt`                    |
| ConfidenceModule     | `pipeline/ConfidenceModule.kt`                  |
| OverlayRenderer      | `ui/components/PhotoWithOverlay.kt`             |
| ManualFallbackModule | `pipeline/ManualFallbackModule.kt`              |
| App state machine    | `MeasureMeViewModel.kt`, `MainActivity.kt`      |
| Constants            | `pipeline/AppConstants.kt`                      |

Screens: `ui/screens/HomeScreen.kt`, `CaptureScreen.kt`, `ManualModeScreen.kt`,
`ResultScreen.kt`, `AdvancedScreen.kt`.

---

## 2. Formula + constants

### The formula (shown verbatim in the UI)

```
height_cm = (person_pixel_height / head_pixel_height) × average_head_cm
```

- `person_pixel_height` = vertical distance crown → lowest visible foot point.
- `head_pixel_height` = head size in pixels (crown → chin).
- `average_head_cm` = 23.0 cm default, **tunable** in Advanced settings (21–25 cm).

### Head-size ladder (best → weakest, each step lowers confidence)

1. **Face fit (best).** Least-squares fit of canonical facial depths on the head
   (`y = crownY + headPx · depth`) using eyes (0.47), nose (0.72), mouth (0.84).
   Needs ≥2 distinct feature rows. Residual → `fitErrorFactor` (0.25–1).
2. **Ear width.** head_px ≈ ear-to-ear pixel width / 0.64 (adult ears span ≈64 % of head height).
3. **Population prior (weakest, flagged).** `height ≈ 7.5 × average_head_cm` — the photo provides
   no scale at all. The UI says so explicitly and confidence collapses.

Missing crown/feet entirely → the app shows a failure state with recovery tips + manual mode.

### Constants (`pipeline/AppConstants.kt`) — value and *why*

| Constant | Value | Why this exists |
|---|---|---|
| `AVERAGE_HEAD_CM` | 23.0 cm | Crown-to-chin adult average (women ≈22.2, men ≈23.3). The single tunable prior. |
| `BODY_TO_HEAD_RATIO` | 7.5 | Classic anatomy canon; **only** used when no head scale exists. |
| `HEAD_WIDTH_CM` | 14.9 cm | Ear-to-ear width; cross-checks head_px and is the ear-width fallback. |
| `SHOULDER_JOINT_CM` | 34.0 cm | Informational anchor (joint centres), kept explicit for future anchors. |
| `FACE_DEPTH_EYES/NOSE/MOUTH` | 0.47 / 0.72 / 0.84 | Canonical face proportions from the crown; they turn face landmarks into a head height. |
| `EAR_WIDTH_TO_HEAD_HEIGHT` | 0.64 | Ear span → head height conversion. |
| `HEADS_TALL_MIN/MAX` | 6.4 / 8.3 | Plausibility window for `person_px/head_px`; outside → warning + confidence penalty. |
| `HEIGHT_MIN/MAX_CM` | 130 / 225 | Soft plausibility window (no hard clamp — honesty over prettiness). |
| `HEIGHT_HARD_MIN/MAX_CM` | 120 / 235 | Outside → treated as detection failure, not a result. |
| `MAX_CONFIDENCE` | 92 % | Ceiling: a single-photo method can't be 98–100 % certain. |
| `MANUAL_MAX_CONFIDENCE` | 65 % | Hand-placed points are never as good as detection. |
| `MIN_HEAD_PX` / `GOOD_HEAD_PX` | 24 / 56 px | Face landmarks below 24 px are guesswork; ≥56 px is solid. |
| `MAX_ROLL_DEG` | 10° | Camera tilt beyond this can't be trusted → deskew is skipped and confidence drops. |
| `LEAN_GOOD/BAD_DEG` | 2 / 15° | Posture penalty ramp for the straightness bucket. |
| `KNEE_STRAIGHT/SUSPECT_DEG` | 168 / 130° | Bent knees shorten the standing height. |
| `VIS_MIN` / `VIS_LOW` | 0.35 / 0.25 | Likelihood gates (looser for feet, often partially occluded). |
| `MAX_ANALYSIS_DIM` | 1440 px | Detection input cap — mid-range phones stay fast and memory-safe. |
| `MIN_IMAGE_DIM` | 480 px | Below this the photo cannot support a measurement. |
| `BRIGHTNESS_MIN/MAX` | 40 / 235 | "Well lit" window for the image-quality bucket. |

### Confidence (4 × 25 additive, shown as a breakdown in the UI)

| Bucket | Up to | Driven by |
|---|---|---|
| Full body visible | +25 | crown & feet present, body span vs frame, margins, multi-person |
| Standing straight | +25 | camera roll (deskew/rejected), lean, knee angle |
| Landmark quality | +25 | face/feet likelihood, face-fit residual, head-width agreement, head pixel size; penalties for fallback heads, unusual ratios |
| Image quality | +25 | resolution, mean luminance, contrast |

Total = sum, capped at **92** (65 in manual mode; when the cap bites, all buckets scale down
proportionally so the breakdown still sums to the headline number). Error bar = ~4.5 % of height
scaled by (1 + (92−confidence)/92), minimum ±5 cm in manual mode.

---

## 3. Limitations (be honest)

1. **No absolute scale except the head prior.** Everything hinges on `average_head_cm`; a person with
   a 21 cm head estimates ≈9 % shorter than the prior assumes. The Advanced slider exists so this
   stays visible and adjustable.
2. **Uncertainty is heuristic.** The confidence buckets are rule-based, not statistically calibrated
   against a labelled dataset. Bands (Good/Fair/Rough) are conventions, not guarantees.
3. **A single photo is one view.** Perspective (especially with the phone close or tilted),
   wide-angle lens distortion, and cropping all bias the pixel measurements.
4. **Posture:** leaning >2° gradually lowers the straightness score, but v1 deliberately does not
   cos-correct the height (kept out of the formula shown in the UI — the correction is documented
   here as a known bias, several % at 10°+ lean).
5. **Shoes & soles:** the feet point is the lowest visible foot landmark; thick soles add a couple of
   centimetres that the model cannot know about.
6. **Small faces in frame:** below ~24 px of head height the face-fit degrades; vintage/low-res
   photos are the observed weak spot.
7. **Camera roll** beyond 10° cannot be deskewed reliably — the app refuses to guess and lowers confidence.
8. **Solo subject only.** Single-pose model: the largest person wins; crowds confuse it.
9. **No medical, no sports, no legal use.** It is a student ML project with an honest error bar.

### Manual mode is 3 taps, not 2 (deliberate deviation)

The spec asked for "tap top of head, tap bottom of feet". Two points define only ONE pixel length
(`person_px`) — the formula additionally needs `head_px`. So:

- If pose detection already found a head size, manual mode asks for **2 taps** (crown, feet) and
  reuses the detected `head_px` — exactly the spec flow.
- Otherwise it asks for **3 taps** (crown, chin, floor line), and the chin tap supplies the missing
  scale. The screen explains this to the user in one sentence.

---

## 4. Why this is an AI/ML project

- The core measurement depends on **BlazePose**, a CNN that regresses 33 body landmarks with per-point
  likelihoods from a single RGB frame, running **on-device** (ML Kit, bundled model, no network).
- The pipeline is a textbook ML system: **input pipeline** (`ImageCaptureModule`: decode, EXIF, resize,
  statistics) → **model inference** (`PoseDetectionModule`) → **domain post-processing**
  (`GeometryModule`: roll/deskew, least-squares face fit, plausibility gates) → **uncertainty
  modelling** (`ConfidenceModule`: decomposable, explainable scoring) → **visualisation**
  (`OverlayRenderer`).
- Failure handling is first-class: typed errors (`pipeline/Errors.kt`) map to concrete recovery tips,
  and the manual fallback keeps the product useful when the model fails.
- The anthropometric prior is explicit and tunable — a reminder that ML outputs are **estimates with
  priors**, not oracles.

---

## 5. Build, run, test

### Requirements

- JDK 17, Android SDK (platform 34, build-tools 34.0.0), Gradle 8.5 (wrapper included).
- Android Studio (Ladybug or newer) or command line. No Google-services, no API keys.

### Command line

```bash
cd android
./gradlew :app:assembleDebug            # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # JVM tests for the pure pipeline math
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(On Windows: `gradlew.bat`. If the wrapper is missing, run `gradle wrapper --gradle-version 8.5` once
or just open the project in Android Studio.)

### APK size (read this before shipping)

The debug APK is ~90 MB because the BlazePose model and its native libraries are bundled for **four
ABIs** (arm64-v8a, armeabi-v7a, x86, x86_64) and minify is off. For distribution:

- build an **App Bundle** (`./gradlew :app:bundleRelease`) → Play delivers only the matching ABI (~25 MB), or
- use ABI splits for direct APKs, or
- swap `pose-detection` for `pose-detection-play-services` (APK shrinks to a few MB, but the model is
  downloaded once via Play Services — the trade-off against "offline immediately after install").

### What was verified in this repo

- `./gradlew :app:assembleDebug` — builds clean, zero compiler warnings.
- `./gradlew :app:testDebugUnitTest` — **17/17 JVM unit tests pass** (fake-point tests for the
  formula, fallback ladder, deskew, knee/lean detection, feet point, ft/in formatting).

### Runtime verification on a real device

The app was installed and exercised on a real phone (Redmi "sunstone", Android 14 / HyperOS,
arm64, mid-range chip):

- Install via `./gradlew installDebug`, launch, Home screen ✓.
- Capture screen: runtime camera-permission request in place, live CameraX preview ✓,
  gallery fallback via the system Photo Picker ✓ (no storage permission needed).
- Camera still capture and gallery import both feed the same pipeline ✓.
- **The complete automatic path succeeded on a real full-body photo**: ML Kit pose detection →
  `derive()` (crown / chin / feet) → the formula → confidence → **Result screen**. The derived
  overlay points sat correctly on the photo (crown above the hair, chin marker below the face,
  feet at the shoe, vertical measurement line between them).
- On-device ML Kit ran with the acceleration path enabled
  (`PoseTaskWithRes: vision_pose_detection_enable_acceleration = true`). One early cold start
  logged a one-off crash of ML Kit's isolated benchmark process — see "Known device-side quirk"
  below; later cold starts were clean and inference completed.

Not yet exercised on-device: the manual 3-tap screen interactions and the failure/low-confidence
cards — they are covered by unit tests and by the checklist below, but a human with a phone is
still the last mile for those.

#### Automated on-device runs (debug builds only)

For CI-style checks without touching the screen, debug builds accept a photo path by intent:

```bash
adb shell am force-stop com.measureme.app     # fresh process required the first time
adb shell am start -n com.measureme.app/.MainActivity \
    --es debug_photo /sdcard/Android/data/com.measureme.app/files/test.jpg
# loads the photo and runs detection → formula → confidence → Result screen, hands-free
```

The extras are ignored in release builds (`BuildConfig.DEBUG` guard). `onNewIntent` re-triggers
the run if the app is already open. Note: on some MIUI/HyperOS phones `adb shell input tap` is
blocked unless "USB debugging (Security settings)" is enabled in developer options — this hook
exists so the pipeline can still be verified end-to-end from a terminal.

### Known device-side quirk: ML Kit acceleration sidecar

On one cold start, ML Kit's acceleration benchmark — which runs in the isolated process
`com.measureme.app:mlkit_acceleration_mini_benchmark` to pick GPU/NNAPI vs CPU — crashed with a
JNI `NoSuchFieldError` inside `com.google.mlkit:mediapipe-internal`. The crash is contained in
that sidecar process: the app itself kept running and later inference runs completed normally.
If you ever want it gone, options are: pin `pose-detection` to another beta, or switch to
`pose-detection-play-services` (Play-Services-flavoured ML Kit that updates independently).

### How to test with a real full-body photo

1. Stand **2–3 m** from the camera (phone at waist height, held vertically).
2. **Full body in frame** — head to floor, with a little margin above the hair and below the shoes.
3. Face the camera, stand straight, arms slightly away from the body, feet visible.
4. Plain background, even lighting (avoid strong backlight).
5. Tap **Estimate height** — first run takes a few seconds (model initialisation), then it is fast.
6. Check the **confidence breakdown**, not just the number. If it is Rough:
   - tap **Enter points manually** and place crown / chin / floor line precisely, or
   - re-shoot farther back / straighter / brighter.

#### Manual test checklist (states)

| State | How to see it |
|---|---|
| Empty | Home → Estimate height (camera preview, gallery) |
| Loading | Estimate on any photo (spinner card) |
| Success | Clear full-body photo → result with overlay |
| Low confidence | Crouch/lunge photo or small face → Fair/Rough bands, warnings |
| Failure | Photo without a full body → tips card + manual offer |
| Manual | Enter points manually → 2 or 3 taps → result (capped 65 %) |
| Advanced | Slider 21–25 cm — an open result re-calculates live |

---

## 6. Future improvements

1. **Reference-object mode** (A4 sheet / door frame): a known real length in the photo gives an
   absolute scale — the single biggest accuracy jump, planned as an optional mode.
2. **Multi-frame capture:** 3–5 shots, median of estimates, per-frame quality weighting.
3. **Better crown estimation:** head segmentation or hair-aware crown detection instead of the
   canonical face-depth fit; also a dedicated `head_px` regression from the face crop.
4. **Ground-plane fitting:** if the floor is visible, fit a homography and use the person's vertical
   axis in world coordinates (still no depth sensor needed).
5. **Per-user calibration:** let the user enter their own height once; then the app can learn their
   personal head prior and open the door to a *calibrated* mode.
6. **Dataset calibration of the confidence model:** collect labelled (photo, true height) pairs and
   fit the bucket weights + error bar instead of hand-tuned numbers.
7. **Explicit accelerator control** (CPU vs GPU/NNAPI) instead of relying on ML Kit's
   auto-selection — and a real accuracy/consistency comparison between them.

---

## 7. Project layout

```
android/
  app/src/main/java/com/measureme/app/
    MainActivity.kt                 single activity, screen switch
    MeasureMeViewModel.kt           state machine + orchestration
    pipeline/                       pure Kotlin, unit-tested
      AppConstants.kt  Types.kt  Errors.kt
      GeometryModule.kt  Anthropometry lives inside estimate()
      ConfidenceModule.kt  ManualFallbackModule.kt
    capture/ImageCaptureModule.kt
    detect/PoseDetectionModule.kt
    ui/theme/Theme.kt
    ui/components/PhotoWithOverlay.kt
    ui/screens/*.kt
  app/src/test/java/com/measureme/app/pipeline/GeometryModuleTest.kt
```

Dependencies (all justified, nothing else): Compose BOM, activity-compose, lifecycle-viewmodel-compose,
core-ktx, CameraX (core/camera2/lifecycle/view), ML Kit `pose-detection` (bundled model),
androidx exifinterface, JUnit (tests only).

*Note: the repository root also contains an earlier browser prototype (`index.html`) of the same
concept. The Android app in `android/` is the MVP described here.*
