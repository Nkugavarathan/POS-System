"use client";

import { Minus, Plus, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import { formatQuantity } from "@/lib/format";
import { addQuantity, stepFor, subtractQuantity, useShopCart } from "../cart-store";

const UNIT = { KG: "kg", G: "g", L: "L", ML: "ml" };

export function unitLabel(uom) {
  return UNIT[uom] || "";
}

/** −/+ control bound to one basket line. Removing the last unit removes the line. */
export function QuantityStepper({ item, size = "md", className }) {
  const setQuantity = useShopCart((s) => s.setQuantity);
  const step = stepFor(item.fractional);
  const next = subtractQuantity(item.quantity, step);
  const big = size === "lg";
  return (
    <div
      className={cn(
        "inline-flex items-center rounded-full border border-primary-200 bg-white shadow-sm",
        big ? "h-11" : "h-9",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setQuantity(item.productId, next)}
        className={cn("flex items-center justify-center rounded-full text-primary-700 hover:bg-primary-50", big ? "h-11 w-11" : "h-9 w-9")}
        aria-label={next === "0" ? `Remove ${item.name}` : `One less ${item.name}`}
      >
        {next === "0" ? <Trash2 className="h-4 w-4" /> : <Minus className="h-4 w-4" />}
      </button>
      <span className="min-w-10 text-center text-sm font-semibold tabular text-slate-900" aria-live="polite">
        {formatQuantity(item.quantity)}
        {unitLabel(item.unitOfMeasure) && <span className="ml-0.5 text-xs font-normal text-slate-500">{unitLabel(item.unitOfMeasure)}</span>}
      </span>
      <button
        type="button"
        onClick={() => setQuantity(item.productId, addQuantity(item.quantity, step))}
        className={cn("flex items-center justify-center rounded-full text-primary-700 hover:bg-primary-50", big ? "h-11 w-11" : "h-9 w-9")}
        aria-label={`One more ${item.name}`}
      >
        <Plus className="h-4 w-4" />
      </button>
    </div>
  );
}
