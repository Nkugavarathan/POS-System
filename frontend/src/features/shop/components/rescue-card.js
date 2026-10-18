"use client";

import Link from "next/link";
import { Clock, Leaf } from "lucide-react";
import { formatDate, formatMoney, formatQuantity } from "@/lib/format";
import { centsToString, toCents } from "@/lib/money";
import { ProductArt } from "./product-art";
import { AddToBasket, Price } from "./product-card";

const WEIGHED = new Set(["KG", "G", "L", "ML"]);

export function daysLeftLabel(days) {
  if (days <= 0) return "Best before today";
  if (days === 1) return "Best before tomorrow";
  return `${days} days left`;
}

/** Turns a rescue deal into the product shape the basket understands. */
export function dealAsProduct(deal) {
  return {
    id: deal.productId,
    name: deal.name,
    brand: deal.brand,
    unitOfMeasure: deal.unitOfMeasure,
    fractional: WEIGHED.has(deal.unitOfMeasure),
    price: deal.price,
    offerPrice: deal.offerPrice,
    stockLevel: "IN_STOCK",
    rescue: true,
  };
}

/**
 * A near-expiry batch on automatic markdown. The freshness bar shows how close the date is
 * (full = several days, empty = today) so the shopper buys knowingly.
 */
export function RescueCard({ deal }) {
  const product = dealAsProduct(deal);
  const freshness = Math.max(8, Math.min(100, ((Number(deal.daysLeft) + 1) / 4) * 100));
  return (
    <article className="flex w-64 shrink-0 snap-start flex-col overflow-hidden rounded-2xl border border-secondary-200 bg-white shadow-sm">
      <Link href={`/shop/products/${deal.productId}`} className="relative block h-32">
        <ProductArt name={deal.name} imageUrl={deal.imageUrl} />
        <span className="absolute right-2 top-2 rounded-full bg-red-600 px-2.5 py-1 text-xs font-extrabold text-white shadow">
          −{formatQuantity(deal.percentOff)}%
        </span>
        <span className="absolute left-2 top-2 inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-0.5 text-[11px] font-bold text-secondary-foreground">
          <Leaf className="h-3 w-3" aria-hidden="true" /> Rescue
        </span>
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-3">
        <Link href={`/shop/products/${deal.productId}`} className="line-clamp-2 min-h-[2.5rem] text-sm font-medium text-slate-800 hover:text-primary-700">
          {deal.name}
        </Link>
        <div>
          <div className="flex items-center justify-between text-xs">
            <span className="inline-flex items-center gap-1 font-semibold text-secondary-800">
              <Clock className="h-3.5 w-3.5" aria-hidden="true" /> {daysLeftLabel(Number(deal.daysLeft))}
            </span>
            <span className="text-slate-500">{formatQuantity(deal.quantityLeft)} left</span>
          </div>
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-secondary-100" aria-hidden="true">
            <div className="h-full rounded-full bg-secondary" style={{ width: `${freshness}%` }} />
          </div>
          <p className="mt-1 text-[11px] text-slate-400">Use by {formatDate(deal.expiryDate)}</p>
        </div>
        <div className="mt-auto flex items-end justify-between gap-2">
          <Price price={deal.price} offerPrice={deal.offerPrice} uom={deal.unitOfMeasure} />
          <AddToBasket product={product} />
        </div>
      </div>
    </article>
  );
}

/** Largest per-unit saving across the deals (for the section banner), as display text. */
export function rescueSavings(deals) {
  let best = null;
  for (const d of deals) {
    const saving = toCents(d.price) - toCents(d.offerPrice);
    if (best === null || saving > best) best = saving;
  }
  return best === null ? null : formatMoney(centsToString(best));
}
