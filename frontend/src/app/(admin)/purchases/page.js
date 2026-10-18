"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ClipboardList, Plus, Search } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Input, Select } from "@/components/ui/form";
import { PO_STATUS, purchasingApi } from "@/features/backoffice/api";
import { useDebouncedValue } from "@/lib/hooks";
import { formatDate, formatMoney } from "@/lib/format";

export default function PurchasesPage() {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const search = useDebouncedValue(q.trim(), 300);
  const params = { q: search || undefined, status: status || undefined, page, size: 25 };
  const list = useQuery({ queryKey: ["purchases", params], queryFn: () => purchasingApi.orders(params), placeholderData: keepPreviousData });

  return (
    <div>
      <PageHeader
        title="Purchase orders"
        description="Order from suppliers and receive deliveries into stock (with batches and expiry dates)."
        actions={
          <Link href="/purchases/new" className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-700">
            <Plus className="h-4 w-4" aria-hidden="true" /> New order
          </Link>
        }
      />
      <Card>
        <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input className="pl-9" placeholder="PO number or supplier" value={q} onChange={(e) => (setQ(e.target.value), setPage(0))} aria-label="Search purchase orders" />
          </div>
          <Select value={status} onChange={(e) => (setStatus(e.target.value), setPage(0))} className="w-44" aria-label="Status">
            <option value="">Any status</option>
            {Object.entries(PO_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </Select>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={ClipboardList} title="No purchase orders" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>PO</Th>
                  <Th>Supplier</Th>
                  <Th>Ordered</Th>
                  <Th>Expected</Th>
                  <Th align="right">Lines</Th>
                  <Th align="right">Value</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((po) => (
                  <tr key={po.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/purchases/${po.id}`} className="font-mono font-medium text-primary-700 hover:underline">
                        {po.poNumber}
                      </Link>
                    </Td>
                    <Td>{po.supplierName}</Td>
                    <Td>{po.orderDate ? formatDate(po.orderDate) : "—"}</Td>
                    <Td>{po.expectedDate ? formatDate(po.expectedDate) : "—"}</Td>
                    <Td align="right">{po.items.length}</Td>
                    <Td align="right">{formatMoney(po.expectedTotal)}</Td>
                    <Td>
                      <Badge color={PO_STATUS[po.status][1]}>{PO_STATUS[po.status][0]}</Badge>
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
