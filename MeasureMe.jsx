/**
 * MeasureMe UI (React + Tailwind + Framer Motion + Lucide)
 *
 * Setup (Vite + Tailwind v3):
 *   npm i framer-motion lucide-react
 *   src/index.css must contain:  @tailwind base; @tailwind components; @tailwind utilities;
 *   Drop this file in as src/App.jsx (default export). No tailwind.config changes needed:
 *   all tokens are CSS variables defined in CSS below and used via arbitrary values.
 *
 * Flow: Home -> Capture (includes Processing) -> Result
 * Numbers on the Result screen are placeholder data until the pipeline is wired in.
 */
import { useEffect, useRef, useState } from "react";
import { AnimatePresence, animate, motion, useReducedMotion } from "framer-motion";
import {
  ArrowLeft, Camera, Check, FileText, ImagePlus, Mountain,
  PersonStanding, RotateCcw, Ruler, ShieldCheck, Upload,
} from "lucide-react";

/* ---------- Design tokens + global CSS ---------- */
const NOISE =
  "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 .14 0 0 0 0 .12 0 0 0 0 .1 0 0 0 .05 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")";

const CSS = `
@import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,300..600&family=Inter:wght@400;500;600&display=swap');
:root{
  --paper:#F4F1EA; --surface:#FBF8F2; --ink:#231F1B; --ink-2:#5C544B;
  --accent:#B4451C; --accent-press:#983A17;
  --line:rgba(35,31,27,.10); --line-strong:rgba(35,31,27,.18);
}
html,body{background:var(--paper);color:var(--ink);font-family:Inter,system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.f-display{font-family:Fraunces,Georgia,serif;font-optical-sizing:auto;letter-spacing:-.02em}
.paper::before{content:"";position:fixed;inset:0;pointer-events:none;mix-blend-mode:multiply;background-image:${NOISE}}
.card{background:var(--surface);border:1px solid var(--line);
  box-shadow:inset 0 1px 0 rgba(255,255,255,.8),0 1px 2px rgba(35,31,27,.05),0 8px 24px -12px rgba(35,31,27,.18)}
.inset{box-shadow:inset 0 2px 6px rgba(35,31,27,.07),inset 0 0 0 1px rgba(255,255,255,.5)}
:focus-visible{outline:2px solid var(--accent);outline-offset:3px}
@media (prefers-reduced-motion:reduce){*{animation-duration:.01ms!important;transition-duration:.01ms!important}}
`;

const spring = { type: "spring", stiffness: 500, damping: 30 };

/* ---------- Primitives ---------- */
function Button({ variant = "secondary", icon: Icon, className = "", children, ...rest }) {
  const base =
    "flex h-12 min-w-[44px] items-center justify-center gap-2 rounded-2xl px-5 text-[15px] font-medium tracking-[-0.005em] transition-colors disabled:pointer-events-none disabled:opacity-40";
  const styles = {
    primary:
      "bg-[var(--accent)] text-white hover:bg-[var(--accent-press)] shadow-[inset_0_1px_0_rgba(255,255,255,.18),0_1px_2px_rgba(120,40,10,.3),0_8px_16px_-8px_rgba(180,69,28,.55)]",
    secondary: "card text-[var(--ink)] hover:bg-white",
    ghost: "text-[var(--ink-2)] hover:text-[var(--ink)]",
  };
  return (
    <motion.button whileTap={{ scale: 0.97 }} transition={spring} className={`${base} ${styles[variant]} ${className}`} {...rest}>
      {Icon && <Icon size={18} strokeWidth={1.75} aria-hidden="true" />}
      {children}
    </motion.button>
  );
}

function TopBar({ onBack, title }) {
  return (
    <header className="mb-6 flex h-11 items-center justify-between">
      {onBack ? (
        <button onClick={onBack} aria-label="Back" className="-ml-3 grid h-11 w-11 place-items-center rounded-full text-[var(--ink-2)] transition-transform active:scale-95">
          <ArrowLeft size={20} strokeWidth={1.75} />
        </button>
      ) : (
        <span className="flex items-center gap-2">
          <Ruler size={18} strokeWidth={1.75} className="text-[var(--accent)]" aria-hidden="true" />
          <span className="f-display text-lg font-medium">MeasureMe</span>
        </span>
      )}
      {title && <span className="text-sm font-medium text-[var(--ink-2)]">{title}</span>}
    </header>
  );
}

