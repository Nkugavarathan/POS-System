"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  BellRing,
  CalendarClock,
  CheckCircle2,
  Hand,
  Leaf,
  PackageCheck,
  RefreshCcw,
  Repeat,
  ShieldCheck,
} from "lucide-react";
import { shopApi, shopKeys, STATUS_LABEL } from "@/features/shop/api";
import { useActiveStore, useShopMe } from "@/features/shop/hooks";
import { ProductCard, ProductGridSkeleton, Price, AddToBasket } from "@/features/shop/components/product-card";
import { RescueCard, rescueSavings } from "@/features/shop/components/rescue-card";
import { ProductArt } from "@/features/shop/components/product-art";

const ACTIVE = ["PLACED", "CONFIRMED", "PACKING", "READY_FOR_PICKUP"];

function LiveOrderCard({ order }) {
  const p = order.progress || { resolved: 0, total: order.lines, pendingSubstitutions: 0 };
  const pct = p.total ? Math.round((p.resolved / p.total) * 100) : 0;
  return (
    <Link
      href={`/shop/orders/${order.orderNumber}`}
      className="block rounded-3xl bg-white p-5 text-slate-900 shadow-2xl ring-1 ring-black/5 transition hover:-translate-y-0.5"
    >
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">Your order, live</p>
        <span className="rounded-full bg-primary-50 px-2.5 py-1 text-xs font-semibold text-primary-800">
          {STATUS_LABEL[order.status]}
        </span>
      </div>
      <p className="mt-2 text-lg font-bold">#{order.orderNumber}</p>
      <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-slate-100">
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${pct}%` }} />
      </div>
      <p className="mt-2 text-sm text-slate-600">
        {p.resolved} of {p.total} items packed
        {p.pendingSubstitutions > 0 && (
          <span className="ml-2 font-semibold text-secondary-700">· {p.pendingSubstitutions} substitute to review</span>
        )}
      </p>
      <p className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-primary-700">
        Track it <ArrowRight className="h-4 w-4" aria-hidden="true" />
      </p>
    </Link>
  );
}

/** Illustration of the packing tracker for visitors who have no order yet. */
function TrackerPreview() {
  const rows = [
    { name: "Fresh Milk 1L", state: "Packed", icon: CheckCircle2, tone: "text-emerald-600" },
    { name: "Sliced Bread 450g", state: "Swap offered", icon: RefreshCcw, tone: "text-secondary-700" },
    { name: "Red Rice 1kg", state: "Packing…", icon: PackageCheck, tone: "text-slate-400" },
  ];
  return (
    <div className="rounded-3xl bg-white p-5 text-slate-900 shadow-2xl ring-1 ring-black/5" aria-hidden="true">
      <p className="text-xs font-semibold uppercase tracking-wider text-primary">What you&rsquo;ll see</p>
      <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-slate-100">
        <div className="h-full w-2/3 rounded-full bg-primary" />
      </div>
      <ul className="mt-4 space-y-3">
        {rows.map((r) => (
          <li key={r.name} className="flex items-center gap-3">
            <div className="h-10 w-10 overflow-hidden rounded-xl">
              <ProductArt name={r.name} iconClassName="h-5 w-5" />
            </div>
            <span className="flex-1 text-sm font-medium">{r.name}</span>
            <span className={`inline-flex items-center gap-1 text-xs font-semibold ${r.tone}`}>
              <r.icon className="h-4 w-4" /> {r.state}
            </span>
          </li>
        ))}
      </ul>
      <div className="mt-4 rounded-2xl bg-secondary-50 p-3 text-xs text-secondary-900">
        <strong>Brown Bread 450g</strong> instead? Rs 150 - you pay the lower price.
      </div>
    </div>
  );
}

function Hero() {
  const me = useShopMe();
  const orders = useQuery({
    queryKey: [...shopKeys.orders, "hero"],
    queryFn: () => shopApi.orders({ size: 5 }),
    enabled: Boolean(me.data),
    refetchInterval: 20_000,
  });
  const live = orders.data?.content?.find((o) => ACTIVE.includes(o.status));
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-primary-800 via-primary-700 to-primary-600 text-white">
      <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-secondary/25 blur-3xl" />
      <div className="mx-auto grid max-w-7xl items-center gap-10 px-4 py-12 md:grid-cols-[1.2fr_1fr] md:py-16">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-primary-50 ring-1 ring-white/20">
            <Hand className="h-3.5 w-3.5 text-secondary" aria-hidden="true" /> Click &amp; collect, done differently
          </p>
          <h1 className="mt-4 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">
            Your basket, <span className="text-secondary">packed while you watch.</span>
          </h1>
          <p className="mt-4 max-w-xl text-base text-primary-50/90">
            See every item go into your bag in real time, approve any substitute from your phone, and skip the queue - tap
            &ldquo;I&rsquo;m here&rdquo; and we bring it to the counter. Pay when you collect.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <Link href="/shop/browse" className="inline-flex h-12 items-center gap-2 rounded-full bg-secondary px-6 font-semibold text-secondary-foreground shadow-lg hover:bg-secondary-600">
              Start shopping <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link href="/shop/rescue" className="inline-flex h-12 items-center gap-2 rounded-full bg-white/10 px-6 font-semibold text-white ring-1 ring-white/30 hover:bg-white/20">
              <Leaf className="h-4 w-4" aria-hidden="true" /> Today&rsquo;s rescue deals
            </Link>
          </div>
        </div>
        <div className="mx-auto w-full max-w-sm">{live ? <LiveOrderCard order={live} /> : <TrackerPreview />}</div>
      </div>
    </section>
  );
}

function RescueStrip({ storeId }) {
  const deals = useQuery({ queryKey: shopKeys.rescue(storeId), queryFn: () => shopApi.rescueDeals(storeId), enabled: Boolean(storeId) });
  if (!deals.data?.length) return null;
  const best = rescueSavings(deals.data);
  return (
    <section className="mx-auto max-w-7xl px-4 pt-10">
      <div className="rounded-3xl bg-secondary-50 p-5 ring-1 ring-secondary-200 sm:p-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-secondary-800">
              <Leaf className="h-4 w-4" aria-hidden="true" /> Rescue deals · this store only
            </p>
            <h2 className="mt-1 text-2xl font-extrabold text-slate-900">Good food, close to its date</h2>
            <p className="mt-1 max-w-2xl text-sm text-slate-600">
              These batches are near their best-before date - still perfectly good. Prices drop automatically as the date
              gets closer{best ? `; save up to Rs ${best} an item` : ""}. Fewer bins, lower bills.
            </p>
          </div>
          <Link href="/shop/rescue" className="inline-flex items-center gap-1 text-sm font-semibold text-secondary-800 hover:underline">
            See all {deals.data.length} <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
        <div className="-mx-1 mt-5 flex snap-x gap-3 overflow-x-auto px-1 pb-2">
          {deals.data.map((d) => (
            <RescueCard key={d.productId} deal={d} />
          ))}
        </div>
      </div>
    </section>
  );
}

function RunningLow({ storeId }) {
  const me = useShopMe();
  const items = useQuery({
    queryKey: shopKeys.buyAgain(storeId),
    queryFn: () => shopApi.buyAgain(storeId),
    enabled: Boolean(me.data && storeId),
  });
  if (!me.data) {
    return (
      <section className="mx-auto max-w-7xl px-4 pt-10">
        <div className="flex flex-col items-start gap-4 rounded-3xl border border-dashed border-primary-300 bg-primary-50/60 p-6 sm:flex-row sm:items-center">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
            <Repeat className="h-6 w-6" aria-hidden="true" />
          </span>
          <div className="flex-1">
            <p className="font-bold text-slate-900">Already shop with us in store?</p>
            <p className="text-sm text-slate-600">
              Connect your loyalty card and we&rsquo;ll learn your rhythm from your till receipts - then remind you before you
              run out of milk, rice or soap.
            </p>
          </div>
          <Link href="/shop/register" className="inline-flex h-11 items-center rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground hover:bg-primary-700">
            Create account
          </Link>
        </div>
      </section>
    );
  }
  if (!items.data?.length) return null;
  return (
    <section className="mx-auto max-w-7xl px-4 pt-10">
      <div className="flex items-end justify-between">
        <div>
          <p className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-primary">
            <CalendarClock className="h-4 w-4" aria-hidden="true" /> Running low?
          </p>
          <h2 className="mt-1 text-2xl font-extrabold text-slate-900">Your usuals, right on time</h2>
          <p className="mt-1 text-sm text-slate-600">Predicted from how often you buy them - online and at the till.</p>
        </div>
      </div>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.data.slice(0, 6).map((i) => (
          <div key={i.productId} className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-3">
            <Link href={`/shop/products/${i.productId}`} className="h-16 w-16 shrink-0 overflow-hidden rounded-xl">
              <ProductArt name={i.name} iconClassName="h-7 w-7" />
            </Link>
            <div className="min-w-0 flex-1">
              <Link href={`/shop/products/${i.productId}`} className="block truncate text-sm font-medium text-slate-800 hover:text-primary-700">
                {i.name}
              </Link>
              <p className={`text-xs font-semibold ${i.dueSoon ? "text-secondary-700" : "text-slate-500"}`}>
                {i.dueInDays === null
                  ? `Bought ${i.purchases}×`
                  : i.dueInDays <= 0
                    ? "Probably running out now"
                    : `Likely due in ${i.dueInDays} day${i.dueInDays === 1 ? "" : "s"}`}
              </p>
              <Price price={i.price} offerPrice={i.offerPrice} uom={i.unitOfMeasure} className="text-sm" />
            </div>
            <AddToBasket
              product={{
                id: i.productId,
                name: i.name,
                unitOfMeasure: i.unitOfMeasure,
                fractional: ["KG", "G", "L", "ML"].includes(i.unitOfMeasure),
                price: i.price,
                offerPrice: i.offerPrice,
                stockLevel: i.stockLevel,
              }}
            />
          </div>
        ))}
      </div>
    </section>
  );
}

function Promises() {
  const items = [
    { icon: PackageCheck, title: "Live packing", body: "Follow your bag item by item, like a parcel - but in minutes." },
    { icon: ShieldCheck, title: "Never pay more", body: "Out of something? We offer a swap; you approve it and pay the lower price." },
    { icon: BellRing, title: "I'm here", body: "Check in from the car park and we have your bags at the counter." },
    { icon: Repeat, title: "One loyalty card", body: "Points and history shared between the till and the app." },
  ];
  return (
    <section className="mx-auto max-w-7xl px-4 pt-10">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {items.map((i) => (
          <div key={i.title} className="rounded-2xl border border-slate-200 bg-white p-4">
            <i.icon className="h-6 w-6 text-primary" aria-hidden="true" />
            <p className="mt-3 font-semibold text-slate-900">{i.title}</p>
            <p className="mt-1 text-sm text-slate-500">{i.body}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function Departments() {
  const categories = useQuery({ queryKey: shopKeys.categories, queryFn: shopApi.categories, staleTime: 10 * 60_000 });
  if (!categories.data?.length) return null;
  return (
    <section className="mx-auto max-w-7xl px-4 pt-10">
      <h2 className="text-2xl font-extrabold text-slate-900">Shop by department</h2>
      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {categories.data.map((c) => (
          <Link
            key={c.id}
            href={`/shop/browse?category=${c.id}`}
            className="group overflow-hidden rounded-2xl border border-slate-200 bg-white transition hover:shadow-md"
          >
            <div className="h-24">
              <ProductArt name={c.name} category={(c.children || []).map((x) => x.name).join(" ")} />
            </div>
            <p className="px-3 py-2.5 text-sm font-semibold text-slate-800 group-hover:text-primary-700">{c.name}</p>
          </Link>
        ))}
      </div>
    </section>
  );
}

function Popular({ storeId }) {
  const products = useQuery({
    queryKey: shopKeys.products({ storeId, size: 8, home: true }),
    queryFn: () => shopApi.products({ storeId, size: 8 }),
    enabled: Boolean(storeId),
  });
  return (
    <section className="mx-auto max-w-7xl px-4 py-10">
      <div className="flex items-end justify-between">
        <h2 className="text-2xl font-extrabold text-slate-900">In store today</h2>
        <Link href="/shop/browse" className="inline-flex items-center gap-1 text-sm font-semibold text-primary-700 hover:underline">
          Browse all <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>
      <div className="mt-5">
        {products.isLoading || !storeId ? (
          <ProductGridSkeleton />
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {products.data?.content.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

export default function ShopHome() {
  const { storeId, store } = useActiveStore();
  return (
    <>
      <Hero />
      {store && (
        <div className="border-b border-slate-200 bg-white">
          <p className="mx-auto max-w-7xl px-4 py-2.5 text-sm text-slate-600">
            Collecting from <strong className="text-slate-900">{store.name}</strong>
            {store.addressLine1 ? ` · ${store.addressLine1}, ${store.city}` : ""} · prices, stock and rescue deals shown
            are for this store · pay at the counter when you collect
          </p>
        </div>
      )}
      <RescueStrip storeId={storeId} />
      <RunningLow storeId={storeId} />
      <Promises />
      <Departments />
      <Popular storeId={storeId} />
    </>
  );
}
