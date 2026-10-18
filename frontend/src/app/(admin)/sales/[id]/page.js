"use client";

import { use } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Printer, RotateCcw } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Receipt } from "@/features/sales/receipt";
import { useMe } from "@/features/auth/hooks";
import { returnsApi, salesApi } from "@/features/backoffice/api";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";
import { compare } from "@/lib/money";

export default function SaleDetailPage({ params }) {
  const { id } = use(params);
  const saleId = Number(id);
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const sale = useQuery({ queryKey: ["sales", saleId], queryFn: () => salesApi.get(saleId) });
  const returns = useQuery({ queryKey: ["returns", "bySale", saleId], queryFn: () => returnsApi.list({ saleId, size: 50 }) });
  const reprint = useMutation({
    mutationFn: () => salesApi.reprint(saleId),
    onSuccess: (data) => {
      queryClient.setQueryData(["sales", saleId], data);
      // Print after React renders the "REPRINT" copy.
      setTimeout(() => window.print(), 100);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (sale.isError) return <ErrorState error={sale.error} onRetry={sale.refetch} />;
  const s = sale.data;
  if (!s) return <Skeleton className="h-96" />;
  const returnable = s.status !== "FULLY_RETURNED" && hasPermission(me, "RETURN_CREATE");

  return (
    <div>
      <Link href="/sales" className="no-print mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Sales
      </Link>
      <div className="no-print">
        <PageHeader
          title={`Receipt ${s.receiptNumber}`}
          description={`${formatDateTime(s.completedAt)} · ${s.cashierName} · ${s.registerCode}${s.onlineOrderNumber ? ` · online order #${s.onlineOrderNumber}` : ""}`}
          actions={
            <>
              <Button variant="outline" loading={reprint.isPending} onClick={() => reprint.mutate()}>
                <Printer className="h-4 w-4" aria-hidden="true" /> Reprint
              </Button>
              {returnable && (
                <Link
                  href={`/returns/new?receipt=${encodeURIComponent(s.receiptNumber)}`}
                  className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-700"
                >
                  <RotateCcw className="h-4 w-4" aria-hidden="true" /> Return items
                </Link>
              )}
            </>
          }
        />
      </div>
      <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
        <div className="no-print space-y-6">
          <Card>
            <CardHeader
              title="Items"
              actions={
                <Badge color={s.status === "COMPLETED" ? "green" : s.status === "FULLY_RETURNED" ? "red" : "amber"}>
                  {s.status.replaceAll("_", " ").toLowerCase()}
                </Badge>
              }
            />
            <Table>
              <thead>
                <tr>
                  <Th>#</Th>
                  <Th>Product</Th>
                  <Th align="right">Qty</Th>
                  <Th align="right">Price</Th>
                  <Th align="right">Discounts</Th>
                  <Th align="right">Tax</Th>
                  <Th align="right">Total</Th>
                  <Th align="right">Returned</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {s.lines.map((l) => {
                  const disc = [l.promoDiscount, l.manualDiscount, l.allocatedTransactionDiscount].filter((x) => x && compare(x, "0") > 0);
                  return (
                    <tr key={l.lineNumber}>
                      <Td>{l.lineNumber}</Td>
                      <Td>
                        <p className="font-medium text-slate-900">{l.name}</p>
                        <p className="text-xs text-slate-500">{l.sku}</p>
                      </Td>
                      <Td align="right">{formatQuantity(l.quantity)}</Td>
                      <Td align="right">{formatMoney(l.unitPrice)}</Td>
                      <Td align="right" className="text-red-600">
                        {disc.length ? disc.map((d) => `−${formatMoney(d)}`).join(" ") : "-"}
                      </Td>
                      <Td align="right">{formatMoney(l.taxAmount)}</Td>
                      <Td align="right" className="font-semibold">
                        {formatMoney(l.lineTotal)}
                      </Td>
                      <Td align="right">{l.quantityReturned && compare(l.quantityReturned, "0") > 0 ? formatQuantity(l.quantityReturned) : "-"}</Td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </Card>
          <div className="grid gap-6 md:grid-cols-2">
            <Card>
              <CardHeader title="Payments" />
              <ul className="divide-y divide-slate-100 text-sm">
                {s.payments.map((p, i) => (
                  <li key={i} className="flex justify-between px-5 py-2.5">
                    <span>
                      {p.tenderType}
                      {p.cardLast4 && <span className="text-slate-500"> ···· {p.cardLast4}</span>}
                      {p.authCode && <span className="text-xs text-slate-400"> auth {p.authCode}</span>}
                    </span>
                    <span className="tabular">{formatMoney(p.amount)}</span>
                  </li>
                ))}
                {compare(s.changeDue ?? "0", "0") > 0 && (
                  <li className="flex justify-between px-5 py-2.5 text-slate-500">
                    <span>Change given</span>
                    <span className="tabular">{formatMoney(s.changeDue)}</span>
                  </li>
                )}
              </ul>
            </Card>
            <Card>
              <CardHeader title="Customer & loyalty" />
              <div className="space-y-1 px-5 py-3 text-sm">
                {s.customerId ? (
                  <Link href={`/customers/${s.customerId}`} className="font-medium text-primary-700 hover:underline">
                    {s.customerName}
                  </Link>
                ) : (
                  <p className="text-slate-500">Walk-in customer</p>
                )}
                <p className="text-slate-600">Points earned: {s.loyaltyPointsEarned}</p>
                <p className="text-slate-600">Points redeemed: {s.loyaltyPointsRedeemed}</p>
                <p className="text-slate-600">Reprinted {s.reprintCount} time{s.reprintCount === 1 ? "" : "s"}</p>
              </div>
            </Card>
          </div>
          {returns.data?.content?.length > 0 && (
            <Card>
              <CardHeader title="Returns against this sale" />
              <ul className="divide-y divide-slate-100 text-sm">
                {returns.data.content.map((r) => (
                  <li key={r.id} className="flex justify-between px-5 py-2.5">
                    <Link href={`/returns/${r.id}`} className="font-mono text-primary-700 hover:underline">
                      {r.returnNumber}
                    </Link>
                    <span className="text-slate-500">{formatDateTime(r.createdAt)}</span>
                    <span className="tabular text-red-600">−{formatMoney(r.refundTotal)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
        <div>
          <p className="no-print mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Receipt preview</p>
          <div className="print-area rounded-lg border border-slate-200 bg-white py-4">
            <Receipt sale={s} />
          </div>
        </div>
      </div>
    </div>
  );
}