const Privacy = () => (
  <p className="flex items-start gap-2 text-[13px] leading-[18px] text-[var(--ink-2)]">
    <ShieldCheck size={16} strokeWidth={1.75} className="mt-px shrink-0" aria-hidden="true" />
    Processed on your device. Your photo never leaves your phone.
  </p>
);

/* ---------- Home ---------- */
const MODES = [
  { id: "proportions", icon: PersonStanding, title: "Proportions", note: "No reference needed. Quickest, but expect a wider range." },
  { id: "horizon", icon: Mountain, title: "Horizon", note: "Uses your phone’s tilt. You’ll enter the camera height." },
  { id: "reference", icon: FileText, title: "Reference", note: "Lay an A4 sheet on the floor. Most accurate." },
];

function HomeScreen({ mode, setMode, onNext }) {
  return (
    <>
      <TopBar />
      <h1 className="f-display text-[40px] font-medium leading-[42px]">Your height, from one photo.</h1>
      <p className="mt-3 max-w-[32ch] text-[15px] leading-[22px] text-[var(--ink-2)]">
        Stand tall, take one photo, and get an estimate in seconds. No tape needed.
      </p>

      <div role="radiogroup" aria-label="Measuring method" className="mt-8 flex flex-col gap-3">
        {MODES.map(({ id, icon: Icon, title, note }) => {
          const on = mode === id;
          return (
            <motion.button
              key={id} role="radio" aria-checked={on} onClick={() => setMode(id)}
              whileTap={{ scale: 0.98 }} transition={spring}
              className={`card flex items-start gap-3 rounded-3xl p-5 text-left ${on ? "ring-2 ring-[var(--accent)]" : ""}`}
            >
              <Icon size={22} strokeWidth={1.5} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              <span className="flex-1">
                <span className="block text-[15px] font-semibold">{title}</span>
                <span className="mt-0.5 block text-[13px] leading-[18px] text-[var(--ink-2)]">{note}</span>
              </span>
              <span className={`mt-0.5 grid h-5 w-5 place-items-center rounded-full ${on ? "bg-[var(--accent)] text-white" : "border border-[var(--line-strong)]"}`}>
                {on && <Check size={13} strokeWidth={2.5} aria-hidden="true" />}
              </span>
            </motion.button>
          );
        })}
      </div>

      <div className="mt-auto flex flex-col gap-4 pt-8">
        <Button variant="primary" onClick={onNext} className="w-full">Continue</Button>
        <Privacy />
      </div>
    </>
  );
}

/* ---------- Capture (+ processing) ---------- */
const STEPS = ["Finding you in the frame", "Locating head and feet", "Working out the scale"];
const TIPS = ["Head to toe in the frame", "Face the camera and stand tall", "Leave a hand’s width above and below"];

function Corner({ pos }) {
  const p = { tl: "left-4 top-4 border-l-2 border-t-2 rounded-tl-xl", tr: "right-4 top-4 border-r-2 border-t-2 rounded-tr-xl",
              bl: "left-4 bottom-4 border-l-2 border-b-2 rounded-bl-xl", br: "right-4 bottom-4 border-r-2 border-b-2 rounded-br-xl" }[pos];
  return <span aria-hidden="true" className={`absolute h-7 w-7 border-[var(--ink)]/40 ${p}`} />;
}

