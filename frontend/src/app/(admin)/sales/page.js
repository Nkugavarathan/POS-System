"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Globe, Receipt, Search } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Input, Select } from "@/components/ui/form";
import { dayEnd, dayStart, salesApi, todayInput } from "@/features/backoffice/api";
import { useDebouncedValue } from "@/lib/hooks";
import { formatDateTime, formatMoney } from "@/lib/format";
import { compare } from "@/lib/money";

const STATUS = {
  COMPLETED: ["Completed", "green"],
  PARTIALLY_RETURNED: ["Part returned", "amber"],
  FULLY_RETURNED: ["Returned", "red"],
};

export default function SalesPage() {
  const [from, setFrom] = useState(todayInput(-6));
  const [to, setTo] = useState(todayInput());
  const [channel, setChannel] = useState("");
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const search = useDebouncedValue(q.trim(), 300);
  const params = {
    from: dayStart(from),
    to: dayEnd(to),
    channel: channel || undefined,
    status: status || undefined,
    q: search || undefined,
    page,
    size: 25,
    sort: "completedAt,desc",
  };
  const sales = useQuery({ queryKey: ["sales", "list", params], queryFn: () => salesApi.list(params), placeholderData: keepPreviousData });
  const reset = (fn) => (e) => {
    fn(e.target.value);
    setPage(0);
  };

  return (
    <div>
      <PageHeader title="Sales & receipts" description="Every completed sale from the tills and online pickups. Open one to reprint or return items." />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 p-4">
          <div className="relative min-w-56 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input className="pl-9" placeholder="Receipt number or customer" value={q} onChange={reset(setQ)} aria-label="Search sales" />
          </div>
          <label className="text-xs text-slate-500">
            From
            <Input type="date" value={from} max={to} onChange={reset(setFrom)} className="mt-1 w-40" />
          </label>
          <label className="text-xs text-slate-500">
            To
            <Input type="date" value={to} min={from} onChange={reset(setTo)} className="mt-1 w-40" />
          </label>
          <Select value={channel} onChange={reset(setChannel)} className="w-36" aria-label="Channel">
            <option value="">All channels</option>
            <option value="POS">In store</option>
            <option value="ONLINE">Online pickup</option>
          </Select>
          <Select value={status} onChange={reset(setStatus)} className="w-40" aria-label="Status">
            <option value="">Any status</option>
            {Object.entries(STATUS).map(([k, [label]]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        {sales.isError ? (
          <ErrorState error={sales.error} onRetry={sales.refetch} />
        ) : sales.isLoading ? (
          <TableSkeleton cols={7} />
        ) : sales.data.content.length === 0 ? (
          <EmptyState icon={Receipt} title="No sales in this period" description="Widen the dates or clear the search." />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Receipt</Th>
                  <Th>Time</Th>
                  <Th>Cashier · till</Th>
                  <Th>Customer</Th>
                  <Th>Tenders</Th>
                  <Th align="right">Items</Th>
                  <Th align="right">Total</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sales.data.content.map((s) => (
                  <tr key={s.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/sales/${s.id}`} className="font-mono font-medium text-primary-700 hover:underline">
                        {s.receiptNumber}
                      </Link>
                      {s.channel === "ONLINE" && (
                        <Globe className="ml-1.5 inline h-3.5 w-3.5 text-slate-400" aria-label="Online pickup" />
                      )}
                    </Td>
                    <Td className="whitespace-nowrap">{formatDateTime(s.completedAt)}</Td>
                    <Td>
                      {s.cashierName} <span className="text-slate-400">· {s.registerCode}</span>
                    </Td>
                    <Td>{s.customerName || <span className="text-slate-400">Walk-in</span>}</Td>
                    <Td className="text-xs">{s.tenders.join(", ")}</Td>
                    <Td align="right">{s.itemCount}</Td>
                    <Td align="right" className="font-semibold">
                      {formatMoney(s.grandTotal)}
                      {compare(s.refundedTotal ?? "0", "0") > 0 && (
                        <p className="text-xs font-normal text-red-600">−{formatMoney(s.refundedTotal)} refunded</p>
                      )}
                    </Td>
                    <Td>
                      <Badge color={STATUS[s.status][1]}>{STATUS[s.status][0]}</Badge>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} size={25} totalPages={sales.data.totalPages} totalElements={sales.data.totalElements} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
