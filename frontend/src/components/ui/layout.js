"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";
import { Button } from "./button";

export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ className, children, ...props }) {
  return (
    <div className={cn("rounded-lg border border-slate-200 bg-white shadow-sm", className)} {...props}>
      {children}
    </div>
  );
}

export function CardHeader({ title, description, actions, className }) {
  return (
    <div className={cn("flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4", className)}>
      <div>
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
      </div>
      {actions}
    </div>
  );
}

/** Table shell with consistent styling; pass <thead>/<tbody> as children. */
export function Table({ className, children }) {
  return (
    <div className="overflow-x-auto">
      <table className={cn("min-w-full divide-y divide-slate-200 text-sm", className)}>{children}</table>
    </div>
  );
}

export function Th({ className, children, align = "left", ...props }) {
  return (
    <th
      scope="col"
      className={cn(
        "whitespace-nowrap bg-slate-50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500",
        align === "right" ? "text-right" : align === "center" ? "text-center" : "text-left",
        className,
      )}
      {...props}
    >
      {children}
    </th>
  );
}

export function Td({ className, children, align = "left", ...props }) {
  return (
    <td
      className={cn(
        "px-4 py-2.5 text-slate-700",
        align === "right" ? "text-right tabular" : align === "center" ? "text-center" : "text-left",
        className,
      )}
      {...props}
    >
      {children}
    </td>
  );
}

/** Server-driven pagination for the backend's PageResponse envelope. */
export function Pagination({ page, totalPages, totalElements, size, onPageChange }) {
  if (totalElements === undefined) return null;
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <nav className="flex items-center justify-between border-t border-slate-200 px-4 py-3" aria-label="Pagination">
      <p className="text-sm text-slate-500">
        {totalElements === 0 ? "No results" : `${from}–${to} of ${totalElements.toLocaleString()}`}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
        >
          Next
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </nav>
  );
}
