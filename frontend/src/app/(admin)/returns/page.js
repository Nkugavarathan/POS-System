"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus, RotateCcw } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Input } from "@/components/ui/form";
import { useMe } from "@/features/auth/hooks";
import { dayEnd, dayStart, returnsApi, todayInput } from "@/features/backoffice/api";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime, formatMoney } from "@/lib/format";

export default function ReturnsPage() {
  const { data: me } = useMe();
  const [from, setFrom] = useState(todayInput(-29));
  const [to, setTo] = useState(todayInput());
  const [page, setPage] = useState(0);
  const params = { from: dayStart(from), to: dayEnd(to), page, size: 25 };
  const list = useQuery({ queryKey: ["returns", "list", params], queryFn: () => returnsApi.list(params), placeholderData: keepPreviousData });

  return (
    <div>
      <PageHeader
        title="Returns"
        description="Refunds against original receipts, with stock put back or written off."
        actions={
          hasPermission(me, "RETURN_CREATE") && (
            <Link href="/returns/new" className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-700">
              <Plus className="h-4 w-4" aria-hidden="true" /> New return
            </Link>
          )
        }
      />
      <Card>
        <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">
          <label className="text-xs text-slate-500">
            From
            <Input type="date" value={from} max={to} onChange={(e) => (setFrom(e.target.value), setPage(0))} className="mt-1 w-40" />
          </label>
          <label className="text-xs text-slate-500">
            To
            <Input type="date" value={to} min={from} onChange={(e) => (setTo(e.target.value), setPage(0))} className="mt-1 w-40" />
          </label>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={RotateCcw} title="No returns in this period" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Return</Th>
                  <Th>When</Th>
                  <Th>Original receipt</Th>
                  <Th>Reason</Th>
                  <Th>Processed by</Th>
                  <Th align="right">Refund</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/returns/${r.id}`} className="font-mono font-medium text-primary-700 hover:underline">
                        {r.returnNumber}
                      </Link>
                    </Td>
                    <Td>{formatDateTime(r.createdAt)}</Td>
                    <Td>
                      <Link href={`/sales/${r.originalSaleId}`} className="font-mono text-slate-700 hover:underline">
                        {r.originalReceiptNumber}
                      </Link>
                    </Td>
                    <Td className="text-xs">{r.reasonCode.replaceAll("_", " ").toLowerCase()}</Td>
                    <Td>
                      {r.processedBy}
                      {r.approvedBy && <span className="text-xs text-slate-500"> · approved {r.approvedBy}</span>}
                    </Td>
                    <Td align="right" className="font-semibold text-red-600">
                      {formatMoney(r.refundTotal)}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} size={25} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
