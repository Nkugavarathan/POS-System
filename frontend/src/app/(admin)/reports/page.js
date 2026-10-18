"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/form";
import { Tabs } from "@/components/ui/tabs";
import { useMe } from "@/features/auth/hooks";
import { todayInput } from "@/features/backoffice/api";
import { downloadCsv, reportsApi } from "@/features/reports/api";
import { DailyRevenueChart } from "@/features/reports/charts";
import { errorMessage } from "@/lib/api-error";
import { hasAny, hasPermission } from "@/lib/permissions";
import { formatDate, formatMoney, formatQuantity } from "@/lib/format";

function CsvButton({ report, params, canExport }) {
  const [busy, setBusy] = useState(false);
  if (!canExport) return null;
  return (
    <Button
      variant="outline"
      size="sm"
      loading={busy}
      onClick={async () => {
        setBusy(true);
        try {
          await downloadCsv(report, params);
        } catch (e) {
          toast.error(errorMessage(e));
        } finally {
          setBusy(false);
        }
      }}
    >
      <Download className="h-4 w-4" aria-hidden="true" /> CSV
    </Button>
  );
}

function SalesTab({ range, canExport }) {
  const q = useQuery({ queryKey: ["reports", "sales", range], queryFn: () => reportsApi.sales(range), placeholderData: keepPreviousData });
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const s = q.data;
  if (!s) return <TableSkeleton cols={8} />;
  const t = s.totals;
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {[
          ["Gross sales", formatMoney(t.grossSales)],
          ["Refunds", formatMoney(t.refunds)],
          ["Net sales", formatMoney(t.netSales)],
          ["Transactions", t.salesCount.toLocaleString()],
          ["Average basket", formatMoney(s.averageBasket)],
        ].map(([l, v]) => (
          <Card key={l} className="p-4">
            <p className="text-sm text-slate-500">{l}</p>
            <p className="mt-1 text-xl font-bold tabular text-slate-900">{v}</p>
          </Card>
        ))}
      </div>
      <Card>
        <CardHeader title="By day" actions={<CsvButton report="sales" params={range} canExport={canExport} />} />
        <div className="p-4">
          <DailyRevenueChart rows={s.days} />
        </div>
        <Table>
          <thead>
            <tr>
              <Th>Day</Th>
              <Th align="right">Sales</Th>
              <Th align="right">Gross</Th>
              <Th align="right">Discounts</Th>
              <Th align="right">Tax</Th>
              <Th align="right">Refunds</Th>
              <Th align="right">Net</Th>
              {s.includesCost && <Th align="right">Gross profit</Th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {[...s.days].reverse().map((d) => (
              <tr key={d.day}>
                <Td>{formatDate(`${d.day}T12:00:00Z`)}</Td>
                <Td align="right">{d.salesCount}</Td>
                <Td align="right">{formatMoney(d.grossSales)}</Td>
                <Td align="right">{formatMoney(d.discounts)}</Td>
                <Td align="right">{formatMoney(d.tax)}</Td>
                <Td align="right">{formatMoney(d.refunds)}</Td>
                <Td align="right" className="font-semibold">
                  {formatMoney(d.netSales)}
                </Td>
                {s.includesCost && <Td align="right">{formatMoney(d.grossProfit)}</Td>}
              </tr>
            ))}
            <tr className="bg-slate-50 font-semibold">
              <Td>Total</Td>
              <Td align="right">{t.salesCount}</Td>
              <Td align="right">{formatMoney(t.grossSales)}</Td>
              <Td align="right">{formatMoney(t.discounts)}</Td>
              <Td align="right">{formatMoney(t.tax)}</Td>
              <Td align="right">{formatMoney(t.refunds)}</Td>
              <Td align="right">{formatMoney(t.netSales)}</Td>
              {s.includesCost && <Td align="right">{formatMoney(t.grossProfit)}</Td>}
            </tr>
          </tbody>
        </Table>
        {s.includesCost && <p className="px-5 py-2 text-xs text-slate-500">Gross profit = sales excluding tax − cost of goods sold (before returns).</p>}
      </Card>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Payment methods" />
          <Table>
            <tbody className="divide-y divide-slate-100">
              {s.tenders.map((x) => (
                <tr key={x.tenderType}>
                  <Td>{x.tenderType}</Td>
                  <Td align="right">{x.count}×</Td>
                  <Td align="right" className="font-semibold">
                    {formatMoney(x.amount)}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
        <Card>
          <CardHeader title="Channels" description="Till sales vs online click & collect" />
          <Table>
            <tbody className="divide-y divide-slate-100">
              {s.channels.map((x) => (
                <tr key={x.channel}>
                  <Td>{x.channel === "ONLINE" ? <Badge color="primary">Online pickup</Badge> : <Badge>In store</Badge>}</Td>
                  <Td align="right">{x.count}×</Td>
                  <Td align="right" className="font-semibold">
                    {formatMoney(x.amount)}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      </div>
    </div>
  );
}

function ProductsTab({ range, canExport }) {
  const top = useQuery({ queryKey: ["reports", "top", range], queryFn: () => reportsApi.topProducts({ ...range, limit: 30 }), placeholderData: keepPreviousData });
  const cats = useQuery({ queryKey: ["reports", "categories", range], queryFn: () => reportsApi.categories(range), placeholderData: keepPreviousData });
  return (
    <div className="grid gap-6 xl:grid-cols-[3fr_2fr]">
      <Card>
        <CardHeader title="Best sellers" description="Net of returns" actions={<CsvButton report="top-products" params={range} canExport={canExport} />} />
        {top.isError ? (
          <ErrorState error={top.error} onRetry={top.refetch} />
        ) : !top.data ? (
          <TableSkeleton cols={4} />
        ) : top.data.length === 0 ? (
          <EmptyState title="No sales in this period" />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>#</Th>
                <Th>Product</Th>
                <Th align="right">Qty</Th>
                <Th align="right">Receipts</Th>
                <Th align="right">Revenue</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {top.data.map((p, i) => (
                <tr key={p.productId}>
                  <Td className="text-slate-400">{i + 1}</Td>
                  <Td>
                    <Link href={`/products/${p.productId}`} className="font-medium text-slate-900 hover:text-primary-700">
                      {p.name}
                    </Link>
                    <p className="text-xs text-slate-500">{p.sku}</p>
                  </Td>
                  <Td align="right">{formatQuantity(p.quantity)}</Td>
                  <Td align="right">{p.receipts}</Td>
                  <Td align="right" className="font-semibold">
                    {formatMoney(p.revenue)}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <Card>
        <CardHeader title="By category" actions={<CsvButton report="categories" params={range} canExport={canExport} />} />
        {cats.isError ? (
          <ErrorState error={cats.error} onRetry={cats.refetch} />
        ) : !cats.data ? (
          <TableSkeleton cols={2} />
        ) : (
          <Table>
            <tbody className="divide-y divide-slate-100">
              {cats.data.map((c) => (
                <tr key={c.categoryId}>
                  <Td>{c.category}</Td>
                  <Td align="right">{formatQuantity(c.quantity)}</Td>
                  <Td align="right" className="font-semibold">
                    {formatMoney(c.revenue)}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}

function StockTab({ canExport, canValue }) {
  const low = useQuery({ queryKey: ["reports", "low-stock"], queryFn: reportsApi.lowStock });
  const exp = useQuery({ queryKey: ["reports", "expiring"], queryFn: () => reportsApi.expiring(7) });
  const val = useQuery({ queryKey: ["reports", "valuation"], queryFn: reportsApi.stockValuation, enabled: canValue });
  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title="Low stock" description="At or below reorder level" actions={<CsvButton report="low-stock" canExport={canExport} />} />
          {low.isError ? (
            <ErrorState error={low.error} onRetry={low.refetch} />
          ) : !low.data ? (
            <TableSkeleton cols={3} />
          ) : low.data.length === 0 ? (
            <EmptyState title="Everything is above its reorder level" />
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>Product</Th>
                  <Th align="right">On hand</Th>
                  <Th align="right">Reorder at</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {low.data.map((r) => (
                  <tr key={r.productId}>
                    <Td>
                      <Link href={`/inventory/${r.productId}`} className="font-medium text-slate-900 hover:text-primary-700">
                        {r.name}
                      </Link>
                      <p className="text-xs text-slate-500">{r.sku}</p>
                    </Td>
                    <Td align="right" className={Number(r.onHand) <= 0 ? "font-semibold text-red-600" : "font-semibold text-amber-700"}>
                      {formatQuantity(r.onHand)}
                    </Td>
                    <Td align="right">{formatQuantity(r.reorderLevel)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
        <Card>
          <CardHeader title="Expiring within 7 days" description="Active batches with stock left" actions={<Link href="/rescue-deals" className="text-sm font-medium text-primary-700 hover:underline">Rescue deals</Link>} />
          {exp.isError ? (
            <ErrorState error={exp.error} onRetry={exp.refetch} />
          ) : !exp.data ? (
            <TableSkeleton cols={3} />
          ) : exp.data.length === 0 ? (
            <EmptyState title="Nothing close to expiry" />
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>Product</Th>
                  <Th>Batch</Th>
                  <Th>Expires</Th>
                  <Th align="right">Qty</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {exp.data.map((r) => (
                  <tr key={`${r.productId}-${r.batchNumber}`}>
                    <Td className="font-medium text-slate-900">{r.name}</Td>
                    <Td className="font-mono text-xs">{r.batchNumber}</Td>
                    <Td>{formatDate(`${r.expiryDate}T12:00:00Z`)}</Td>
                    <Td align="right">{formatQuantity(r.quantity)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>
      {canValue && (
        <Card>
          <CardHeader title="Stock valuation" description="Quantity on hand × moving-average cost" actions={<CsvButton report="stock-valuation" canExport={canExport} />} />
          {val.isError ? (
            <ErrorState error={val.error} onRetry={val.refetch} />
          ) : !val.data ? (
            <TableSkeleton cols={4} />
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>Category</Th>
                  <Th align="right">Products</Th>
                  <Th align="right">Units</Th>
                  <Th align="right">Value</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {val.data.map((r) => (
                  <tr key={r.categoryId}>
                    <Td>{r.category}</Td>
                    <Td align="right">{r.products}</Td>
                    <Td align="right">{formatQuantity(r.units)}</Td>
                    <Td align="right" className="font-semibold">
                      {formatMoney(r.value)}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      )}
    </div>
  );
}

function Reports() {
  const { data: me } = useMe();
  const params = useSearchParams();
  const router = useRouter();
  const canSales = hasPermission(me, "REPORT_VIEW");
  const tab = params.get("tab") || (canSales ? "sales" : "stock");
  const [from, setFrom] = useState(todayInput(-6));
  const [to, setTo] = useState(todayInput());
  const range = { from, to };
  const canExport = hasPermission(me, "REPORT_EXPORT");
  const canValue = canSales && hasAny(me, ["REPORT_FINANCIAL", "PRODUCT_VIEW_COST"]);
  const tabs = [
    ...(canSales ? [{ id: "sales", label: "Sales" }, { id: "products", label: "Products" }] : []),
    { id: "stock", label: "Stock" },
  ];

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Trading and stock for the active store. Days follow the store's local time."
        actions={
          tab !== "stock" && (
            <div className="flex items-end gap-2">
              <label className="text-xs text-slate-500">
                From
                <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="mt-1 w-40" />
              </label>
              <label className="text-xs text-slate-500">
                To
                <Input type="date" value={to} min={from} max={todayInput()} onChange={(e) => setTo(e.target.value)} className="mt-1 w-40" />
              </label>
              <div className="flex gap-1 pb-0.5">
                {[
                  ["7d", -6],
                  ["30d", -29],
                  ["90d", -89],
                ].map(([l, d]) => (
                  <Button key={l} size="sm" variant="ghost" onClick={() => (setFrom(todayInput(d)), setTo(todayInput()))}>
                    {l}
                  </Button>
                ))}
              </div>
            </div>
          )
        }
      />
      <Tabs tabs={tabs} value={tab} onChange={(id) => router.replace(`/reports?tab=${id}`)} />
      {tab === "sales" && canSales && <SalesTab range={range} canExport={canExport} />}
      {tab === "products" && canSales && <ProductsTab range={range} canExport={canExport} />}
      {tab === "stock" && <StockTab canExport={canExport} canValue={canValue} />}
    </div>
  );
}

export default function ReportsPage() {
  return (
    <Suspense>
      <Reports />
    </Suspense>
  );
}
