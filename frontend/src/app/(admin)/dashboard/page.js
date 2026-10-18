"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowDownRight, ArrowUpRight, CalendarClock, Globe, Leaf, MapPin, Receipt, ShoppingBasket, Undo2, Wallet } from "lucide-react";
import { useMe } from "@/features/auth/hooks";
import { reportsApi } from "@/features/reports/api";
import { DailyRevenueChart, HourlyChart } from "@/features/reports/charts";
import { NAV_SECTIONS } from "@/lib/navigation";
import { hasAny, hasPermission } from "@/lib/permissions";
import { PageHeader, Card, CardHeader } from "@/components/ui/layout";
import { ErrorState, Skeleton } from "@/components/ui/feedback";
import { cn } from "@/lib/cn";
import { formatMoney, formatQuantity } from "@/lib/format";
import { centsToString, toCents } from "@/lib/money";

function Kpi({ icon: Icon, label, value, sub, tone }) {
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-500">{label}</p>
        <Icon className="h-4 w-4 text-slate-400" aria-hidden="true" />
      </div>
      <p className="mt-2 text-2xl font-bold tabular text-slate-900">{value}</p>
      {sub && <p className={cn("mt-1 text-xs", tone || "text-slate-500")}>{sub}</p>}
    </Card>
  );
}

function versus(today, yesterday) {
  const t = toCents(today);
  const y = toCents(yesterday);
  if (y === 0n) return { text: "no sales by this time yesterday", tone: "text-slate-500", up: null };
  const diff = t - y;
  // Percentage for display: integer maths on cents, one decimal.
  const pct = Number((diff * 1000n) / y) / 10;
  return {
    text: `${pct >= 0 ? "+" : ""}${pct.toFixed(1)}% vs yesterday (Rs ${formatMoney(centsToString(y))})`,
    tone: pct >= 0 ? "text-emerald-700" : "text-red-600",
    up: pct >= 0,
  };
}

function OpsTile({ href, icon: Icon, label, value, alert }) {
  return (
    <Link
      href={href}
      className={cn(
        "flex items-center gap-3 rounded-lg border p-3 transition hover:shadow",
        alert ? "border-amber-300 bg-amber-50" : "border-slate-200 bg-white",
      )}
    >
      <span className={cn("flex h-9 w-9 items-center justify-center rounded-md", alert ? "bg-amber-100 text-amber-800" : "bg-primary-50 text-primary-700")}>
        <Icon className="h-5 w-5" aria-hidden="true" />
      </span>
      <span className="flex-1 text-sm text-slate-700">{label}</span>
      <span className="text-lg font-bold tabular text-slate-900">{value}</span>
    </Link>
  );
}

function Insights() {
  const d = useQuery({ queryKey: ["reports", "dashboard"], queryFn: reportsApi.dashboard, refetchInterval: 60_000 });
  if (d.isError) return <ErrorState error={d.error} onRetry={d.refetch} />;
  if (!d.data) {
    return (
      <div className="grid gap-4 md:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-28" />
        ))}
      </div>
    );
  }
  const x = d.data;
  const vs = versus(x.revenueToday, x.revenueYesterdaySameTime);
  const online = Object.values(x.onlineOrders || {}).reduce((a, b) => a + b, 0);

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={vs.up === false ? ArrowDownRight : ArrowUpRight} label="Sales today" value={`Rs ${formatMoney(x.revenueToday)}`} sub={vs.text} tone={vs.tone} />
        <Kpi icon={Receipt} label="Transactions" value={x.salesToday.toLocaleString()} sub="completed receipts" />
        <Kpi icon={ShoppingBasket} label="Average basket" value={`Rs ${formatMoney(x.averageBasketToday)}`} />
        <Kpi icon={Undo2} label="Refunds today" value={`Rs ${formatMoney(x.refundsToday)}`} />
      </div>

      <div className="grid gap-6 xl:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader title="Last 14 days" description="Sales and refunds per day" actions={<Link href="/reports" className="text-sm font-medium text-primary-700 hover:underline">Reports</Link>} />
          <div className="p-4">
            <DailyRevenueChart rows={x.last14Days} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Needs attention" />
          <div className="space-y-2 p-4">
            <OpsTile href="/online-orders" icon={Globe} label="Open online orders" value={online} alert={(x.onlineOrders?.PLACED ?? 0) > 0} />
            <OpsTile href="/online-orders" icon={MapPin} label="Customers waiting at store" value={x.onlineArrivedWaiting} alert={x.onlineArrivedWaiting > 0} />
            <OpsTile href="/reports?tab=stock" icon={AlertTriangle} label="Products at or below reorder level" value={x.lowStockCount} alert={x.lowStockCount > 0} />
            <OpsTile href="/reports?tab=stock" icon={CalendarClock} label="Batches expiring within 3 days" value={x.expiringBatches} alert={x.expiringBatches > 0} />
            <OpsTile href="/rescue-deals" icon={Leaf} label="Rescue deals running" value={x.rescueDeals} />
            <OpsTile href="/shifts" icon={Wallet} label="Open till shifts" value={x.openShifts} />
          </div>
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title="Today by hour" />
          <div className="p-4">
            <HourlyChart rows={x.hoursToday} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Top products · last 7 days" />
          {x.topProducts7d.length === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-slate-500">No sales yet.</p>
          ) : (
            <ol className="divide-y divide-slate-100">
              {x.topProducts7d.map((p, i) => (
                <li key={p.productId} className="flex items-center gap-3 px-5 py-2.5 text-sm">
                  <span className="w-5 text-center font-semibold text-slate-400">{i + 1}</span>
                  <Link href={`/products/${p.productId}`} className="flex-1 truncate font-medium text-slate-800 hover:text-primary-700">
                    {p.name}
                  </Link>
                  <span className="text-xs text-slate-500">{formatQuantity(p.quantity)} sold</span>
                  <span className="w-28 text-right font-semibold tabular">Rs {formatMoney(p.revenue)}</span>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { data: user } = useMe();
  const shortcuts = NAV_SECTIONS.flatMap((s) => s.items).filter(
    (i) => i.href !== "/dashboard" && i.href !== "/account" && hasAny(user, i.anyOf),
  );
  const insights = hasPermission(user, "DASHBOARD_VIEW") || hasPermission(user, "REPORT_VIEW");

  return (
    <div className="space-y-6">
      <PageHeader title={`Welcome, ${user?.fullName?.split(" ")[0] ?? ""}`} description="Today at your store" />
      {insights && <Insights />}
      <section aria-labelledby="shortcuts">
        <h2 id="shortcuts" className="mb-3 text-sm font-semibold text-slate-700">
          Go to
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {shortcuts.map(({ href, label, icon: Icon }) => (
            <Link key={href} href={href} className="group">
              <Card className="flex items-center gap-3 p-4 transition group-hover:border-primary-300 group-hover:shadow">
                <span className="flex h-9 w-9 items-center justify-center rounded-md bg-primary-50 text-primary-700">
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </span>
                <span className="text-sm font-medium text-slate-800">{label}</span>
              </Card>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
