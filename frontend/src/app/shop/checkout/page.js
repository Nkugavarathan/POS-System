"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, CalendarDays, Lock, MapPin, Zap } from "lucide-react";
import { cn } from "@/lib/cn";
import { errorMessage, isApiError } from "@/lib/api-error";
import { formatMoney } from "@/lib/format";
import { useMounted } from "@/lib/hooks";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useShopCart } from "@/features/shop/cart-store";
import { useActiveStore, useCartQuote, useShopMe } from "@/features/shop/hooks";
import { BasketSummary } from "@/features/shop/components/basket-summary";

const OPEN_HOUR = 8;
const CLOSE_HOUR = 20; // last window starts at 18:00

/** Two-hour collection windows for today and the next two days (browser local time). */
function buildSlots(now = new Date()) {
  const days = [];
  for (let d = 0; d < 3; d++) {
    const day = new Date(now);
    day.setDate(now.getDate() + d);
    day.setHours(0, 0, 0, 0);
    const slots = [];
    for (let h = OPEN_HOUR; h + 2 <= CLOSE_HOUR; h += 2) {
      const start = new Date(day);
      start.setHours(h);
      // Leave at least an hour to pack.
      if (start.getTime() - now.getTime() < 60 * 60_000) continue;
      const end = new Date(start);
      end.setHours(h + 2);
      slots.push({ iso: start.toISOString(), label: `${fmt(start)} – ${fmt(end)}` });
    }
    if (slots.length) {
      days.push({
        key: day.toDateString(),
        label: d === 0 ? "Today" : d === 1 ? "Tomorrow" : day.toLocaleDateString("en-LK", { weekday: "long" }),
        date: day.toLocaleDateString("en-LK", { day: "numeric", month: "short" }),
        slots,
      });
    }
  }
  return days;
}

function fmt(date) {
  return date.toLocaleTimeString("en-LK", { hour: "numeric", minute: "2-digit" });
}

