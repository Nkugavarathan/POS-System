"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronRight, Package } from "lucide-react";
import { formatDateTime, formatMoney } from "@/lib/format";
import { shopApi, shopKeys, STATUS_COLOR, STATUS_LABEL } from "@/features/shop/api";
import { useShopMe } from "@/features/shop/hooks";
import { Badge, ErrorState } from "@/components/ui/feedback";

const LIVE = new Set(["PLACED", "CONFIRMED", "PACKING", "READY_FOR_PICKUP"]);

export default function OrdersPage() {
  const me = useShopMe();
  const [page, setPage] = useState(0);
  const orders = useQuery({
    queryKey: [...shopKeys.orders, page],
    queryFn: () => shopApi.orders({ page, size: 10 }),
    enabled: Boolean(me.data),
    placeholderData: keepPreviousData,
    refetchInterval: 20_000,
  });

  if (me.isLoading) return <div className="mx-auto max-w-3xl px-4 py-10" />;
  if (!me.data) {
    return (
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <p className="font-semibold text-slate-800">Sign in to see your orders.</p>
        <Link href="/shop/sign-in?next=/shop/orders" className="mt-3 inline-block font-semibold text-primary-700 hover:underline">
          Sign in
        </Link>
      </div>
    );
  }

  const data = orders.data;
  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="text-2xl font-extrabold text-slate-900">My orders</h1>
      <div className="mt-6">
        {orders.isError ? (
          <ErrorState error={orders.error} onRetry={orders.refetch} />
        ) : !data ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-24 animate-pulse rounded-3xl bg-slate-100" />
            ))}
          </div>
        ) : data.content.length === 0 ? (
          <div className="flex flex-col items-center rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
            <Package className="h-10 w-10 text-slate-300" aria-hidden="true" />
            <p className="mt-3 font-semibold text-slate-800">No orders yet</p>
            <Link href="/shop/browse" className="mt-3 text-sm font-semibold text-primary-700 hover:underline">
              Start shopping
            </Link>
          </div>
        ) : (
          <ul className="space-y-3">
            {data.content.map((o) => {
              const pct = o.progress?.total ? Math.round((o.progress.resolved / o.progress.total) * 100) : 0;
              return (
                <li key={o.id}>
                  <Link
                    href={`/shop/orders/${o.orderNumber}`}
                    className="flex items-center gap-4 rounded-3xl border border-slate-200 bg-white p-4 transition hover:shadow-md"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-sm font-semibold text-slate-900">#{o.orderNumber}</span>
                        <Badge color={STATUS_COLOR[o.status]}>{STATUS_LABEL[o.status]}</Badge>
                        {o.progress?.pendingSubstitutions > 0 && <Badge color="secondary">Swap to review</Badge>}
                      </div>
                      <p className="mt-1 text-sm text-slate-500">
                        {formatDateTime(o.placedAt)} · {o.lines} item{o.lines === 1 ? "" : "s"} · {o.storeName}
                      </p>
                      {LIVE.has(o.status) && o.status !== "READY_FOR_PICKUP" && (
                        <div className="mt-2 h-1.5 max-w-xs overflow-hidden rounded-full bg-slate-100">
                          <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                        </div>
                      )}
                    </div>
                    <p className="text-right font-bold tabular text-slate-900">Rs {formatMoney(o.payableTotal)}</p>
                    <ChevronRight className="h-5 w-5 text-slate-300" aria-hidden="true" />
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
        {data && data.totalPages > 1 && (
          <div className="mt-6 flex justify-center gap-3">
            <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm disabled:opacity-40">
              Newer
            </button>
            <button
              type="button"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage(page + 1)}
              className="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm disabled:opacity-40"
            >
              Older
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
