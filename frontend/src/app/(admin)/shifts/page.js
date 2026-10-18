"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Wallet } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Input, Select } from "@/components/ui/form";
import { dayEnd, dayStart, shiftsApi, todayInput } from "@/features/backoffice/api";
import { formatDateTime, formatMoney } from "@/lib/format";
import { compare } from "@/lib/money";

function VarianceBadge({ value }) {
  if (value === null || value === undefined) return <span className="text-slate-400">-</span>;
  const c = compare(value, "0");
  return <Badge color={c === 0 ? "green" : c > 0 ? "blue" : "red"}>{c > 0 ? "+" : ""}{formatMoney(value)}</Badge>;
}

export default function ShiftsPage() {
  const [from, setFrom] = useState(todayInput(-6));
  const [to, setTo] = useState(todayInput());
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const params = { from: dayStart(from), to: dayEnd(to), status: status || undefined, page, size: 25 };
  const list = useQuery({ queryKey: ["shifts", params], queryFn: () => shiftsApi.list(params), placeholderData: keepPreviousData });

  return (
    <div>
      <PageHeader title="Cashier shifts" description="Opening floats, expected vs counted cash and variances for every till session." />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 p-4">
          <label className="text-xs text-slate-500">
            From
            <Input type="date" value={from} max={to} onChange={(e) => (setFrom(e.target.value), setPage(0))} className="mt-1 w-40" />
          </label>
          <label className="text-xs text-slate-500">
            To
            <Input type="date" value={to} min={from} onChange={(e) => (setTo(e.target.value), setPage(0))} className="mt-1 w-40" />
          </label>
          <Select value={status} onChange={(e) => (setStatus(e.target.value), setPage(0))} className="w-36" aria-label="Status">
            <option value="">All</option>
            <option value="OPEN">Open</option>
            <option value="CLOSED">Closed</option>
          </Select>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={7} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={Wallet} title="No shifts in this period" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Shift</Th>
                  <Th>Cashier · till</Th>
                  <Th>Opened</Th>
                  <Th>Closed</Th>
                  <Th align="right">Float</Th>
                  <Th align="right">Expected</Th>
                  <Th align="right">Counted</Th>
                  <Th>Variance</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((s) => (
                  <tr key={s.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/shifts/${s.id}`} className="font-mono font-medium text-primary-700 hover:underline">
                        {s.shiftNumber}
                      </Link>
                    </Td>
                    <Td>
                      {s.cashierName} <span className="text-slate-400">· {s.registerCode}</span>
                    </Td>
                    <Td className="whitespace-nowrap">{formatDateTime(s.openedAt)}</Td>
                    <Td className="whitespace-nowrap">{s.closedAt ? formatDateTime(s.closedAt) : <Badge color="green">Open</Badge>}</Td>
                    <Td align="right">{formatMoney(s.openingFloat)}</Td>
                    <Td align="right">{s.expectedCash ? formatMoney(s.expectedCash) : "-"}</Td>
                    <Td align="right">{s.countedCash ? formatMoney(s.countedCash) : "-"}</Td>
                    <Td>
                      <VarianceBadge value={s.variance} />
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