function CaptureScreen({ mode, onBack, onDone }) {
  const [photo, setPhoto] = useState(null); // null | "sample" | objectURL
  const [step, setStep] = useState(-1);
  const camRef = useRef(null);
  const fileRef = useRef(null);
  const busy = step >= 0;

  useEffect(() => {
    if (step < 0) return;
    const t = setTimeout(() => (step >= STEPS.length ? onDone() : setStep((s) => s + 1)), step >= STEPS.length ? 500 : 800);
    return () => clearTimeout(t);
  }, [step]); // eslint-disable-line

  const pick = (e) => { const f = e.target.files?.[0]; if (f) setPhoto(URL.createObjectURL(f)); };
  const pct = (Math.max(step, 0) / STEPS.length) * 100;

  return (
    <>
      <TopBar onBack={busy ? undefined : onBack} title={MODES.find((m) => m.id === mode).title} />

      <div className="card inset relative aspect-[3/4] w-full overflow-hidden rounded-[28px]">
        {photo === "sample" ? (
          <div className="absolute inset-0 bg-gradient-to-b from-[#d9d1c3] to-[#b9ad9a]" aria-label="Sample photo placeholder" />
        ) : photo ? (
          <img src={photo} alt="Your photo" className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <svg viewBox="0 0 120 200" className="absolute inset-0 m-auto h-[78%] text-[var(--ink)]/25" fill="none" stroke="currentColor" strokeWidth="1.5" strokeDasharray="4 5" aria-hidden="true">
            <circle cx="60" cy="26" r="16" />
            <path d="M34 62q26-12 52 0l6 52h-12l-4 70H44l-4-70H28z" />
          </svg>
        )}
        {["tl", "tr", "bl", "br"].map((p) => <Corner key={p} pos={p} />)}
        {!photo && (
          <p className="absolute inset-x-0 bottom-6 text-center text-[13px] text-[var(--ink-2)]">Your photo will appear here</p>
        )}
      </div>

      <AnimatePresence mode="wait" initial={false}>
        {busy ? (
          <motion.section key="busy" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="mt-5 flex flex-col gap-4" aria-live="polite">
            <div className="h-2 overflow-hidden rounded-full bg-[var(--line)] inset" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)}>
              <motion.div className="h-full rounded-full bg-[var(--accent)]" initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ type: "spring", stiffness: 140, damping: 22 }} />
            </div>
            <ul className="flex flex-col gap-2 text-[15px]">
              {STEPS.map((s, i) => (
                <li key={s} className={`flex items-center gap-3 ${i > step ? "text-[var(--ink-2)]/70" : ""}`}>
                  <span className={`grid h-5 w-5 place-items-center rounded-full ${i < step ? "bg-[var(--accent)] text-white" : "border border-[var(--line-strong)]"}`}>
                    {i < step && <Check size={13} strokeWidth={2.5} aria-hidden="true" />}
                  </span>
                  {s}
                </li>
              ))}
            </ul>
          </motion.section>
        ) : (
          <motion.section key="idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="mt-5 flex flex-1 flex-col">
            <ul className="flex flex-col gap-2 text-[14px] text-[var(--ink-2)]">
              {TIPS.map((t) => (
                <li key={t} className="flex items-center gap-2.5">
                  <Check size={15} strokeWidth={2} className="text-[var(--accent)]" aria-hidden="true" />{t}
                </li>
              ))}
            </ul>
            <input ref={camRef} type="file" accept="image/*" capture="environment" hidden onChange={pick} />
            <input ref={fileRef} type="file" accept="image/*" hidden onChange={pick} />
            <div className="mt-5 grid grid-cols-2 gap-3">
              <Button icon={Camera} onClick={() => camRef.current.click()}>Take photo</Button>
              <Button icon={Upload} onClick={() => fileRef.current.click()}>Choose photo</Button>
            </div>
            <Button variant="ghost" icon={ImagePlus} onClick={() => setPhoto("sample")} className="mt-1 self-start !px-0">Use a sample</Button>
            <div className="mt-auto flex flex-col gap-4 pt-5">
              <Button variant="primary" icon={Ruler} disabled={!photo} onClick={() => setStep(0)} className="w-full">Measure my height</Button>
              <Privacy />
            </div>
          </motion.section>
        )}
      </AnimatePresence>
    </>
  );
}

