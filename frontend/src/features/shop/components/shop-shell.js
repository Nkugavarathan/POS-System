"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Bell,
  Leaf,
  LogOut,
  MapPin,
  Package,
  Search,
  ShieldCheck,
  ShoppingBasket,
  Store,
  User,
  Zap,
} from "lucide-react";
import { cn } from "@/lib/cn";
import { useMounted } from "@/lib/hooks";
import { shopApi, shopKeys } from "../api";
import { SHOP_NAME, SHOP_TAGLINE } from "../brand";
import { useShopCart } from "../cart-store";
import { useActiveStore, useShopLogout, useShopMe, useUnreadCount } from "../hooks";

export function ShopLogo({ className }) {
  return (
    <Link href="/shop" className={cn("flex items-center gap-2", className)} aria-label={`${SHOP_NAME} home`}>
      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
        <ShoppingBasket className="h-5 w-5" aria-hidden="true" />
      </span>
      <span className="text-lg font-extrabold tracking-tight text-slate-900">
        {SHOP_NAME.slice(0, Math.ceil(SHOP_NAME.length / 2))}
        <span className="text-primary">{SHOP_NAME.slice(Math.ceil(SHOP_NAME.length / 2))}</span>
      </span>
    </Link>
  );
}

function StorePicker() {
  const { storeId, stores } = useActiveStore();
  const setStore = useShopCart((s) => s.setStore);
  const mounted = useMounted();
  return (
    <label className="flex min-w-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white py-1 pl-3 pr-1 text-sm">
      <MapPin className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
      <span className="sr-only">Pickup store</span>
      <select
        value={mounted && storeId ? storeId : ""}
        onChange={(e) => setStore(Number(e.target.value))}
        className="min-w-0 max-w-44 truncate bg-transparent py-0.5 pr-1 text-sm font-medium text-slate-800 focus:outline-none"
      >
        {!storeId && <option value="">Choose store</option>}
        {stores.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name.replace(/^.*? - /, "")}
          </option>
        ))}
      </select>
    </label>
  );
}

function SearchBox({ className }) {
  const router = useRouter();
  const [q, setQ] = useState("");
  return (
    <form
      role="search"
      className={cn("relative", className)}
      onSubmit={(e) => {
        e.preventDefault();
        router.push(`/shop/browse?q=${encodeURIComponent(q.trim())}`);
      }}
    >
      <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        type="search"
        placeholder="Search milk, rice, soap…"
        aria-label="Search products"
        className="h-11 w-full rounded-full border border-slate-200 bg-slate-50 pl-10 pr-4 text-sm placeholder:text-slate-400 focus:border-primary focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary/20"
      />
    </form>
  );
}

function BasketButton() {
  const mounted = useMounted();
  const count = useShopCart((s) => s.items.length);
  return (
    <Link
      href="/shop/cart"
      className="relative flex h-11 items-center gap-2 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-sm hover:bg-primary-700"
    >
      <ShoppingBasket className="h-5 w-5" aria-hidden="true" />
      <span className="hidden sm:inline">Basket</span>
      {mounted && count > 0 && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-secondary px-1 text-xs font-bold text-secondary-foreground">
          {count}
        </span>
      )}
      <span className="sr-only">{mounted ? `${count} items` : ""}</span>
    </Link>
  );
}

