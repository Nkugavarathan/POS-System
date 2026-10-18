"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Clock, Leaf, MapPin, PackageCheck, ShieldCheck } from "lucide-react";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useActiveStore } from "@/features/shop/hooks";
import { ProductArt } from "@/features/shop/components/product-art";
import { AddToBasket, Price, ProductCard, StockPill } from "@/features/shop/components/product-card";
import { daysLeftLabel } from "@/features/shop/components/rescue-card";
import { ErrorState } from "@/components/ui/feedback";

export default function ProductPage({ params }) {
  const { id } = use(params);
  const productId = Number(id);
  const { storeId, store } = useActiveStore();
  const product = useQuery({
    queryKey: shopKeys.product(productId, storeId),
    queryFn: () => shopApi.product(productId, storeId),
    enabled: Boolean(storeId),
  });
  const p = product.data;
  const related = useQuery({
    queryKey: shopKeys.products({ storeId, categoryId: p?.categoryId, size: 5, related: true }),
    queryFn: () => shopApi.products({ storeId, categoryId: p.categoryId, size: 5 }),
    enabled: Boolean(storeId && p?.categoryId),
  });

  if (product.isError) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <ErrorState error={product.error} onRetry={product.refetch} />
      </div>
    );
  }
  if (!p) {
    return (
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 md:grid-cols-2">
        <div className="aspect-square animate-pulse rounded-3xl bg-slate-100" />
        <div className="space-y-4">
          <div className="h-8 w-3/4 animate-pulse rounded bg-slate-100" />
          <div className="h-6 w-1/3 animate-pulse rounded bg-slate-100" />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <Link href={p.categoryId ? `/shop/browse?category=${p.categoryId}` : "/shop/browse"} className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {p.categoryName || "All products"}
      </Link>
      <div className="mt-4 grid gap-10 md:grid-cols-2">
        <div className="relative aspect-square overflow-hidden rounded-3xl border border-slate-200 bg-white">
          <ProductArt name={p.name} category={p.categoryName} imageUrl={p.imageUrl} iconClassName="h-24 w-24" />
          {p.rescue && (
            <span className="absolute left-4 top-4 inline-flex items-center gap-1 rounded-full bg-secondary px-3 py-1 text-sm font-bold text-secondary-foreground shadow">
              <Leaf className="h-4 w-4" aria-hidden="true" /> Rescue deal
            </span>
          )}
        </div>
        <div>
          {p.brand && <p className="text-sm font-semibold uppercase tracking-wide text-slate-400">{p.brand}</p>}
          <h1 className="mt-1 text-3xl font-extrabold tracking-tight text-slate-900">{p.name}</h1>
          <div className="mt-4 flex items-center gap-3">
            <Price price={p.price} offerPrice={p.offerPrice} uom={p.unitOfMeasure} className="text-2xl" />
            {p.offerLabel && !p.rescue && (
              <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-bold text-red-700">{p.offerLabel}</span>
            )}
          </div>
          {p.rescue && (
            <div className="mt-4 rounded-2xl bg-secondary-50 p-4 text-sm text-secondary-900 ring-1 ring-secondary-200">
              <p className="flex items-center gap-1.5 font-bold">
                <Clock className="h-4 w-4" aria-hidden="true" /> {daysLeftLabel(Number(p.rescueDaysLeft ?? 0))}
              </p>
              <p className="mt-1">
                Part of this store&rsquo;s stock is close to its date, so it&rsquo;s marked down. When we pack your order we pick
                from that batch first — you get the deal and help cut waste.
              </p>
            </div>
          )}
          <div className="mt-4">
            <StockPill level={p.stockLevel} />
          </div>
          <div className="mt-6">
            <AddToBasket product={p} size="lg" />
          </div>
          {p.description && <p className="mt-6 whitespace-pre-line text-slate-600">{p.description}</p>}
          <ul className="mt-8 space-y-3 text-sm text-slate-600">
            <li className="flex items-start gap-3">
              <MapPin className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
              <span>
                Price and availability at <strong className="text-slate-900">{store?.name}</strong>
              </span>
            </li>
            <li className="flex items-start gap-3">
              <PackageCheck className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
              <span>Watch it picked from the shelf in your live order tracker</span>
            </li>
            <li className="flex items-start gap-3">
              <ShieldCheck className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
              <span>If it runs out, we ask before swapping — and a swap never costs more than this</span>
            </li>
          </ul>
        </div>
      </div>

      {related.data?.content?.filter((r) => r.id !== p.id).length > 0 && (
        <section className="mt-14">
          <h2 className="text-xl font-bold text-slate-900">More in {p.categoryName}</h2>
          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {related.data.content
              .filter((r) => r.id !== p.id)
              .slice(0, 4)
              .map((r) => (
                <ProductCard key={r.id} product={r} />
              ))}
          </div>
        </section>
      )}
    </div>
  );
}
