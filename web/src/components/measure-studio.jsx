import { useEffect, useRef, useState } from "react";
import { AlertTriangle, Camera, ImagePlus, Ruler, Upload } from "lucide-react";
import { Button } from "./ui/button";
import { Marker, MeasureRod } from "./overlay";
import { cn } from "../lib/cn";
import { detectLandmarks } from "../lib/pose";
import {
  MODEL,
  SAMPLE,
  buildReadout,
  clamp,
  clamp01,
  containMap,
  derivedChin,
  faceSpan,
  measureFromManual,
  norm,
  priorSpan,
  trimNum,
} from "../lib/measure";
import sampleUrl from "../assets/standing.jpg";

function decodeUrl(url, isSample) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () =>
      resolve({ url, width: img.naturalWidth, height: img.naturalHeight, isSample });
    img.onerror = () => reject(new Error("Image failed to decode"));
    img.src = url;
  });
}

export function MeasureStudio() {
  const [photo, setPhoto] = useState(null); // { url, width, height, isSample }
  const [manual, setManual] = useState(false);
  const [crown, setCrown] = useState(null); // normalized points
  const [chin, setChin] = useState(null);
  const [feet, setFeet] = useState(null);
  const [chinAdjusted, setChinAdjusted] = useState(false);
  const [measurement, setMeasurement] = useState(null); // auto-detection result
  const [decoding, setDecoding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [runError, setRunError] = useState(null);
  const [headCm, setHeadCm] = useState(MODEL.defaultHeadCm);
  const [heads, setHeads] = useState(MODEL.defaultCanonicalHeads);
  const [knownCm, setKnownCm] = useState("");
  const [model, setModel] = useState("ready"); // loading | ready | failed (see lib/pose.js)
  const [frame, setFrame] = useState({ w: 0, h: 0 });

  const frameRef = useRef(null);
  const resultRef = useRef(null);
  const fileRef = useRef(null);
  const cameraRef = useRef(null);
  const dragRef = useRef(null);
  const photoUrlRef = useRef(null);

  /* Frame size — the overlay's viewBox is the frame in CSS pixels. */
  useEffect(() => {
    const el = frameRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      setFrame({ w: width, h: height });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  /* Demo hooks for previews: ?demo=result | ?demo=manual, plus ?scroll=N. */
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const demo = params.get("demo");
    const scroll = Number(params.get("scroll") ?? "0");
    let cancelled = false;
    const later = () => {
      if (scroll > 0) window.setTimeout(() => window.scrollTo(0, scroll), 350);
    };
    if (demo !== "result" && demo !== "manual") {
      later();
      return;
    }
    decodeUrl(sampleUrl, true)
      .then((decoded) => {
        if (cancelled) return;
        setPhoto(decoded);
        if (demo === "result") {
          setMeasurement(faceSpan());
        } else {
          setManual(true);
          setCrown(norm(SAMPLE.crown));
          setChin(norm(SAMPLE.chin));
          setFeet(norm(SAMPLE.feet));
          setChinAdjusted(true);
        }
        later();
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  function revokePrevious() {
    if (photoUrlRef.current) {
      URL.revokeObjectURL(photoUrlRef.current);
      photoUrlRef.current = null;
    }
  }

  function applyPhoto(decoded) {
    setPhoto(decoded);
    setMeasurement(null);
    setCrown(null);
    setChin(null);
    setFeet(null);
    setChinAdjusted(false);
  }

  async function loadSample() {
    setDecoding(true);
    setRunError(null);
    try {
      const decoded = await decodeUrl(sampleUrl, true);
      revokePrevious();
      applyPhoto(decoded);
    } catch {
      setRunError("The sample photo couldn't be loaded.");
    } finally {
      setDecoding(false);
    }
  }

  async function onFile(file) {
    if (!file) return;
    setDecoding(true);
    setRunError(null);
    try {
      const url = URL.createObjectURL(file);
      const decoded = await decodeUrl(url, false);
      revokePrevious();
      photoUrlRef.current = url;
      applyPhoto(decoded);
    } catch {
      setRunError("That file couldn't be read as an image.");
    } finally {
      setDecoding(false);
    }
  }

  function scrollToResult() {
    requestAnimationFrame(() => {
      resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }

  async function estimate() {
    if (!photo) return;
    setBusy(true);
    setRunError(null);
    try {
      if (manual) {
        if (!crown || !feet) {
          setRunError("Tap the top of the head, then the bottom of the feet.");
          return;
        }
        scrollToResult();
        return;
      }
      const found = await detectLandmarks(photo);
      setModel("ready");
      setMeasurement(found ? faceSpan() : priorSpan(photo, heads));
      scrollToResult();
    } finally {
      setBusy(false);
    }
  }

  function screenOf(point, box) {
    return { x: box.x + point.x * box.w, y: box.y + point.y * box.h };
  }

  function pointFromEvent(event) {
    if (!photo) return null;
    const rect = event.currentTarget.getBoundingClientRect();
    const sx = event.clientX - rect.left;
    const sy = event.clientY - rect.top;
    const box = containMap(rect.width, rect.height, photo.width, photo.height);
    return { sx, sy, box, x: clamp01((sx - box.x) / box.w), y: clamp01((sy - box.y) / box.h) };
  }

  function hitMarker(sx, sy, box) {
    const order = ["chin", "crown", "feet"];
    const points = { chin, crown, feet };
    let best = null;
    for (const id of order) {
      const point = points[id];
      if (!point) continue;
      const screen = screenOf(point, box);
      const d = Math.hypot(screen.x - sx, screen.y - sy);
      if (d <= 28 && (!best || d < best.d)) best = { id, d };
    }
    return best?.id ?? null;
  }

  function moveMarker(id, point) {
    if (id === "crown") {
      setCrown(point);
      return;
    }
    if (id === "feet") {
      setFeet(point);
      return;
    }
    setChin(point);
    setChinAdjusted(true);
  }

  const span = !photo
    ? null
    : manual
      ? crown && feet
        ? measureFromManual({
            crown,
            chin,
            chinAdjusted,
            feet,
            photoHeight: photo.height,
            heads,
          })
        : null
      : measurement;
  const readout = span && span.ok ? buildReadout(span, headCm, heads) : null;

  const box = photo && frame.w > 0 ? containMap(frame.w, frame.h, photo.width, photo.height) : null;

  const overlayMarkers = (() => {
    if (manual && crown) {
      const chinPoint = feet ? (chinAdjusted && chin ? chin : derivedChin(crown, feet, heads)) : null;
      return { crown, chin: chinPoint, feet };
    }
    if (!manual && span && span.ok) {
      return { crown: span.crown, chin: span.chin, feet: span.feet };
    }
    return null;
  })();

  const manualHint = !manual
    ? null
    : !crown
      ? "Tap the top of the head."
      : !feet
        ? "Tap the bottom of the shoes, where they meet the floor."
        : chinAdjusted
          ? "Drag any marker to correct it. The estimate updates as you move them."
          : "Drag the chin marker to the bottom of the chin. It starts at 1 ÷ the canonical ratio, which is only a prior.";

  const known = Number(knownCm);
  const knownOk = Number.isFinite(known) && known >= 80 && known <= 250;
  const impliedHead = readout && knownOk ? known / readout.ratio : null;

  const status = decoding
    ? "Reading the photo…"
    : model === "loading"
      ? "Pose model is loading on this device."
      : model === "ready"
        ? "Pose detection runs in the browser. The photo is not uploaded."
        : "Automatic detection is unavailable. Mark the head and feet by hand.";

  const emptyNote =
    runError ??
    (photo
      ? manual
        ? "Mark the crown and the feet. The estimate fills in as soon as both points are set."
        : "Press Estimate height. You'll get centimeters, feet and inches, and a pose-quality score."
      : "Add a full-body photo, or try the sample, then estimate.");

  return (
    <main className="mx-auto min-h-dvh w-full max-w-5xl px-4 py-6 sm:px-6 sm:py-10">
      <header className="mb-6 max-w-2xl">
        <p className="text-sm font-medium text-ink/60">Proportion estimate</p>
        <div className="mt-2 flex items-center gap-3">
          <span className="relative h-9 w-3 shrink-0" aria-hidden>
            <span className="absolute inset-x-0 top-0 h-px bg-primary" />
            <span className="absolute top-0 left-1/2 h-full w-px -translate-x-1/2 bg-primary" />
            <span className="absolute inset-x-0 bottom-0 h-px bg-primary" />
          </span>
          <h1 className="font-display text-4xl font-semibold tracking-tight text-balance text-ink sm:text-5xl">
            MeasureMe
          </h1>
        </div>
        <p className="mt-3 max-w-xl text-base leading-relaxed text-pretty text-ink/80">
          Estimate standing height from one photo. No reference object — only how many head lengths fit in the body.
        </p>
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <section className="min-w-0">
          <div className="overflow-hidden rounded-3xl bg-ink text-surface shadow-sm">
            <div ref={frameRef} className="relative aspect-[3/4] w-full">
              {photo ? (
                <img
                  src={photo.url}
                  alt="Full-body photo used for the height estimate"
                  className="absolute inset-0 h-full w-full object-contain"
                />
              ) : (
                <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
                  <Ruler className="size-8 text-surface/80" aria-hidden />
                  <p className="font-display text-2xl text-balance">Head to feet, in frame</p>
                  <p className="max-w-xs text-sm leading-relaxed text-pretty text-surface/75">
                    Stand straight, face the camera, and leave a little room above your hair and below your shoes.
                  </p>
                </div>
              )}
              {photo && box && (
                <svg
                  className={cn("absolute inset-0 h-full w-full", manual && "touch-none")}
                  viewBox={`0 0 ${frame.w} ${frame.h}`}
                  role={manual ? "application" : "img"}
                  aria-label={
                    manual
                      ? "Measurement markers. Tap the top of the head, then the feet. Drag the chin marker to match the head."
                      : "Detected crown, chin, and feet with a vertical measurement line"
                  }
                  onPointerDown={(event) => {
                    if (!manual) return;
                    const mapped = pointFromEvent(event);
                    if (!mapped) return;
                    const hit = hitMarker(mapped.sx, mapped.sy, mapped.box);
                    if (hit) {
                      dragRef.current = hit;
                      event.currentTarget.setPointerCapture(event.pointerId);
                      return;
                    }
                    const point = { x: mapped.x, y: mapped.y };
                    if (!crown) setCrown(point);
                    else if (!feet) setFeet(point);
                  }}
                  onPointerMove={(event) => {
                    const id = dragRef.current;
                    if (!id) return;
                    const mapped = pointFromEvent(event);
                    if (!mapped) return;
                    moveMarker(id, { x: mapped.x, y: mapped.y });
                  }}
                  onPointerUp={() => {
                    dragRef.current = null;
                  }}
                  onPointerCancel={() => {
                    dragRef.current = null;
                  }}
                >
                  {overlayMarkers?.crown && overlayMarkers.feet && (
                    <MeasureRod
                      crown={screenOf(overlayMarkers.crown, box)}
                      feet={screenOf(overlayMarkers.feet, box)}
                      chin={overlayMarkers.chin ? screenOf(overlayMarkers.chin, box) : null}
                      label={readout ? `${Math.round(readout.cm)} cm` : null}
                    />
                  )}
                  {overlayMarkers?.crown && (
                    <Marker point={screenOf(overlayMarkers.crown, box)} label="Head" />
                  )}
                  {overlayMarkers?.chin && (
                    <Marker point={screenOf(overlayMarkers.chin, box)} label="Chin" />
                  )}
                  {overlayMarkers?.feet && (
                    <Marker point={screenOf(overlayMarkers.feet, box)} label="Feet" />
                  )}
                </svg>
              )}
            </div>
          </div>

          <div className="mt-3 grid grid-cols-2 gap-2">
            <Button variant="secondary" className="w-full" onClick={() => fileRef.current?.click()} disabled={decoding}>
              <Upload className="size-4" aria-hidden />
              Upload
            </Button>
            <Button variant="secondary" className="w-full" onClick={() => cameraRef.current?.click()} disabled={decoding}>
              <Camera className="size-4" aria-hidden />
              Take photo
            </Button>
          </div>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="sr-only"
            onChange={(event) => {
              void onFile(event.target.files?.[0]);
              event.target.value = "";
            }}
          />
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            onChange={(event) => {
              void onFile(event.target.files?.[0]);
              event.target.value = "";
            }}
          />

          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2">
            <Button variant="ghost" className="px-2" onClick={() => void loadSample()} disabled={decoding}>
              <ImagePlus className="size-4" aria-hidden />
              Try a sample photo
            </Button>
            <Button
              variant="ghost"
              className="px-2"
              onClick={() => {
                setManual((value) => !value);
                setRunError(null);
              }}
            >
              {manual ? "Use automatic detection" : "Mark points yourself"}
            </Button>
          </div>

          {manualHint && <p className="mt-3 text-sm leading-relaxed text-ink/80">{manualHint}</p>}

          <Button size="lg" className="mt-4" onClick={() => void estimate()} disabled={!photo || busy || decoding}>
            <Ruler className="size-4" aria-hidden />
            {busy ? "Estimating…" : "Estimate height"}
          </Button>
          <p className="mt-2 text-sm text-ink/60">{status}</p>
        </section>

        <section ref={resultRef} className="flex min-w-0 flex-col gap-4">
          {busy && (
            <article className="rounded-3xl border border-ink/10 bg-surface p-5 shadow-sm">
              <h2 className="font-display text-2xl">Reading the pose</h2>
              <p className="mt-2 text-sm leading-relaxed text-pretty text-ink/75">
                Finding the crown-to-feet span. The first run also finishes loading the pose model.
              </p>
            </article>
          )}

          {!busy && readout && (
            <article className="rounded-3xl border border-ink/10 bg-surface p-5 shadow-sm" aria-live="polite">
              <p className="text-sm font-medium text-ink/60">Estimated height</p>
              <p className="mt-1 font-display text-6xl leading-none font-semibold tabular-nums text-ink">
                {Math.round(readout.cm)}
                <span className="ml-2 text-2xl font-medium text-ink/70">cm</span>
              </p>
              <p className="mt-2 text-lg tabular-nums text-ink/80">
                {readout.feet} ft {readout.inches} in
              </p>
              <div className="mt-5">
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="font-medium">{readout.confidenceLabel}</span>
                  <span className="tabular-nums text-ink/70">Pose quality {readout.confidence} / 100</span>
                </div>
                <div
                  className="mt-2 h-2 overflow-hidden rounded-full bg-ink/10"
                  role="meter"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={readout.confidence}
                  aria-label="Pose quality, not accuracy"
                >
                  <div className="h-full rounded-full bg-primary" style={{ width: `${readout.confidence}%` }} />
                </div>
                <p className="mt-2 text-sm leading-relaxed text-pretty text-ink/70">
                  This scores the pose — standing straight, full body in frame, camera roughly level. It is not an
                  accuracy percentage, and it never means certain. Typical error is several centimeters.
                </p>
              </div>
              <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-2xl bg-bg px-3 py-3">
                  <dt className="text-ink/60">Body span</dt>
                  <dd className="mt-1 font-medium tabular-nums">{Math.round(readout.personPx)} px</dd>
                </div>
                <div className="rounded-2xl bg-bg px-3 py-3">
                  <dt className="text-ink/60">Head length</dt>
                  <dd className="mt-1 font-medium tabular-nums">{Math.round(readout.headPx)} px</dd>
                </div>
              </dl>
              <p className="mt-4 text-sm leading-relaxed text-pretty text-ink">
                Measured stature is {trimNum(readout.ratio)} heads. The canonical constant is {trimNum(heads)}.
              </p>
              <div className="mt-3 rounded-2xl bg-bg px-3 py-3 text-sm leading-relaxed">
                <p className="font-medium">{readout.symbolic}</p>
                <p className="mt-1 tabular-nums text-ink/80">{readout.substituted}</p>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-pretty text-ink/80">{readout.explanation}</p>
              {readout.warnings.length > 0 && (
                <ul className="mt-3 list-disc space-y-1 pl-5 text-sm leading-relaxed text-ink/80">
                  {readout.warnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              )}
            </article>
          )}

          {!busy && span && !span.ok && (
            <article className="rounded-3xl border border-ink/10 bg-surface p-5 shadow-sm" aria-live="polite">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
                <div>
                  <h2 className="font-display text-2xl text-balance">Couldn't measure that photo</h2>
                  <p className="mt-2 text-sm leading-relaxed text-pretty">{span.reason}</p>
                </div>
              </div>
              <ul className="mt-4 list-disc space-y-1 pl-5 text-sm leading-relaxed text-ink/80">
                {span.tips.map((tip) => (
                  <li key={tip}>{tip}</li>
                ))}
              </ul>
              {!manual && (
                <Button variant="secondary" className="mt-4" onClick={() => setManual(true)}>
                  Mark points yourself
                </Button>
              )}
            </article>
          )}

          {!busy && !span && (
            <article className="rounded-3xl border border-ink/10 bg-surface p-5 shadow-sm">
              <p className="text-sm font-medium text-ink/60">Estimated height</p>
              <p className="mt-1 font-display text-6xl leading-none text-ink/30">—</p>
              <p className="mt-3 text-sm leading-relaxed text-pretty text-ink/75">{emptyNote}</p>
            </article>
          )}

          {!busy && runError && span?.ok && (
            <p className="text-sm text-ink/75" role="status">
              {runError}
            </p>
          )}

          <article className="rounded-3xl border border-ink/10 bg-surface p-5 shadow-sm">
            <h2 className="font-display text-2xl">How the estimate is calculated</h2>
            <p className="mt-3 text-sm leading-relaxed font-medium text-pretty">
              height_cm = (person_pixel_height / head_pixel_height) × average_head_cm
            </p>
            <p className="mt-2 text-sm leading-relaxed text-pretty text-ink/80">
              Standing height starts from the constant {trimNum(heads)} × head length. Pixel stature is the vertical
              distance from the crown to the lowest foot point. Head length defaults to {trimNum(headCm)} cm. Both
              constants are yours to tune. MediaPipe's 3D pose is not metric, so those units are never treated as
              centimeters.
            </p>

            <div className="mt-5 space-y-4">
              <label className="block" htmlFor="canonical-heads">
                <span className="flex items-baseline justify-between gap-3 text-sm">
                  <span>Canonical stature</span>
                  <span className="font-medium tabular-nums">{trimNum(heads)} × head</span>
                </span>
                <input
                  id="canonical-heads"
                  type="range"
                  min={MODEL.minHeads}
                  max={MODEL.maxHeads}
                  step={0.1}
                  value={heads}
                  onChange={(event) => setHeads(Number(event.target.value))}
                  className="mt-2 h-11 w-full cursor-pointer"
                />
              </label>
              <label className="block" htmlFor="head-cm">
                <span className="flex items-baseline justify-between gap-3 text-sm">
                  <span>Average head length</span>
                  <span className="font-medium tabular-nums">{trimNum(headCm)} cm</span>
                </span>
                <input
                  id="head-cm"
                  type="range"
                  min={MODEL.minHeadCm}
                  max={MODEL.maxHeadCm}
                  step={0.5}
                  value={headCm}
                  onChange={(event) => setHeadCm(Number(event.target.value))}
                  className="mt-2 h-11 w-full cursor-pointer"
                />
              </label>
            </div>

            <ul className="mt-4 list-disc space-y-1 pl-5 text-sm leading-relaxed text-pretty text-ink/80">
              <li>Eyes are halfway from crown to chin. The mouth is at 78% of head height.</li>
              <li>Head pixels = eye-to-mouth pixels ÷ {trimNum(MODEL.mouthFromCrown - MODEL.eyeFromCrown, 2)}.</li>
              <li>If the mouth isn't reliable, the nose tip at 63% of head height is used instead.</li>
              <li>If the face isn't reliable, head pixels = shoulder width ÷ {MODEL.shoulderBreadthPerHead}.</li>
              <li>
                If the head still can't be measured, head pixels = body pixels ÷ {trimNum(heads)}, and confidence
                drops. That fallback mostly repeats the average.
              </li>
              <li>Tuned for a standing adult. Children usually need a shorter head length and fewer heads.</li>
            </ul>

            <details className="mt-4 rounded-2xl bg-bg px-3 py-3 text-sm">
              <summary className="cursor-pointer font-medium">Debug: compare with a known height</summary>
              <p className="mt-2 leading-relaxed text-pretty text-ink/75">
                Optional. This does not use a reference object, and the estimate above does not change until you
                apply a tuned head length.
              </p>
              <label className="mt-3 block" htmlFor="known-cm">
                <span className="text-ink/70">Known height (cm)</span>
                <input
                  id="known-cm"
                  inputMode="decimal"
                  value={knownCm}
                  onChange={(event) => setKnownCm(event.target.value)}
                  placeholder="172"
                  className="mt-1 h-11 w-full rounded-xl border border-ink/15 bg-surface px-3 tabular-nums"
                />
              </label>
              {readout && knownOk && impliedHead !== null && (
                <div className="mt-3 space-y-2 leading-relaxed">
                  <p>
                    This estimate is {trimNum(readout.cm - known)} cm {readout.cm >= known ? "above" : "below"} the
                    known height.
                  </p>
                  <p>Implied head length for this photo: {trimNum(impliedHead)} cm.</p>
                  <Button
                    variant="secondary"
                    onClick={() =>
                      setHeadCm(Math.min(MODEL.maxHeadCm, Math.max(MODEL.minHeadCm, Math.round(impliedHead * 2) / 2)))
                    }
                  >
                    Tune head length to{" "}
                    {trimNum(Math.min(MODEL.maxHeadCm, Math.max(MODEL.minHeadCm, impliedHead)))} cm
                  </Button>
                </div>
              )}
              {knownCm !== "" && !knownOk && (
                <p className="mt-2 text-ink/70">Enter a height between 80 and 250 cm.</p>
              )}
            </details>
          </article>

          <p className="px-1 text-sm leading-relaxed text-pretty text-ink/70">
            Not a medical or official measurement. Results are estimates. Typical error is several centimeters, and
            it can be larger with a wide-angle lens, a bent pose, or a head length unlike the one you set. Nothing in
            the photo is sent to a server.
          </p>
        </section>
      </div>
    </main>
  );
}
