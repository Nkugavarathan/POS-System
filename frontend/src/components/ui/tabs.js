"use client";

import { cn } from "@/lib/cn";

/** Simple accessible tab bar. `tabs` = [{ id, label, count? }]. */
export function Tabs({ tabs, value, onChange, className }) {
  return (
    <div role="tablist" className={cn("mb-4 flex gap-1 overflow-x-auto border-b border-slate-200", className)}>
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          type="button"
          aria-selected={value === t.id}
          onClick={() => onChange(t.id)}
          className={cn(
            "-mb-px flex items-center gap-2 whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium",
            value === t.id ? "border-primary text-primary-700" : "border-transparent text-slate-500 hover:text-slate-800",
          )}
        >
          {t.label}
          {t.count > 0 && (
            <span className="rounded-full bg-secondary-100 px-1.5 text-xs font-semibold text-secondary-800">{t.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
