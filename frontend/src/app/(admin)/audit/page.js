"use client";

import { Fragment, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, ScrollText } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Input, Select } from "@/components/ui/form";
import { dayEnd, dayStart, todayInput } from "@/features/backoffice/api";
import { reportsApi } from "@/features/reports/api";
import { useDebouncedValue } from "@/lib/hooks";
import { formatDateTime } from "@/lib/format";

function pretty(details) {
  try {
    return JSON.stringify(JSON.parse(details), null, 2);
  } catch {
    return details;
  }
}

export default function AuditPage() {
  const [from, setFrom] = useState(todayInput(-6));
  const [to, setTo] = useState(todayInput());
  const [action, setAction] = useState("");
  const [actor, setActor] = useState("");
  const [outcome, setOutcome] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(null);
  const a = useDebouncedValue(action.trim(), 300);
  const u = useDebouncedValue(actor.trim(), 300);
  const params = {
    from: dayStart(from),
    to: dayEnd(to),
    action: a || undefined,
    actor: u || undefined,
    outcome: outcome || undefined,
    page,
    size: 50,
  };
  const list = useQuery({ queryKey: ["audit", params], queryFn: () => reportsApi.audit(params), placeholderData: keepPreviousData });
  const reset = (fn) => (e) => (fn(e.target.value), setPage(0));

  return (
    <div>
      <PageHeader title="Audit log" description="Every sensitive action - who, what, when, from where. Entries cannot be edited or deleted." />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 p-4">
          <label className="text-xs text-slate-500">
            From
            <Input type="date" value={from} max={to} onChange={reset(setFrom)} className="mt-1 w-40" />
          </label>
          <label className="text-xs text-slate-500">
            To
            <Input type="date" value={to} min={from} onChange={reset(setTo)} className="mt-1 w-40" />
          </label>
          <Input className="w-48" placeholder="Action, e.g. LOGIN" value={action} onChange={reset(setAction)} aria-label="Filter by action" />
          <Input className="w-40" placeholder="User" value={actor} onChange={reset(setActor)} aria-label="Filter by user" />
          <Select className="w-36" value={outcome} onChange={reset(setOutcome)} aria-label="Outcome">
            <option value="">Any outcome</option>
            <option value="SUCCESS">Success</option>
            <option value="DENIED">Denied</option>
            <option value="FAILURE">Failure</option>
          </Select>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={ScrollText} title="No entries match" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th />
                  <Th>When</Th>
                  <Th>User</Th>
                  <Th>Action</Th>
                  <Th>Subject</Th>
                  <Th>Summary</Th>
                  <Th>Outcome</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((e) => (
                  <Fragment key={e.id}>
                    <tr className="cursor-pointer hover:bg-slate-50" onClick={() => setOpen(open === e.id ? null : e.id)}>
                      <Td className="w-8">
                        {open === e.id ? <ChevronDown className="h-4 w-4 text-slate-400" /> : <ChevronRight className="h-4 w-4 text-slate-400" />}
                      </Td>
                      <Td className="whitespace-nowrap text-xs">{formatDateTime(e.occurredAt)}</Td>
                      <Td>{e.actorUsername || <span className="text-slate-400">system</span>}</Td>
                      <Td className="font-mono text-xs">{e.action}</Td>
                      <Td className="text-xs">
                        {e.entityType}
                        {e.entityId ? ` #${e.entityId}` : ""}
                      </Td>
                      <Td className="max-w-md truncate text-sm">{e.summary}</Td>
                      <Td>
                        <Badge color={e.outcome === "SUCCESS" ? "green" : e.outcome === "DENIED" ? "amber" : "red"}>{e.outcome.toLowerCase()}</Badge>
                      </Td>
                    </tr>
                    {open === e.id && (
                      <tr className="bg-slate-50">
                        <td colSpan={7} className="px-6 py-3 text-xs text-slate-600">
                          <p>
                            IP {e.ipAddress || "-"} · correlation <span className="font-mono">{e.correlationId || "-"}</span>
                          </p>
                          {e.details && <pre className="mt-2 max-h-64 overflow-auto rounded bg-white p-3 font-mono text-[11px]">{pretty(e.details)}</pre>}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} size={50} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
