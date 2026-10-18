"use client";

import { Ban, Check, CheckCircle2, Clock, Hourglass, PackageCheck, RefreshCcw, Store, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";
import { formatDateTime, formatMoney, formatQuantity, formatTime } from "@/lib/format";
import { compare } from "@/lib/money";
import { ORDER_STEPS } from "../api";
import { ProductArt } from "./product-art";

const STEP_INDEX = Object.fromEntries(ORDER_STEPS.map((s, i) => [s.status, i]));

/** Horizontal (desktop) / vertical (mobile) status stepper with the time each step happened. */
export function StatusStepper({ order }) {
  const current = STEP_INDEX[order.status] ?? -1;
  const at = {};
  for (const e of order.events || []) if (!at[e.status]) at[e.status] = e.at;
  at.PLACED = at.PLACED || order.placedAt;
  return (
    <ol className="grid gap-4 sm:grid-cols-5 sm:gap-0">
      {ORDER_STEPS.map((step, i) => {
        const done = i < current || order.status === "COLLECTED";
        const active = i === current && order.status !== "COLLECTED";
        return (
          <li key={step.status} className="relative flex items-center gap-3 sm:flex-col sm:items-center sm:text-center">
            {i > 0 && (
              <span
                className={cn(
                  "absolute right-1/2 top-4 hidden h-1 w-full rounded-full sm:block",
                  i <= current ? "bg-primary" : "bg-slate-200",
                )}
                aria-hidden="true"
              />
            )}
            <span
              className={cn(
                "relative z-10 flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-2 text-sm font-bold",
                done && "border-primary bg-primary text-primary-foreground",
                active && "border-primary bg-white text-primary ring-4 ring-primary/15",
                !done && !active && "border-slate-200 bg-white text-slate-400",
              )}
            >
              {done ? <Check className="h-4 w-4" aria-hidden="true" /> : i + 1}
              {active && <span className="absolute inset-0 animate-ping rounded-full border-2 border-primary/40" aria-hidden="true" />}
            </span>
            <span className="sm:mt-2">
              <span className={cn("block text-sm font-semibold", done || active ? "text-slate-900" : "text-slate-400")}>
                {step.label}
              </span>
              <span className="block text-xs text-slate-400">{at[step.status] ? formatTime(at[step.status]) : ""}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

/** Ring showing packed/total items. */
export function ProgressRing({ resolved, total, size = 132 }) {
  const stroke = 12;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = total ? resolved / total : 0;
  return (
    <div className="relative" style={{ width: size, height: size }} role="img" aria-label={`${resolved} of ${total} items packed`}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-slate-100" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - pct)}
          className="stroke-primary transition-[stroke-dashoffset] duration-700"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-3xl font-extrabold tabular text-slate-900">
          {resolved}
          <span className="text-lg text-slate-400">/{total}</span>
        </span>
        <span className="text-xs font-medium text-slate-500">packed</span>
      </div>
    </div>
  );
}

const PICK = {
  PENDING: { label: "Waiting to be picked", icon: Hourglass, tone: "text-slate-400", bg: "" },
  PICKED: { label: "Packed", icon: CheckCircle2, tone: "text-emerald-600", bg: "" },
  SUBSTITUTE_PROPOSED: { label: "Swap offered - your call", icon: RefreshCcw, tone: "text-secondary-700", bg: "bg-secondary-50/70" },
  SUBSTITUTED: { label: "Swapped", icon: RefreshCcw, tone: "text-primary-700", bg: "" },
  UNAVAILABLE: { label: "Not available - not charged", icon: Ban, tone: "text-slate-500", bg: "bg-slate-50" },
};

export function ItemRow({ line, packing }) {
  const meta = PICK[line.pickStatus] || PICK.PENDING;
  const Icon = meta.icon;
  const shortPick =
    line.pickStatus === "PICKED" && line.pickedQuantity && compare(line.pickedQuantity, line.quantity) !== 0;
  return (
    <li className={cn("flex items-center gap-3 px-4 py-3", meta.bg)}>
      <div className="h-12 w-12 shrink-0 overflow-hidden rounded-xl">
        <ProductArt name={line.name} iconClassName="h-6 w-6" />
      </div>
      <div className="min-w-0 flex-1">
        <p className={cn("truncate text-sm font-medium", line.pickStatus === "UNAVAILABLE" ? "text-slate-400 line-through" : "text-slate-900")}>
          {line.name}
        </p>
        <p className="text-xs text-slate-500">
          {formatQuantity(line.quantity)} × Rs {formatMoney(line.unitPrice)}
          {line.preference === "SKIP" && <span className="ml-2 text-slate-400">· no swaps</span>}
        </p>
        {line.substitute && (
          <p className="mt-0.5 text-xs font-medium text-primary-700">
            → {line.substitute.name} ({formatQuantity(line.substitute.quantity)})
          </p>
        )}
        {shortPick && <p className="mt-0.5 text-xs text-amber-700">Packed {formatQuantity(line.pickedQuantity)} - you pay for that</p>}
      </div>
      <div className="text-right">
        <p className={cn("inline-flex items-center gap-1 text-xs font-semibold", meta.tone)}>
          <Icon className={cn("h-4 w-4", packing && line.pickStatus === "PENDING" && "animate-pulse")} aria-hidden="true" />
          <span className="hidden sm:inline">{meta.label}</span>
        </p>
        <p className="text-sm font-semibold tabular text-slate-900">
          Rs {formatMoney(line.finalLineTotal ?? line.substitute?.lineTotal ?? line.lineTotal)}
        </p>
      </div>
    </li>
  );
}

export function SubstitutionCard({ line, onDecide, pending }) {
  const sub = line.substitute;
  const saving = compare(line.lineTotal, sub.lineTotal) > 0;
  return (
    <div className="rounded-3xl border-2 border-secondary bg-white p-5 shadow-lg shadow-secondary/10">
      <p className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-secondary-800">
        <RefreshCcw className="h-4 w-4" aria-hidden="true" /> Your packer needs a quick answer
      </p>
      <div className="mt-4 grid grid-cols-[1fr_auto_1fr] items-center gap-3">
        <div className="text-center">
          <div className="mx-auto h-16 w-16 overflow-hidden rounded-2xl opacity-60 grayscale">
            <ProductArt name={line.name} iconClassName="h-7 w-7" />
          </div>
          <p className="mt-2 text-sm font-medium text-slate-500 line-through">{line.name}</p>
          <p className="text-xs text-slate-400">Out of stock</p>
        </div>
        <span className="text-2xl text-slate-300" aria-hidden="true">→</span>
        <div className="text-center">
          <div className="mx-auto h-16 w-16 overflow-hidden rounded-2xl ring-2 ring-secondary">
            <ProductArt name={sub.name} iconClassName="h-7 w-7" />
          </div>
          <p className="mt-2 text-sm font-semibold text-slate-900">{sub.name}</p>
          <p className="text-xs text-slate-500">
            {formatQuantity(sub.quantity)} for Rs {formatMoney(sub.lineTotal)}
          </p>
        </div>
      </div>
      <p className="mt-4 rounded-xl bg-secondary-50 px-3 py-2 text-center text-xs font-medium text-secondary-900">
        {saving
          ? `You'd pay Rs ${formatMoney(sub.lineTotal)} instead of Rs ${formatMoney(line.lineTotal)}.`
          : `Same price as what you ordered (Rs ${formatMoney(line.lineTotal)}) - never more.`}
      </p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <button
          type="button"
          disabled={pending}
          onClick={() => onDecide(false)}
          className="flex h-11 items-center justify-center gap-1.5 rounded-full border border-slate-300 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          <XCircle className="h-4 w-4" aria-hidden="true" /> No thanks
        </button>
        <button
          type="button"
          disabled={pending}
          onClick={() => onDecide(true)}
          className="flex h-11 items-center justify-center gap-1.5 rounded-full bg-secondary text-sm font-semibold text-secondary-foreground hover:bg-secondary-600 disabled:opacity-50"
        >
          <Check className="h-4 w-4" aria-hidden="true" /> Accept swap
        </button>
      </div>
    </div>
  );
}

/** The 6-digit code shown at the counter, styled like a boarding pass. */
export function PickupPass({ order, onArrived, arriving }) {
  return (
    <div className="overflow-hidden rounded-3xl bg-gradient-to-br from-emerald-600 to-primary-700 text-white shadow-xl">
      <div className="p-6">
        <p className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-emerald-100">
          <PackageCheck className="h-4 w-4" aria-hidden="true" /> Ready for pickup
        </p>
        <p className="mt-2 text-sm text-emerald-50">Show this code at the counter</p>
        <p className="mt-1 font-mono text-5xl font-extrabold tracking-[0.3em]" aria-label={`Pickup code ${order.pickupCode.split("").join(" ")}`}>
          {order.pickupCode}
        </p>
        {order.collectBy && (
          <p className="mt-3 flex items-center gap-1.5 text-sm text-emerald-50">
            <Clock className="h-4 w-4" aria-hidden="true" /> Held for you until {formatDateTime(order.collectBy)}
          </p>
        )}
      </div>
      <div className="relative border-t border-dashed border-white/30 bg-black/10 p-5">
        <span className="absolute -left-3 -top-3 h-6 w-6 rounded-full bg-slate-50" aria-hidden="true" />
        <span className="absolute -right-3 -top-3 h-6 w-6 rounded-full bg-slate-50" aria-hidden="true" />
        {order.arrivedAt ? (
          <p className="flex items-start gap-2 text-sm">
            <Store className="mt-0.5 h-5 w-5 shrink-0 text-secondary" aria-hidden="true" />
            <span>
              <strong>We know you&rsquo;re here</strong> (since {formatTime(order.arrivedAt)}). The counter team is bringing
              your bags - go to any till and give your code.
            </span>
          </p>
        ) : (
          <button
            type="button"
            onClick={onArrived}
            disabled={!order.canCheckIn || arriving}
            className="flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-secondary text-base font-extrabold text-secondary-foreground shadow-lg hover:bg-secondary-400 disabled:opacity-60"
          >
            <Store className="h-5 w-5" aria-hidden="true" /> I&rsquo;m here
          </button>
        )}
      </div>
    </div>
  );
}
