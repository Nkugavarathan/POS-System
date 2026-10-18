"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Printer } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { shiftsApi } from "@/features/backoffice/api";
import { formatDateTime, formatMoney } from "@/lib/format";
import { compare } from "@/lib/money";

function Row({ label, value, strong, tone }) {
  return (
    <div className={`flex justify-between py-1.5 text-sm ${strong ? "border-t border-slate-200 pt-2.5 font-semibold" : ""}`}>
      <dt className="text-slate-600">{label}</dt>
      <dd className={`tabular ${tone || ""}`}>{value}</dd>
    </div>
  );
}

export default function ShiftSummaryPage({ params }) {
  const { id } = use(params);
  const q = useQuery({ queryKey: ["shifts", "summary", Number(id)], queryFn: () => shiftsApi.summary(Number(id)) });
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const s = q.data;
  if (!s) return <Skeleton className="h-96" />;
  const sh = s.shift;
  const variance = sh.variance;
  const vc = variance == null ? 0 : compare(variance, "0");

  return (
    <div className="print-area">
      <Link href="/shifts" className="no-print mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Shifts
      </Link>
      <PageHeader
        title={`Shift ${sh.shiftNumber}`}
        description={`${sh.cashierName} · ${sh.registerCode} · ${formatDateTime(sh.openedAt)} → ${sh.closedAt ? formatDateTime(sh.closedAt) : "still open"}`}
        actions={
          <Button variant="outline" className="no-print" onClick={() => window.print()}>
            <Printer className="h-4 w-4" aria-hidden="true" /> Print Z-report
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="p-5">
          <h2 className="text-sm font-semibold text-slate-900">Cash drawer</h2>
          <dl className="mt-3">
            <Row label="Opening float" value={formatMoney(sh.openingFloat)} />
            <Row label="Cash sales" value={formatMoney(s.cashSales)} />
            <Row label="Cash refunds" value={`−${formatMoney(s.cashRefunds)}`} />
            <Row label="Pay-ins" value={formatMoney(s.payIns)} />
            <Row label="Pay-outs" value={`−${formatMoney(s.payOuts)}`} />
            <Row label="Cash drops" value={`−${formatMoney(s.cashDrops)}`} />
            <Row label="Expected in drawer" value={formatMoney(s.expectedCash)} strong />
            {sh.countedCash != null && <Row label="Counted" value={formatMoney(sh.countedCash)} />}
            {variance != null && (
              <Row
                label="Variance"
                value={`${vc > 0 ? "+" : ""}${formatMoney(variance)}`}
                strong
                tone={vc === 0 ? "text-emerald-700" : vc > 0 ? "text-sky-700" : "text-red-700"}
              />
            )}
          </dl>
        </Card>
        <Card className="p-5">
          <h2 className="text-sm font-semibold text-slate-900">Activity</h2>
          <dl className="mt-3">
            <Row label="Sales" value={`${s.salesCount} · ${formatMoney(s.salesTotal)}`} />
            <Row label="Returns" value={`${s.returnsCount} · −${formatMoney(s.refundsTotal)}`} />
            <Row label="Voids recorded" value={s.voidsCount} />
          </dl>
          <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-slate-500">By tender</h3>
          <dl className="mt-1">
            {s.tenderTotals.map((t) => (
              <Row key={t.tenderType} label={`${t.tenderType} (${t.count})`} value={formatMoney(t.amount)} />
            ))}
          </dl>
        </Card>
        <Card>
          <CardHeader title="Cash movements" />
          {s.cashMovements.length === 0 ? (
            <p className="px-5 py-6 text-sm text-slate-500">None</p>
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>Type</Th>
                  <Th>Reason</Th>
                  <Th align="right">Amount</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {s.cashMovements.map((m) => (
                  <tr key={m.id}>
                    <Td>
                      <Badge>{m.type.replaceAll("_", " ").toLowerCase()}</Badge>
                    </Td>
                    <Td className="text-xs">
                      {m.reason}
                      <p className="text-slate-400">{formatDateTime(m.createdAt)}</p>
                    </Td>
                    <Td align="right">{formatMoney(m.amount)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>
    </div>
  );
}
