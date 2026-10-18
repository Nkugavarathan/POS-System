"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LogOut, Menu, Store, X } from "lucide-react";
import { NAV_SECTIONS } from "@/lib/navigation";
import { hasAny, hasPermission } from "@/lib/permissions";
import { fulfilmentApi, fulfilmentKeys } from "@/features/online-orders/api";
import { cn } from "@/lib/cn";
import { useLogout } from "@/features/auth/hooks";
import { useSessionStore } from "@/stores/session-store";
import { Button } from "@/components/ui/button";

function StoreSwitcher({ user }) {
  const queryClient = useQueryClient();
  const activeStoreId = useSessionStore((s) => s.activeStoreId);
  const setActiveStore = useSessionStore((s) => s.setActiveStore);
  const stores = user.stores || [];
  if (stores.length <= 1) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-600">
        <Store className="h-4 w-4" aria-hidden="true" />
        <span className="truncate">{stores[0]?.name}</span>
      </div>
    );
  }
  return (
    <label className="flex items-center gap-2 text-sm text-slate-600">
      <Store className="h-4 w-4" aria-hidden="true" />
      <span className="sr-only">Active store</span>
      <select
        className="h-8 rounded-md border border-slate-300 bg-white px-2 text-sm"
        value={activeStoreId ?? ""}
        onChange={(e) => {
          setActiveStore(Number(e.target.value));
          // Everything cached belongs to the previous store.
          queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== "auth" });
        }}
      >
        {stores.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}
          </option>
        ))}
      </select>
    </label>
  );
}

/** New online orders + customers waiting at the store, refreshed in the background. */
function OnlineBadge({ user }) {
  const summary = useQuery({
    queryKey: fulfilmentKeys.summary,
    queryFn: fulfilmentApi.summary,
    enabled: hasPermission(user, "ONLINE_ORDER_MANAGE"),
    refetchInterval: 20_000,
  });
  const fresh = summary.data?.counts?.PLACED ?? 0;
  const waiting = summary.data?.arrivedWaiting ?? 0;
  if (!fresh && !waiting) return null;
  return (
    <span className="ml-auto flex gap-1">
      {waiting > 0 && (
        <span className="rounded-full bg-emerald-600 px-1.5 text-xs font-semibold text-white" title="Customers at the store">
          {waiting}
        </span>
      )}
      {fresh > 0 && (
        <span className="rounded-full bg-secondary px-1.5 text-xs font-semibold text-secondary-foreground" title="New orders">
          {fresh}
        </span>
      )}
    </span>
  );
}

function Sidebar({ user, onNavigate }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Main" className="flex-1 overflow-y-auto px-3 py-4">
      {NAV_SECTIONS.map((section) => {
        const items = section.items.filter((item) => hasAny(user, item.anyOf));
        if (!items.length) return null;
        return (
          <div key={section.label} className="mb-5">
            <p className="px-3 text-xs font-semibold uppercase tracking-wider text-slate-400">{section.label}</p>
            <ul className="mt-2 space-y-0.5">
              {items.map(({ href, label, icon: Icon, badge }) => {
                const active = pathname === href || pathname.startsWith(`${href}/`);
                return (
                  <li key={href}>
                    <Link
                      href={href}
                      onClick={onNavigate}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium",
                        active ? "bg-primary-50 text-primary-800" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                      )}
                    >
                      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                      {label}
                      {badge === "online" && <OnlineBadge user={user} />}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </nav>
  );
}

export function AppShell({ user, children }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const logout = useLogout();

  const brand = (
    <div className="flex h-14 items-center gap-2 border-b border-slate-200 px-5">
      <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-sm font-bold text-primary-foreground">P</div>
      <span className="font-semibold text-slate-900">Supermarket POS</span>
    </div>
  );

  return (
    <div className="flex h-screen overflow-hidden">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">
        {brand}
        <Sidebar user={user} />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setMobileOpen(false)} />
          <aside className="relative flex h-full w-72 flex-col bg-white shadow-xl">
            <div className="flex items-center justify-between pr-2">
              {brand}
              <Button variant="ghost" size="icon" aria-label="Close navigation" onClick={() => setMobileOpen(false)}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <Sidebar user={user} onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 lg:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
              <Menu className="h-5 w-5" />
            </Button>
            <StoreSwitcher user={user} />
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-slate-900">{user.fullName}</p>
              <p className="text-xs text-slate-500">{user.roles.join(", ").replaceAll("_", " ").toLowerCase()}</p>
            </div>
            <Button variant="outline" size="sm" onClick={() => logout.mutate()} loading={logout.isPending}>
              <LogOut className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto px-4 py-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
