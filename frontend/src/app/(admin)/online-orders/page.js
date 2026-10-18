"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { BellRing, Clock, Globe, Leaf, MapPin, Search, Store } from "lucide-react";
import { PageHeader } from "@/components/ui/layout";
import { Badge, ErrorState } from "@/components/ui/feedback";
import { Input } from "@/components/ui/form";
import { cn } from "@/lib/cn";
import { formatDateTime, formatMoney, formatTime } from "@/lib/format";
import { useDebouncedValue } from "@/lib/hooks";
import { BOARD_COLUMNS, fulfilmentApi, fulfilmentKeys } from "@/features/online-orders/api";

const ACTIVE = BOARD_COLUMNS.map((c) => c.status);

function minutesSince(iso) {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60_000));
}

function OrderCard({ order }) {
  const p = order.progress || { resolved: 0, total: order.lines, pendingSubstitutions: 0 };
  const pct = p.total ? Math.round((p.resolved / p.total) * 100) : 0;
  const waiting = minutesSince(order.placedAt);
  const late = order.status === "PLACED" && waiting >= 15;
  return (
    <Link
      href={`/online-orders/${order.id}`}
      className={cn(
        "block rounded-lg border bg-white p-3 shadow-sm transition hover:shadow-md",
        order.arrivedAt ? "border-emerald-400 ring-2 ring-emerald-200" : late ? "border-amber-300" : "border-slate-200",
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-sm font-semibold text-slate-900">#{order.orderNumber}</span>
        <span className="text-sm font-semibold tabular text-slate-900">Rs {formatMoney(order.payableTotal)}</span>
      </div>
      <p className="mt-0.5 truncate text-sm text-slate-600">{order.customerName}</p>
      <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
        <span className="text-slate-500">
          {order.lines} item{order.lines === 1 ? "" : "s"}
        </span>
        {order.pickupAfter ? (
          <Badge color="blue">
            <Clock className="mr-1 h-3 w-3" aria-hidden="true" /> from {formatTime(order.pickupAfter)}
          </Badge>
        ) : (
          <Badge color="gray">ASAP</Badge>
        )}
        {p.pendingSubstitutions > 0 && <Badge color="secondary">Waiting on customer</Badge>}
        {late && <Badge color="amber">{waiting} min waiting</Badge>}
        {order.arrivedAt && (
          <Badge color="green">
            <MapPin className="mr-1 h-3 w-3" aria-hidden="true" /> Here since {formatTime(order.arrivedAt)}
          </Badge>
        )}
      </div>
      {order.status === "PACKING" && (
        <div className="mt-2">
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
          </div>
          <p className="mt-1 text-[11px] text-slate-500">
            {p.resolved}/{p.total} picked
          </p>
        </div>
      )}
    </Link>
  );
}

export default function OnlineOrdersBoard() {
  const [q, setQ] = useState("");
  const search = useDebouncedValue(q.trim(), 300);
  const params = { status: ACTIVE.join(","), q: search || undefined, size: 100 };
  const board = useQuery({
    queryKey: fulfilmentKeys.board(params),
    queryFn: () => fulfilmentApi.board(params),
    refetchInterval: 10_000,
  });
  const summary = useQuery({ queryKey: fulfilmentKeys.summary, queryFn: fulfilmentApi.summary, refetchInterval: 10_000 });

  // Alert staff when a new order lands or a customer checks in, without needing to watch the screen.
  const seen = useRef(null);
  useEffect(() => {
    const orders = board.data?.content;
    if (!orders) return;
    const placed = new Set(orders.filter((o) => o.status === "PLACED").map((o) => o.id));
    const arrived = new Set(orders.filter((o) => o.arrivedAt).map((o) => o.id));
    if (seen.current && !search) {
      const fresh = [...placed].filter((id) => !seen.current.placed.has(id));
      const here = [...arrived].filter((id) => !seen.current.arrived.has(id));
      if (fresh.length) toast.info(`${fresh.length} new online order${fresh.length > 1 ? "s" : ""}`, { icon: <Globe className="h-4 w-4" /> });
      for (const id of here) {
        const o = orders.find((x) => x.id === id);
        toast.success(`${o.customerName} is at the store for #${o.orderNumber}`, { duration: 10_000 });
      }
    }
    seen.current = { placed, arrived };
  }, [board.data, search]);

  const byStatus = Object.fromEntries(BOARD_COLUMNS.map((c) => [c.status, []]));
  for (const o of board.data?.content || []) byStatus[o.status]?.push(o);
  // Customers already at the store first, then oldest first.
  for (const list of Object.values(byStatus)) {
    list.sort((a, b) => (b.arrivedAt ? 1 : 0) - (a.arrivedAt ? 1 : 0) || new Date(a.placedAt) - new Date(b.placedAt));
  }

  return (
    <div>
      <PageHeader
        title="Online orders"
        description="Click & collect: confirm, pack item by item, offer swaps, and hand over at the counter."
        actions={
          <Link href="/rescue-deals" className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-800 hover:bg-slate-50">
            <Leaf className="h-4 w-4 text-secondary-600" aria-hidden="true" /> Rescue deals
          </Link>
        }
      />

      {summary.data?.arrivedWaiting > 0 && (
        <div role="status" className="mb-4 flex items-center gap-3 rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
          <BellRing className="h-5 w-5 animate-bounce" aria-hidden="true" />
          <strong>{summary.data.arrivedWaiting}</strong> customer{summary.data.arrivedWaiting > 1 ? "s are" : " is"} at the store
          waiting for their order.
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative w-full max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <Input className="pl-9" placeholder="Order number, customer or phone" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        {summary.data && (
          <p className="text-sm text-slate-500">
            <Store className="mr-1 inline h-4 w-4" aria-hidden="true" />
            {Object.entries(summary.data.counts)
              .filter(([s]) => ACTIVE.includes(s))
              .reduce((n, [, c]) => n + c, 0)}{" "}
            open orders · updated {board.dataUpdatedAt ? formatDateTime(new Date(board.dataUpdatedAt).toISOString()) : "—"}
          </p>
        )}
      </div>

      {board.isError ? (
        <ErrorState error={board.error} onRetry={board.refetch} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {BOARD_COLUMNS.map((col) => (
            <section key={col.status} className="flex min-h-64 flex-col rounded-lg bg-slate-100/80 p-3" aria-label={col.label}>
              <header className="mb-3 flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">{col.label}</h2>
                  <p className="text-xs text-slate-500">{col.hint}</p>
                </div>
                <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-slate-700">
                  {byStatus[col.status].length}
                </span>
              </header>
              <div className="space-y-2">
                {board.isLoading
                  ? Array.from({ length: 2 }).map((_, i) => <div key={i} className="h-24 animate-pulse rounded-lg bg-white" />)
                  : byStatus[col.status].map((o) => <OrderCard key={o.id} order={o} />)}
                {!board.isLoading && byStatus[col.status].length === 0 && (
                  <p className="py-6 text-center text-xs text-slate-400">Nothing here</p>
                )}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
