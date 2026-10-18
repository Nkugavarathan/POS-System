"use client";

import { Suspense, use, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, CircleAlert, MapPin, PartyPopper, RefreshCcw } from "lucide-react";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/lib/api-error";
import { formatDateTime, formatMoney, formatTime } from "@/lib/format";
import { compare } from "@/lib/money";
import { shopApi, shopKeys, STATUS_LABEL } from "@/features/shop/api";
import { useShopMe } from "@/features/shop/hooks";
import { ItemRow, PickupPass, ProgressRing, StatusStepper, SubstitutionCard } from "@/features/shop/components/order-tracker";
import { ConfirmDialog } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/feedback";

const LIVE = new Set(["PLACED", "CONFIRMED", "PACKING", "READY_FOR_PICKUP"]);

function headline(order) {
  const p = order.progress;
  switch (order.status) {
    case "PLACED":
      return ["We’ve got your order", "The store will confirm it shortly. You’ll get a notification at every step."];
    case "CONFIRMED":
      return ["Confirmed — packing starts soon", "We’ll show each item here as it goes into your bag."];
    case "PACKING":
      return p.pendingSubstitutions > 0
        ? ["Your packer is waiting for you", "Something was out of stock. Approve or refuse the swap below."]
        : [`Packing now — ${p.resolved} of ${p.total} done`, "This page updates by itself. No need to refresh."];
    case "READY_FOR_PICKUP":
      return ["Your order is ready!", "Come over any time before the hold ends. Tap “I’m here” when you arrive."];
    case "COLLECTED":
      return ["Collected — enjoy!", "Thanks for shopping with us. Your receipt and points are on your account."];
    case "CANCELLED":
      return ["This order was cancelled", order.cancelReason || "Nothing was charged."];
    case "EXPIRED":
      return ["This order expired", "It wasn’t collected in time, so the items went back on the shelf. Nothing was charged."];
    default:
      return [STATUS_LABEL[order.status], ""];
  }
}