export default function CheckoutPage() {
  const mounted = useMounted();
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useShopMe();
  const items = useShopCart((s) => s.items);
  const clear = useShopCart((s) => s.clear);
  const { storeId, store } = useActiveStore();
  const quote = useCartQuote(storeId, mounted ? items : []);
  const days = useMemo(() => (mounted ? buildSlots() : []), [mounted]);
  const [dayKey, setDayKey] = useState(null);
  const [slot, setSlot] = useState(null); // null = as soon as ready
  const [note, setNote] = useState("");
  const activeDay = days.find((d) => d.key === dayKey) || days[0];

  const place = useMutation({
    mutationFn: () =>
      shopApi.place({
        storeId,
        pickupAfter: slot,
        note: note.trim() || null,
        expectedTotal: quote.data.grandTotal,
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity, substitutionPreference: i.preference })),
      }),
    onSuccess: (order) => {
      clear();
      queryClient.invalidateQueries({ queryKey: ["shop"] });
      router.replace(`/shop/orders/${order.orderNumber}?placed=1`);
    },
    onError: (e) => {
      if (isApiError(e) && e.code === "QUOTE_MISMATCH") {
        toast.warning("Prices changed a moment ago. Please check the new total and place the order again.");
        quote.refetch();
      } else if (isApiError(e) && e.code === "INSUFFICIENT_STOCK") {
        toast.error("Some items just sold out. Review your basket.");
        router.push("/shop/cart");
      } else {
        toast.error(errorMessage(e));
      }
    },
  });

  if (!mounted || me.isLoading) return <div className="mx-auto max-w-6xl px-4 py-10" />;

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-xl px-4 py-20 text-center">
        <p className="text-lg font-semibold text-slate-800">Your basket is empty.</p>
        <Link href="/shop/browse" className="mt-3 inline-block font-semibold text-primary-700 hover:underline">
          Browse products
        </Link>
      </div>
    );
  }

  if (!me.data) {
    return (
      <div className="mx-auto max-w-md px-4 py-16">
        <div className="rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-50">
            <Lock className="h-7 w-7 text-primary" aria-hidden="true" />
          </span>
          <h1 className="mt-4 text-xl font-bold text-slate-900">Sign in to place your order</h1>
          <p className="mt-2 text-sm text-slate-500">
            We&rsquo;ll send packing updates and your pickup code to your account. Your basket is saved.
          </p>
          <div className="mt-6 flex flex-col gap-2">
            <Link href="/shop/sign-in?next=/shop/checkout" className="flex h-11 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
              Sign in
            </Link>
            <Link href="/shop/register?next=/shop/checkout" className="flex h-11 items-center justify-center rounded-full border border-slate-300 text-sm font-semibold text-slate-800">
              Create an account
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const blocked = !quote.data || quote.isFetching || (quote.data.unavailableProductIds || []).length > 0;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <Link href="/shop/cart" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to basket
      </Link>
      <h1 className="mt-3 text-2xl font-extrabold text-slate-900">Pickup details</h1>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-5">
          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <h2 className="flex items-center gap-2 font-bold text-slate-900">
              <MapPin className="h-5 w-5 text-primary" aria-hidden="true" /> Collect from
            </h2>
            <p className="mt-2 font-medium text-slate-800">{store?.name}</p>
            <p className="text-sm text-slate-500">
              {[store?.addressLine1, store?.city].filter(Boolean).join(", ")} {store?.phone ? `· ${store.phone}` : ""}
            </p>
            <p className="mt-2 text-xs text-slate-400">Change the store from the header - prices and stock will update.</p>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <h2 className="flex items-center gap-2 font-bold text-slate-900">
              <CalendarDays className="h-5 w-5 text-primary" aria-hidden="true" /> When will you come?
            </h2>
            <button
              type="button"
              onClick={() => setSlot(null)}
              className={cn(
                "mt-4 flex w-full items-center gap-3 rounded-2xl border p-4 text-left",
                slot === null ? "border-primary bg-primary-50 ring-2 ring-primary/20" : "border-slate-200 hover:border-slate-300",
              )}
            >
              <Zap className="h-5 w-5 text-secondary-600" aria-hidden="true" />
              <span>
                <span className="block font-semibold text-slate-900">As soon as it&rsquo;s ready</span>
                <span className="text-sm text-slate-500">We start packing right away and notify you when it&rsquo;s ready</span>
              </span>
            </button>
            <p className="mt-5 text-xs font-semibold uppercase tracking-wide text-slate-400">Or pick a window</p>
            <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
              {days.map((d) => (
                <button
                  key={d.key}
                  type="button"
                  onClick={() => setDayKey(d.key)}
                  className={cn(
                    "shrink-0 rounded-2xl border px-4 py-2 text-left",
                    activeDay?.key === d.key ? "border-slate-900 bg-slate-900 text-white" : "border-slate-200 text-slate-700",
                  )}
                >
                  <span className="block text-sm font-semibold">{d.label}</span>
                  <span className="text-xs opacity-70">{d.date}</span>
                </button>
              ))}
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
              {activeDay?.slots.map((s) => (
                <button
                  key={s.iso}
                  type="button"
                  onClick={() => setSlot(s.iso)}
                  className={cn(
                    "rounded-xl border px-3 py-2.5 text-sm font-medium",
                    slot === s.iso ? "border-primary bg-primary text-primary-foreground" : "border-slate-200 text-slate-700 hover:border-slate-300",
                  )}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <label htmlFor="note" className="font-bold text-slate-900">
              Note for the packer <span className="text-sm font-normal text-slate-400">(optional)</span>
            </label>
            <textarea
              id="note"
              value={note}
              onChange={(e) => setNote(e.target.value.slice(0, 500))}
              rows={3}
              placeholder="e.g. Ripe bananas please, no plastic bags"
              className="mt-2 block w-full rounded-2xl border border-slate-200 p-3 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </section>
        </div>

        <div className="lg:sticky lg:top-40 lg:self-start">
          <BasketSummary quote={quote.data} title={`${items.length} item${items.length === 1 ? "" : "s"}`}>
            <button
              type="button"
              disabled={blocked || place.isPending}
              onClick={() => place.mutate()}
              className="flex h-12 w-full items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {place.isPending ? "Placing order…" : `Place order · Rs ${quote.data ? formatMoney(quote.data.grandTotal) : "…"}`}
            </button>
            <p className="mt-3 text-center text-xs text-slate-500">
              Ordering as {me.data.fullName} · {me.data.email}
            </p>
          </BasketSummary>
        </div>
      </div>
    </div>
  );
}
