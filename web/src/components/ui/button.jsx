import { cn } from "../../lib/cn";

/* Variant/size classes copied 1:1 from the reference app (ui/button.tsx). */
const BASE =
  "inline-flex items-center justify-center gap-2 rounded-full font-medium transition-colors duration-200 disabled:pointer-events-none disabled:opacity-45";

const VARIANTS = {
  primary: "bg-primary text-surface hover:bg-primary/90",
  secondary: "border border-ink/15 bg-surface text-ink hover:bg-bg",
  ghost: "bg-transparent text-ink hover:bg-ink/5",
};

const SIZES = {
  md: "min-h-11 px-4 text-sm",
  lg: "min-h-12 w-full px-5 text-base",
};

export function Button({ className, variant = "primary", size = "md", type = "button", ...props }) {
  return <button type={type} className={cn(BASE, VARIANTS[variant], SIZES[size], className)} {...props} />;
}
