"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Leaf, Recycle, Sparkles, TrendingDown } from "lucide-react";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useActiveStore } from "@/features/shop/hooks";
import { RescueCard } from "@/features/shop/components/rescue-card";
import { ErrorState } from "@/components/ui/feedback";

export default function RescuePage() {
  const { storeId, store } = useActiveStore();
  const deals = useQuery({ queryKey: shopKeys.rescue(storeId), queryFn: () => shopApi.rescueDeals(storeId), enabled: Boolean(storeId) });
  const groups = [
    { title: "Best before today or tomorrow", match: (d) => d.daysLeft <= 1 },
    { title: "A few more days", match: (d) => d.daysLeft > 1 },
  ];

  return (
    <div>
      <section className="bg-gradient-to-br from-secondary-100 via-secondary-50 to-white">
        <div className="mx-auto max-w-7xl px-4 py-10">
          <p className="inline-flex items-center gap-1.5 rounded-full bg-white px-3 py-1 text-xs font-bold uppercase tracking-wider text-secondary-800 ring-1 ring-secondary-200">
            <Leaf className="h-4 w-4" aria-hidden="true" /> Rescue deals {store ? `· ${store.name}` : ""}
          </p>
          <h1 className="mt-3 max-w-2xl text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Rescue good food before its date - and pay less for it.
          </h1>
          <p className="mt-3 max-w-2xl text-slate-600">
            When a batch on our shelves gets close to its best-before date, its price drops automatically - online and at the
            till. The closer the date, the bigger the discount. Each deal is for this store&rsquo;s stock only, so what you see
            is really there.
          </p>
          <div className="mt-6 grid max-w-3xl gap-3 sm:grid-cols-3">
            {[
              { icon: TrendingDown, title: "Auto markdown", body: "Discount grows as the date nears" },
              { icon: Sparkles, title: "Still great", body: "Every item is within its date" },
              { icon: Recycle, title: "Less waste", body: "Food eaten, not binned" },
            ].map((x) => (
              <div key={x.title} className="flex items-start gap-3 rounded-2xl bg-white/80 p-3 ring-1 ring-secondary-200">
                <x.icon className="mt-0.5 h-5 w-5 text-secondary-700" aria-hidden="true" />
                <div>
                  <p className="text-sm font-semibold text-slate-900">{x.title}</p>
                  <p className="text-xs text-slate-500">{x.body}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl px-4 py-8">
        {deals.isError ? (
          <ErrorState error={deals.error} onRetry={deals.refetch} />
        ) : deals.isLoading || !storeId ? (
          <div className="flex gap-3 overflow-hidden">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-80 w-64 shrink-0 animate-pulse rounded-2xl bg-slate-100" />
            ))}
          </div>
        ) : deals.data.length === 0 ? (
          <div className="flex flex-col items-center rounded-3xl border border-dashed border-secondary-300 bg-white px-6 py-16 text-center">
            <Leaf className="h-10 w-10 text-secondary-300" aria-hidden="true" />
            <p className="mt-3 font-semibold text-slate-800">Nothing to rescue right now</p>
            <p className="mt-1 max-w-sm text-sm text-slate-500">
              Our shelves are fresh at this store. Deals appear here automatically when a batch nears its date - check back
              later.
            </p>
            <Link href="/shop/browse" className="mt-4 text-sm font-semibold text-primary-700 hover:underline">
              Browse all products
            </Link>
          </div>
        ) : (
          groups.map((g) => {
            const list = deals.data.filter(g.match);
            if (list.length === 0) return null;
            return (
              <section key={g.title} className="mb-10">
                <h2 className="text-lg font-bold text-slate-900">
                  {g.title} <span className="text-sm font-normal text-slate-500">({list.length})</span>
                </h2>
                <div className="mt-4 flex flex-wrap gap-3">
                  {list.map((d) => (
                    <RescueCard key={d.productId} deal={d} />
                  ))}
                </div>
              </section>
            );
          })
        )}
      </div>
    </div>
  );
}
