"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, ArrowRight, Repeat, ShoppingBasket, Trash2, Ban } from "lucide-react";
import { cn } from "@/lib/cn";
import { formatMoney } from "@/lib/format";
import { useDebouncedValue, useMounted } from "@/lib/hooks";
import { add, compare } from "@/lib/money";
import { useShopCart } from "@/features/shop/cart-store";
import { useActiveStore, useCartQuote } from "@/features/shop/hooks";
import { ProductArt } from "@/features/shop/components/product-art";
import { QuantityStepper } from "@/features/shop/components/quantity-stepper";
import { BasketSummary } from "@/features/shop/components/basket-summary";
import { ErrorState } from "@/components/ui/feedback";

function PreferenceToggle({ item }) {
  const setPreference = useShopCart((s) => s.setPreference);
  const options = [
    { value: "SUBSTITUTE", label: "Offer me a swap", icon: Repeat },
    { value: "SKIP", label: "Skip it", icon: Ban },
  ];
  return (
    <fieldset className="mt-2">
      <legend className="text-[11px] font-medium uppercase tracking-wide text-slate-400">If it&rsquo;s out of stock</legend>
      <div className="mt-1 inline-flex rounded-full bg-slate-100 p-0.5">
        {options.map((o) => (
          <label
            key={o.value}
            className={cn(
              "flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium",
              item.preference === o.value ? "bg-white text-slate-900 shadow-sm" : "text-slate-500",
            )}
          >
            <input
              type="radio"
              name={`pref-${item.productId}`}
              value={o.value}
              checked={item.preference === o.value}
              onChange={() => setPreference(item.productId, o.value)}
              className="sr-only"
            />
            <o.icon className="h-3.5 w-3.5" aria-hidden="true" /> {o.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

export default function CartPage() {
  const mounted = useMounted();
  const router = useRouter();
  const items = useShopCart((s) => s.items);
  const remove = useShopCart((s) => s.remove);
  const clear = useShopCart((s) => s.clear);
  const { storeId, store } = useActiveStore();
  const debounced = useDebouncedValue(items, 350);
  const quote = useCartQuote(storeId, mounted ? debounced : []);

  if (!mounted) return <div className="mx-auto max-w-6xl px-4 py-10" />;

  if (items.length === 0) {
    return (
      <div className="mx-auto flex max-w-xl flex-col items-center px-4 py-20 text-center">
        <span className="flex h-20 w-20 items-center justify-center rounded-full bg-primary-50">
          <ShoppingBasket className="h-10 w-10 text-primary" aria-hidden="true" />
        </span>
        <h1 className="mt-5 text-2xl font-extrabold text-slate-900">Your basket is empty</h1>
        <p className="mt-2 text-slate-500">Start with today&rsquo;s rescue deals — good food, lower prices.</p>
        <div className="mt-6 flex gap-3">
          <Link href="/shop/rescue" className="inline-flex h-11 items-center rounded-full bg-secondary px-5 text-sm font-semibold text-secondary-foreground">
            Rescue deals
          </Link>
          <Link href="/shop/browse" className="inline-flex h-11 items-center rounded-full border border-slate-300 bg-white px-5 text-sm font-semibold text-slate-800">
            Browse products
          </Link>
        </div>
      </div>
    );
  }

  const lines = new Map((quote.data?.lines || []).map((l) => [l.productId, l]));
  const unavailable = new Set(quote.data?.unavailableProductIds || []);
  const stale = quote.isFetching || debounced !== items;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900">Your basket</h1>
          <p className="text-sm text-slate-500">
            {items.length} item{items.length === 1 ? "" : "s"} · collecting from {store?.name || "…"}
          </p>
        </div>
        <button type="button" onClick={clear} className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-red-600">
          <Trash2 className="h-4 w-4" aria-hidden="true" /> Empty basket
        </button>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-3">
          {unavailable.size > 0 && (
            <div role="alert" className="flex items-start gap-2 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              Some items aren&rsquo;t available in that quantity at this store. Lower the quantity or remove them to continue.
            </div>
          )}
          {quote.isError && <ErrorState error={quote.error} onRetry={quote.refetch} />}
          <ul className="divide-y divide-slate-100 overflow-hidden rounded-3xl border border-slate-200 bg-white">
            {items.map((item) => {
              const line = lines.get(item.productId);
              const out = unavailable.has(item.productId);
              return (
                <li key={item.productId} className={cn("flex gap-4 p-4", out && "bg-amber-50/50")}>
                  <Link href={`/shop/products/${item.productId}`} className="h-20 w-20 shrink-0 overflow-hidden rounded-2xl">
                    <ProductArt name={item.name} iconClassName="h-8 w-8" />
                  </Link>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <Link href={`/shop/products/${item.productId}`} className="font-medium text-slate-900 hover:text-primary-700">
                          {item.name}
                        </Link>
                        {line?.promotionName && (
                          <p className="text-xs font-semibold text-red-600">{line.promotionName}</p>
                        )}
                        {out && <p className="text-xs font-semibold text-amber-700">Not enough in stock at this store</p>}
                      </div>
                      <div className="text-right">
                        {line ? (
                          <>
                            <p className={cn("font-bold tabular text-slate-900", stale && "opacity-50")}>Rs {formatMoney(line.lineTotal)}</p>
                            {compare(line.promoDiscount, "0") > 0 && (
                              <p className="text-xs text-slate-400 line-through tabular">
                                Rs {formatMoney(add(line.lineTotal, line.promoDiscount))}
                              </p>
                            )}
                          </>
                        ) : (
                          <div className="h-5 w-16 animate-pulse rounded bg-slate-100" />
                        )}
                      </div>
                    </div>
                    <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
                      <PreferenceToggle item={item} />
                      <div className="flex items-center gap-2">
                        <QuantityStepper item={item} />
                        <button
                          type="button"
                          onClick={() => remove(item.productId)}
                          className="flex h-9 w-9 items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-red-600"
                          aria-label={`Remove ${item.name}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="lg:sticky lg:top-40 lg:self-start">
          <BasketSummary quote={quote.data}>
            <button
              type="button"
              disabled={!quote.data || unavailable.size > 0 || stale}
              onClick={() => router.push("/shop/checkout")}
              className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-primary text-sm font-semibold text-primary-foreground shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Choose pickup time <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </button>
          </BasketSummary>
        </div>
      </div>
    </div>
  );
}
