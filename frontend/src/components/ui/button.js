"use client";

import { forwardRef } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

const VARIANTS = {
  primary: "bg-primary text-primary-foreground hover:bg-primary-700 disabled:bg-primary/50",
  secondary: "bg-secondary text-secondary-foreground hover:bg-secondary-600 disabled:bg-secondary/50",
  outline: "bg-white text-slate-800 border border-slate-300 hover:bg-slate-50 disabled:text-slate-400",
  danger: "bg-red-600 text-white hover:bg-red-700 disabled:bg-red-600/50",
  ghost: "text-slate-700 hover:bg-slate-100 disabled:text-slate-400",
  success: "bg-emerald-600 text-white hover:bg-emerald-700 disabled:bg-emerald-600/50",
  // For dark bars (POS header): light text with a subtle hover.
  inverse: "text-white/90 hover:bg-white/15 hover:text-white disabled:text-white/40",
};

const SIZES = {
  sm: "h-8 px-3 text-sm gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  lg: "h-12 px-5 text-base gap-2",
  xl: "h-16 px-6 text-lg gap-3",
  icon: "h-9 w-9 justify-center",
};

export const Button = forwardRef(function Button(
  { variant = "primary", size = "md", loading = false, disabled, className, children, type = "button", ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center justify-center rounded-md font-medium transition-colors",
        "disabled:cursor-not-allowed",
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
});