function OrderView({ number }) {
  const params = useSearchParams();
  const justPlaced = params.get("placed") === "1";
  const queryClient = useQueryClient();
  const me = useShopMe();
  const [confirmCancel, setConfirmCancel] = useState(false);

  const order = useQuery({
    queryKey: shopKeys.order(number),
    queryFn: () => shopApi.order(number),
    enabled: Boolean(me.data),
    // Live tracking: poll quickly while it is being packed, slower otherwise; stop when done.
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      if (!s || !LIVE.has(s)) return false;
      return s === "PACKING" ? 4000 : 10_000;
    },
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });

  const update = (data) => {
    queryClient.setQueryData(shopKeys.order(number), data);
    queryClient.invalidateQueries({ queryKey: shopKeys.orders });
    queryClient.invalidateQueries({ queryKey: shopKeys.unread });
  };
  const decide = useMutation({
    mutationFn: ({ itemId, accept }) => shopApi.decide(number, itemId, accept),
    onSuccess: (data, v) => {
      update(data);
      toast.success(v.accept ? "Swap accepted — thanks!" : "No problem, we’ll leave it out.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const arrived = useMutation({
    mutationFn: () => shopApi.arrived(number),
    onSuccess: (data) => {
      update(data);
      toast.success("The counter knows you’re here.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: () => shopApi.cancel(number),
    onSuccess: (data) => {
      update(data);
      setConfirmCancel(false);
      toast.success("Order cancelled.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (me.isLoading) return <div className="mx-auto max-w-5xl px-4 py-10" />;
  if (!me.data) {
    return (
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <p className="font-semibold text-slate-800">Sign in to see this order.</p>
        <Link href={`/shop/sign-in?next=/shop/orders/${number}`} className="mt-3 inline-block font-semibold text-primary-700 hover:underline">
          Sign in
        </Link>
      </div>
    );
  }
  if (order.isError) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <ErrorState error={order.error} onRetry={order.refetch} />
      </div>
    );
  }
  const o = order.data;
  if (!o) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-10">
        <div className="h-10 w-1/3 animate-pulse rounded bg-slate-100" />
        <div className="h-40 animate-pulse rounded-3xl bg-slate-100" />
      </div>
    );
  }

  const [title, sub] = headline(o);
  const proposals = o.lines.filter((l) => l.pickStatus === "SUBSTITUTE_PROPOSED" && l.substitute);
  const ended = o.status === "CANCELLED" || o.status === "EXPIRED";
  const packing = o.status === "PACKING";
  const finalKnown = o.finalTotal != null;
  const lower = finalKnown && compare(o.finalTotal, o.grandTotal) < 0;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <Link href="/shop/orders" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> My orders
      </Link>

      {justPlaced && o.status === "PLACED" && (
        <div className="mt-4 flex items-center gap-3 rounded-3xl bg-primary-50 p-4 text-primary-900 ring-1 ring-primary-200">
          <PartyPopper className="h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
          <p className="text-sm">
            <strong>Order placed.</strong> Keep this page open to watch it being packed — or close it, we&rsquo;ll notify you.
          </p>
        </div>
      )}

      <header className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-slate-500">
            Order <span className="font-mono text-slate-800">#{o.orderNumber}</span> · placed {formatDateTime(o.placedAt)}
          </p>
          <h1 className={cn("mt-1 text-3xl font-extrabold tracking-tight", ended ? "text-red-700" : "text-slate-900")}>{title}</h1>
          <p className="mt-1 text-slate-600">{sub}</p>
        </div>
        {LIVE.has(o.status) && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
            <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" aria-hidden="true" /> Live
          </span>
        )}
      </header>

      {!ended && (
        <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-5">
          <StatusStepper order={o} />
        </div>
      )}
      {ended && (
        <div role="status" className="mt-6 flex items-start gap-3 rounded-3xl border border-red-200 bg-red-50 p-4 text-sm text-red-900">
          <CircleAlert className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <span>
            {STATUS_LABEL[o.status]} {o.cancelledAt ? `on ${formatDateTime(o.cancelledAt)}` : ""}. {o.cancelReason || ""}
          </span>
        </div>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          {proposals.map((line) => (
            <SubstitutionCard
              key={line.id}
              line={line}
              pending={decide.isPending}
              onDecide={(accept) => decide.mutate({ itemId: line.id, accept })}
            />
          ))}

          {o.status === "READY_FOR_PICKUP" && (
            <PickupPass order={o} onArrived={() => arrived.mutate()} arriving={arrived.isPending} />
          )}

          <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white">
            <div className="flex items-center gap-5 border-b border-slate-100 p-5">
              <ProgressRing resolved={o.progress.resolved} total={o.progress.total} size={96} />
              <div>
                <h2 className="font-bold text-slate-900">Your bag</h2>
                <p className="text-sm text-slate-500">
                  {o.status === "PLACED" || o.status === "CONFIRMED"
                    ? "Items light up here as they are packed."
                    : `${o.progress.resolved} of ${o.progress.total} items sorted`}
                  {o.progress.pendingSubstitutions > 0 && (
                    <span className="ml-1 font-semibold text-secondary-700">
                      · {o.progress.pendingSubstitutions} waiting for you
                    </span>
                  )}
                </p>
                {packing && (
                  <p className="mt-1 inline-flex items-center gap-1 text-xs text-slate-400">
                    <RefreshCcw className="h-3 w-3 animate-spin [animation-duration:3s]" aria-hidden="true" /> updating live
                  </p>
                )}
              </div>
            </div>
            <ul className="divide-y divide-slate-100">
              {o.lines.map((l) => (
                <ItemRow key={l.id} line={l} packing={packing} />
              ))}
            </ul>
          </section>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-40 lg:self-start">
          <div className="rounded-3xl border border-slate-200 bg-white p-5">
            <h2 className="font-bold text-slate-900">To pay at the counter</h2>
            <p className="mt-2 text-3xl font-extrabold tabular text-slate-900">Rs {formatMoney(o.payableTotal)}</p>
            {lower && (
              <p className="mt-1 text-sm text-emerald-700">
                Down from Rs {formatMoney(o.grandTotal)} after swaps and missing items.
              </p>
            )}
            {!finalKnown && !ended && (
              <p className="mt-1 text-xs text-slate-500">Estimate — the final amount is fixed when packing is done and can only go down.</p>
            )}
            <dl className="mt-4 space-y-1.5 text-sm text-slate-600">
              <div className="flex justify-between">
                <dt>Items</dt>
                <dd className="tabular">Rs {formatMoney(o.subtotal)}</dd>
              </div>
              {compare(o.discountTotal, "0") > 0 && (
                <div className="flex justify-between text-red-600">
                  <dt>Savings</dt>
                  <dd className="tabular">− Rs {formatMoney(o.discountTotal)}</dd>
                </div>
              )}
            </dl>
          </div>

          <div className="rounded-3xl border border-slate-200 bg-white p-5 text-sm">
            <p className="flex items-center gap-1.5 font-bold text-slate-900">
              <MapPin className="h-4 w-4 text-primary" aria-hidden="true" /> {o.store.name}
            </p>
            <p className="mt-1 text-slate-500">{[o.store.addressLine1, o.store.city].filter(Boolean).join(", ")}</p>
            {o.store.phone && <p className="text-slate-500">{o.store.phone}</p>}
            {o.pickupAfter && (
              <p className="mt-3 text-slate-700">
                Pickup from <strong>{formatDateTime(o.pickupAfter)}</strong>
              </p>
            )}
            {o.customerNote && <p className="mt-3 rounded-xl bg-slate-50 p-3 text-slate-600">&ldquo;{o.customerNote}&rdquo;</p>}
          </div>

          {o.events?.length > 0 && (
            <div className="rounded-3xl border border-slate-200 bg-white p-5">
              <h2 className="font-bold text-slate-900">Updates</h2>
              <ol className="mt-3 space-y-3 border-l-2 border-slate-100 pl-4">
                {[...o.events].reverse().map((e, i) => (
                  <li key={`${e.at}-${i}`} className="relative">
                    <span className="absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full bg-primary" aria-hidden="true" />
                    <p className="text-sm text-slate-800">{e.message}</p>
                    <p className="text-xs text-slate-400">{formatTime(e.at)}</p>
                  </li>
                ))}
              </ol>
            </div>
          )}

          {o.canCancel && (
            <button
              type="button"
              onClick={() => setConfirmCancel(true)}
              className="w-full rounded-full py-2 text-sm font-medium text-slate-500 hover:bg-red-50 hover:text-red-700"
            >
              Cancel this order
            </button>
          )}
        </aside>
      </div>

      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title="Cancel this order?"
        description="Anything already packed goes back on the shelf. Nothing has been charged."
        confirmLabel="Cancel order"
        cancelLabel="Keep it"
        loading={cancel.isPending}
        onConfirm={() => cancel.mutate()}
      />
    </div>
  );
}

export default function OrderPage({ params }) {
  const { number } = use(params);
  return (
    <Suspense>
      <OrderView number={decodeURIComponent(number)} />
    </Suspense>
  );
}
