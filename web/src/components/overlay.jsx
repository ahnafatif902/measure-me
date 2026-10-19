/* Overlay vocabulary, copied 1:1 from the reference app (measure-studio.tsx).
   Coordinates arrive in frame pixels (the SVG's viewBox is "0 0 frameW frameH"),
   so the drawing lands exactly on the photo at any size. */

export function Marker({ point, label }) {
  return (
    <g>
      <circle
        cx={point.x}
        cy={point.y}
        r={11}
        fill="var(--color-primary)"
        stroke="var(--color-surface)"
        strokeWidth={3}
      />
      <text
        x={point.x + 16}
        y={point.y + 4}
        fill="var(--color-surface)"
        stroke="var(--color-ink)"
        strokeWidth={3}
        paintOrder="stroke"
        fontSize={12}
        fontFamily="Outfit, sans-serif"
      >
        {label}
      </text>
    </g>
  );
}

export function MeasureRod({ crown, feet, chin, label }) {
  const x = (crown.x + feet.x) / 2;
  const midY = (crown.y + feet.y) / 2;
  const headX = chin ? chin.x + 22 : crown.x + 22;
  return (
    <g aria-hidden>
      <line x1={x} y1={crown.y} x2={x} y2={feet.y} stroke="var(--color-surface)" strokeWidth={6} strokeLinecap="round" />
      <line x1={x} y1={crown.y} x2={x} y2={feet.y} stroke="var(--color-primary)" strokeWidth={2} strokeLinecap="round" />
      <line x1={x - 12} y1={crown.y} x2={x + 12} y2={crown.y} stroke="var(--color-primary)" strokeWidth={2} />
      <line x1={x - 12} y1={feet.y} x2={x + 12} y2={feet.y} stroke="var(--color-primary)" strokeWidth={2} />
      {chin && (
        <>
          <line x1={headX} y1={crown.y} x2={headX} y2={chin.y} stroke="var(--color-surface)" strokeWidth={5} />
          <line x1={headX} y1={crown.y} x2={headX} y2={chin.y} stroke="var(--color-primary)" strokeWidth={2} />
          <line x1={headX - 8} y1={crown.y} x2={headX + 8} y2={crown.y} stroke="var(--color-primary)" strokeWidth={2} />
          <line x1={headX - 8} y1={chin.y} x2={headX + 8} y2={chin.y} stroke="var(--color-primary)" strokeWidth={2} />
        </>
      )}
      {label && (
        <text
          x={x + 16}
          y={midY}
          fill="var(--color-surface)"
          stroke="var(--color-ink)"
          strokeWidth={4}
          paintOrder="stroke"
          fontSize={13}
          fontFamily="Outfit, sans-serif"
        >
          {label}
        </text>
      )}
    </g>
  );
}
