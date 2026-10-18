"use client";

import Link from "next/link";
import { Leaf, Plus } from "lucide-react";
import { cn } from "@/lib/cn";
import { formatMoney } from "@/lib/format";
import { useMounted } from "@/lib/hooks";
import { useShopCart } from "../cart-store";
import { ProductArt } from "./product-art";
import { QuantityStepper, unitLabel } from "./quantity-stepper";

export function StockPill({ level }) {
  if (level === "IN_STOCK") return null;
  return (
    <span
      className={cn(
        "rounded-full px-2 py-0.5 text-[11px] font-semibold",
        level === "OUT" ? "bg-slate-200 text-slate-600" : "bg-amber-100 text-amber-800",
      )}
    >
      {level === "OUT" ? "Out of stock" : "Only a few left"}
    </span>
  );
}

export function Price({ price, offerPrice, uom, className }) {
  const per = unitLabel(uom) ? ` / ${unitLabel(uom)}` : "";
  return (
    <div className={cn("flex items-baseline gap-2", className)}>
      <span className={cn("font-bold tabular", offerPrice ? "text-red-600" : "text-slate-900")}>
        Rs {formatMoney(offerPrice || price)}
        <span className="text-xs font-normal text-slate-500">{per}</span>
      </span>
      {offerPrice && <span className="text-xs text-slate-400 line-through tabular">Rs {formatMoney(price)}</span>}
    </div>
  );
}

/** Add-to-basket button that turns into a stepper once the product is in the basket. */
export function AddToBasket({ product, size = "md", className }) {
  const mounted = useMounted();
  const item = useShopCart((s) => s.items.find((i) => i.productId === product.id));
  const add = useShopCart((s) => s.add);
  if (product.stockLevel === "OUT") {
    return (
      <span className={cn("inline-flex h-9 items-center text-xs font-medium text-slate-400", className)}>Unavailable here</span>
    );
  }
  if (mounted && item) return <QuantityStepper item={item} size={size} className={className} />;
  return (
    <button
      type="button"
      onClick={() => add(product)}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-full bg-primary font-semibold text-primary-foreground shadow-sm hover:bg-primary-700",
        size === "lg" ? "h-11 px-6 text-sm" : "h-9 px-4 text-sm",
        className,
      )}
    >
      <Plus className="h-4 w-4" aria-hidden="true" /> Add
    </button>
  );
}

export function ProductCard({ product }) {
  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white transition hover:-translate-y-0.5 hover:shadow-lg">
      <Link href={`/shop/products/${product.id}`} className="relative block aspect-[4/3] overflow-hidden">
        <ProductArt name={product.name} category={product.categoryName} imageUrl={product.imageUrl} />
        <div className="absolute left-2 top-2 flex flex-col items-start gap-1">
          {product.rescue && (
            <span className="inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-0.5 text-[11px] font-bold text-secondary-foreground shadow">
              <Leaf className="h-3 w-3" aria-hidden="true" /> Rescue deal
            </span>
          )}
          {!product.rescue && product.offerLabel && (
            <span className="rounded-full bg-red-600 px-2 py-0.5 text-[11px] font-bold text-white shadow">{product.offerLabel}</span>
          )}
        </div>
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-3">
        <div className="min-h-[2.5rem]">
          {product.brand && <p className="text-[11px] font-medium uppercase tracking-wide text-slate-400">{product.brand}</p>}
          <Link href={`/shop/products/${product.id}`} className="line-clamp-2 text-sm font-medium text-slate-800 hover:text-primary-700">
            {product.name}
          </Link>
        </div>
        <StockPill level={product.stockLevel} />
        <div className="mt-auto flex items-end justify-between gap-2">
          <Price price={product.price} offerPrice={product.offerPrice} uom={product.unitOfMeasure} />
          <AddToBasket product={product} />
        </div>
      </div>
    </article>
  );
}

export function ProductGridSkeleton({ count = 8 }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
          <div className="aspect-[4/3] animate-pulse bg-slate-100" />
          <div className="space-y-2 p-3">
            <div className="h-4 w-3/4 animate-pulse rounded bg-slate-100" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-slate-100" />
          </div>
        </div>
      ))}
    </div>
  );
}