/* ---------- Result ---------- */
function CountUp({ value }) {
  const reduce = useReducedMotion();
  const [n, setN] = useState(reduce ? value : 0);
  useEffect(() => {
    if (reduce) { setN(value); return; }
    const c = animate(0, value, { duration: 1.2, ease: [0.16, 1, 0.3, 1], onUpdate: (v) => setN(Math.round(v)) });
    return () => c.stop();
  }, [value, reduce]);
  return <span aria-hidden="true" style={{ fontVariantNumeric: "tabular-nums" }}>{n}</span>;
}

function ResultScreen({ onAgain }) {
  const cm = 167, quality = 92, margin = 6;
  const totalIn = Math.round(cm / 2.54);
  const ft = Math.floor(totalIn / 12), inch = totalIn % 12;

  return (
    <>
      <TopBar title="Result" />
      <motion.section initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.22 }} className="card flex flex-col rounded-3xl p-5">
        <p className="text-[13px] font-medium text-[var(--ink-2)]">You’re about</p>
        <p className="sr-only">Estimated height {cm} centimetres, likely between {cm - margin} and {cm + margin}.</p>
        <p className="mt-1 flex items-baseline gap-2">
          <span className="f-display text-[88px] font-normal leading-[88px]"><CountUp value={cm} /></span>
          <span className="text-lg font-medium text-[var(--ink-2)]">cm</span>
        </p>
        <p className="mt-2 text-[15px] text-[var(--ink-2)]">{ft} ft {inch} in. Likely {cm - margin}–{cm + margin} cm.</p>

        <div className="mt-6">
          <div className="mb-2 flex items-baseline justify-between text-[13px]">
            <span className="font-semibold text-[var(--ink)]">Clean shot</span>
            <span className="text-[var(--ink-2)]">Photo quality {quality}/100</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-[var(--line)] inset" role="progressbar" aria-label="Photo quality" aria-valuemin={0} aria-valuemax={100} aria-valuenow={quality}>
            <motion.div className="h-full rounded-full bg-[var(--accent)]" initial={{ width: 0 }} animate={{ width: `${quality}%` }} transition={{ type: "spring", stiffness: 140, damping: 22, delay: 0.15 }} />
          </div>
          <p className="mt-3 text-[13px] leading-[19px] text-[var(--ink-2)]">
            This rates the photo, not the answer. Even a clean photo can be off by several centimetres.
          </p>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3">
          {[["Head to toe", "1,400 px"], ["Head height", "193 px"]].map(([k, v]) => (
            <div key={k} className="rounded-2xl border border-[var(--line)] bg-[var(--paper)]/60 p-4 inset">
              <p className="text-[13px] text-[var(--ink-2)]">{k}</p>
              <p className="mt-1 text-[15px] font-semibold" style={{ fontVariantNumeric: "tabular-nums" }}>{v}</p>
            </div>
          ))}
        </div>
        <p className="mt-5 text-[14px] leading-[21px] text-[var(--ink-2)]">
          You measure 7.3 heads tall. Classical art uses 7.5.
        </p>
      </motion.section>

      <div className="mt-auto flex flex-col gap-4 pt-6">
        <Button variant="primary" icon={RotateCcw} onClick={onAgain} className="w-full">Measure again</Button>
        <Privacy />
      </div>
    </>
  );
}

/* ---------- App ---------- */
export default function App() {
  const [screen, setScreen] = useState("home");
  const [mode, setMode] = useState("proportions");

  return (
    <div className="paper min-h-dvh">
      <style>{CSS}</style>
      <main className="relative mx-auto flex min-h-dvh w-full max-w-[430px] flex-col px-5 pb-8 pt-6">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div key={screen} className="flex flex-1 flex-col" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.22 }}>
            {screen === "home" && <HomeScreen mode={mode} setMode={setMode} onNext={() => setScreen("capture")} />}
            {screen === "capture" && <CaptureScreen mode={mode} onBack={() => setScreen("home")} onDone={() => setScreen("result")} />}
            {screen === "result" && <ResultScreen onAgain={() => setScreen("capture")} />}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