function AccountMenu() {
  const me = useShopMe();
  const unread = useUnreadCount(Boolean(me.data));
  const logout = useShopLogout();
  const router = useRouter();
  const [open, setOpen] = useState(false);

  if (me.isLoading) return <div className="h-11 w-24 animate-pulse rounded-full bg-slate-100" />;
  if (!me.data) {
    return (
      <Link href="/shop/sign-in" className="flex h-11 items-center gap-2 rounded-full px-3 text-sm font-medium text-slate-700 hover:bg-slate-100">
        <User className="h-5 w-5" aria-hidden="true" />
        <span className="hidden md:inline">Sign in</span>
      </Link>
    );
  }
  return (
    <div className="flex items-center gap-1">
      <Link
        href="/shop/notifications"
        className="relative flex h-11 w-11 items-center justify-center rounded-full text-slate-700 hover:bg-slate-100"
        aria-label={`Notifications${unread.data ? `, ${unread.data} unread` : ""}`}
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {unread.data > 0 && (
          <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread.data > 9 ? "9+" : unread.data}
          </span>
        )}
      </Link>
      <div className="relative">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          className="flex h-11 items-center gap-2 rounded-full px-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          aria-expanded={open}
          aria-haspopup="menu"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-sm font-bold text-primary-800">
            {me.data.fullName.slice(0, 1).toUpperCase()}
          </span>
          <span className="hidden max-w-28 truncate md:inline">{me.data.fullName.split(" ")[0]}</span>
        </button>
        {open && (
          <div role="menu" className="absolute right-0 z-30 mt-2 w-56 overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-xl">
            <div className="border-b border-slate-100 px-4 py-2.5">
              <p className="truncate text-sm font-semibold text-slate-900">{me.data.fullName}</p>
              <p className="text-xs text-slate-500">{me.data.points.toLocaleString()} points</p>
            </div>
            <MenuLink href="/shop/orders" icon={Package}>My orders</MenuLink>
            <MenuLink href="/shop/account" icon={User}>Account & loyalty</MenuLink>
            <button
              type="button"
              role="menuitem"
              onClick={() => logout.mutate(undefined, { onSettled: () => router.push("/shop") })}
              className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" /> Sign out
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function MenuLink({ href, icon: Icon, children }) {
  return (
    <Link role="menuitem" href={href} className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
      <Icon className="h-4 w-4" aria-hidden="true" /> {children}
    </Link>
  );
}

function CategoryBar() {
  const categories = useQuery({ queryKey: shopKeys.categories, queryFn: shopApi.categories, staleTime: 10 * 60_000 });
  const pathname = usePathname();
  return (
    <nav aria-label="Departments" className="border-t border-slate-100">
      <div className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 py-2 text-sm [scrollbar-width:none]">
        <Link
          href="/shop/rescue"
          className={cn(
            "flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 font-semibold",
            pathname === "/shop/rescue" ? "bg-secondary text-secondary-foreground" : "bg-secondary-50 text-secondary-800 hover:bg-secondary-100",
          )}
        >
          <Leaf className="h-4 w-4" aria-hidden="true" /> Rescue deals
        </Link>
        <Link href="/shop/browse" className="shrink-0 rounded-full px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100">
          All products
        </Link>
        {(categories.data || []).map((c) => (
          <Link
            key={c.id}
            href={`/shop/browse?category=${c.id}`}
            className="shrink-0 rounded-full px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100"
          >
            {c.name}
          </Link>
        ))}
      </div>
    </nav>
  );
}

export function ShopShell({ children }) {
  return (
    <div className="flex min-h-full flex-col bg-white">
      <div className="bg-primary-900 text-primary-50">
        <div className="mx-auto flex max-w-7xl items-center justify-center gap-6 px-4 py-1.5 text-xs sm:justify-between">
          <p className="flex items-center gap-1.5">
            <Zap className="h-3.5 w-3.5 text-secondary" aria-hidden="true" /> {SHOP_TAGLINE}
          </p>
          <p className="hidden items-center gap-1.5 sm:flex">
            <ShieldCheck className="h-3.5 w-3.5 text-secondary" aria-hidden="true" /> Substitutes never cost you more
          </p>
        </div>
      </div>
      <header className="sticky top-0 z-20 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3">
          <ShopLogo />
          <div className="hidden lg:block">
            <StorePicker />
          </div>
          <SearchBox className="hidden flex-1 md:block" />
          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <AccountMenu />
            <BasketButton />
          </div>
        </div>
        <div className="flex gap-2 px-4 pb-3 md:hidden">
          <SearchBox className="flex-1" />
        </div>
        <div className="px-4 pb-2 lg:hidden">
          <StorePicker />
        </div>
        <CategoryBar />
      </header>
      <main className="flex-1 bg-slate-50/60">{children}</main>
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 text-sm text-slate-600 sm:grid-cols-3">
          <div className="space-y-3">
            <ShopLogo />
            <p className="max-w-xs text-slate-500">
              Click &amp; collect from your neighbourhood store. Pay at the counter when you pick up — no card needed online.
            </p>
          </div>
          <div>
            <p className="font-semibold text-slate-900">How it works</p>
            <ol className="mt-3 space-y-1.5 text-slate-500">
              <li>1. Fill your basket and pick a time</li>
              <li>2. Watch it packed, item by item</li>
              <li>3. Approve any substitute (never pricier)</li>
              <li>4. Tap &ldquo;I&rsquo;m here&rdquo; and collect</li>
            </ol>
          </div>
          <div>
            <p className="font-semibold text-slate-900">Good to know</p>
            <ul className="mt-3 space-y-1.5 text-slate-500">
              <li className="flex items-center gap-2">
                <Leaf className="h-4 w-4 text-secondary-600" aria-hidden="true" /> Rescue deals cut food waste
              </li>
              <li className="flex items-center gap-2">
                <Store className="h-4 w-4 text-primary" aria-hidden="true" /> One loyalty card, online and in store
              </li>
              <li>
                <Link href="/login" className="text-slate-400 hover:text-slate-600">
                  Staff sign-in
                </Link>
              </li>
            </ul>
          </div>
        </div>
      </footer>
    </div>
  );
}
