"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Printer } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { returnsApi } from "@/features/backoffice/api";
import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

export default function ReturnDetailPage({ params }) {
  const { id } = use(params);
  const r = useQuery({ queryKey: ["returns", Number(id)], queryFn: () => returnsApi.get(Number(id)) });
  if (r.isError) return <ErrorState error={r.error} onRetry={r.refetch} />;
  const d = r.data;
  if (!d) return <Skeleton className="h-80" />;
  return (
    <div className="print-area">
      <Link href="/returns" className="no-print mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Returns
      </Link>
      <PageHeader
        title={`Return ${d.returnNumber}`}
        description={`${formatDateTime(d.createdAt)} · against receipt ${d.originalReceiptNumber} · by ${d.processedBy}${d.approvedBy ? `, approved by ${d.approvedBy}` : ""}`}
        actions={
          <Button variant="outline" className="no-print" onClick={() => window.print()}>
            <Printer className="h-4 w-4" aria-hidden="true" /> Print
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader title="Items returned" description={`Reason: ${d.reasonCode.replaceAll("_", " ").toLowerCase()}${d.notes ? ` - ${d.notes}` : ""}`} />
          <Table>
            <thead>
              <tr>
                <Th>Product</Th>
                <Th align="right">Qty</Th>
                <Th>Condition</Th>
                <Th>Stock</Th>
                <Th align="right">Refund</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {d.items.map((i) => (
                <tr key={i.saleItemId}>
                  <Td className="font-medium text-slate-900">{i.name}</Td>
                  <Td align="right">{formatQuantity(i.quantity)}</Td>
                  <Td>
                    <Badge color={i.condition === "SALEABLE" ? "green" : "red"}>{i.condition.toLowerCase()}</Badge>
                  </Td>
                  <Td className="text-xs">{i.restock ? "Back on shelf" : "Written off"}</Td>
                  <Td align="right">{formatMoney(i.refundAmount)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
        <Card className="p-5">
          <p className="text-sm text-slate-500">Total refunded</p>
          <p className="text-3xl font-bold tabular text-red-600">{formatMoney(d.refundTotal)}</p>
          <p className="text-xs text-slate-500">incl. tax {formatMoney(d.taxTotal)}</p>
          <ul className="mt-4 space-y-1 text-sm">
            {d.refunds.map((x, i) => (
              <li key={i} className="flex justify-between">
                <span>
                  {x.tenderType}
                  {x.cardLast4 ? ` ···· ${x.cardLast4}` : ""}
                </span>
                <span className="tabular">{formatMoney(x.amount)}</span>
              </li>
            ))}
          </ul>
          {d.loyaltyPointsReversed > 0 && <p className="mt-3 text-xs text-slate-500">{d.loyaltyPointsReversed} loyalty points reversed</p>}
          <Link href={`/sales/${d.originalSaleId}`} className="no-print mt-4 inline-block text-sm font-medium text-primary-700 hover:underline">
            View original sale
          </Link>
        </Card>
      </div>
    </div>
  );
}
