"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Receipt, RotateCcw, Search } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Alert, ErrorState } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { ApprovalDialog } from "@/features/auth/approval-dialog";
import { returnsApi, salesApi, shiftsApi } from "@/features/backoffice/api";
import { errorMessage } from "@/lib/api-error";
import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";
import { newIdempotencyKey } from "@/lib/idempotency";
import { centsToString, toCents, toMilli } from "@/lib/money";

const REASONS = [
  ["DEFECTIVE", "Faulty / damaged"],
  ["EXPIRED", "Expired or spoiled"],
  ["WRONG_ITEM", "Wrong item sold"],
  ["CHANGED_MIND", "Changed mind"],
  ["OTHER", "Other"],
];

function remaining(line) {
  const left = toMilli(line.quantity) - toMilli(line.quantityReturned || "0");
  return left > 0n ? left : 0n;
}

function milliToString(m) {
  const whole = m / 1000n;
  const frac = m % 1000n;
  return frac === 0n ? String(whole) : `${whole}.${String(frac).padStart(3, "0").replace(/0+$/, "")}`;
}

/** Splits the refund over the original tenders (non-cash first, cash last) within what each allows. */
function allocate(total, tenders) {
  let left = toCents(total);
  const ordered = [...tenders].sort((a, b) => (a.tenderType === "CASH") - (b.tenderType === "CASH"));
  const out = [];
  for (const t of ordered) {
    if (left <= 0n) break;
    const take = toCents(t.available) < left ? toCents(t.available) : left;
    if (take > 0n) {
      out.push({ tenderType: t.tenderType, amount: centsToString(take) });
      left -= take;
    }
  }
  return out;
}

function NewReturn() {
  const router = useRouter();
  const params = useSearchParams();
  const [receiptInput, setReceiptInput] = useState(params.get("receipt") || "");
  const [receipt, setReceipt] = useState(params.get("receipt") || "");
  const [selected, setSelected] = useState({}); // saleItemId → { quantity, condition, restock }
  const [reasonCode, setReasonCode] = useState("DEFECTIVE");
  const [notes, setNotes] = useState("");
  const [preview, setPreview] = useState(null);
  const [refunds, setRefunds] = useState([]);
  const [approval, setApproval] = useState(false);
  const [key, setKey] = useState(() => newIdempotencyKey());

  const shift = useQuery({ queryKey: ["pos", "shift"], queryFn: shiftsApi.current });
  const sale = useQuery({ queryKey: ["sales", "receipt", receipt], queryFn: () => salesApi.byReceipt(receipt), enabled: Boolean(receipt) });

  useEffect(() => {
    setSelected({});
    setPreview(null);
  }, [receipt]);

  const items = useMemo(
    () =>
      Object.entries(selected)
        .filter(([, v]) => v.quantity && toMilli(v.quantity) > 0n)
        .map(([id, v]) => ({ saleItemId: Number(id), quantity: v.quantity, condition: v.condition, restock: v.condition === "SALEABLE" && v.restock, reason: null })),
    [selected],
  );

  const doPreview = useMutation({
    mutationFn: () => returnsApi.preview({ originalReceiptNumber: receipt, items }),
    onSuccess: (p) => {
      setPreview(p);
      setRefunds(allocate(p.refundTotal, p.tenders));
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const create = useMutation({
    mutationFn: (approvalToken) =>
      returnsApi.create(
        {
          originalReceiptNumber: receipt,
          registerId: shift.data.registerId,
          reasonCode,
          notes: notes.trim() || null,
          items,
          refunds,
          approvalToken: approvalToken || null,
        },
        key,
      ),
    onSuccess: (r) => {
      toast.success(`Return ${r.returnNumber} completed. Refund ${formatMoney(r.refundTotal)}.`);
      router.push(`/returns/${r.id}`);
    },
    onError: (e) => {
      if (e.code === "APPROVAL_REQUIRED") setApproval(true);
      else {
        if (e.status !== 0) setKey(newIdempotencyKey());
        toast.error(errorMessage(e));
      }
    },
  });

  const update = (id, patch) => {
    setPreview(null);
    setSelected((s) => ({ ...s, [id]: { quantity: "", condition: "SALEABLE", restock: true, ...s[id], ...patch } }));
  };
  const refundSum = refunds.reduce((a, r) => a + toCents(r.amount), 0n);
  const balanced = preview && refundSum === toCents(preview.refundTotal);

  return (
    <div>
      <Link href="/returns" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Returns
      </Link>
      <PageHeader title="New return" description="Find the receipt, choose what comes back, and refund to the original payment." />

      {!shift.isLoading && !shift.data && (
        <Alert tone="warning" title="No open shift" className="mb-4">
          Refunds come out of a till. Open a shift on the <Link href="/pos" className="font-semibold underline">POS</Link> first, then
          come back here.
        </Alert>
      )}

      <Card className="mb-6 p-4">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            setReceipt(receiptInput.trim());
          }}
        >
          <Field label="Receipt number" className="w-72">
            {(a11y) => (
              <div className="relative">
                <Receipt className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <Input {...a11y} autoFocus className="pl-9 font-mono" value={receiptInput} onChange={(e) => setReceiptInput(e.target.value)} />
              </div>
            )}
          </Field>
          <Button type="submit" variant="outline" disabled={!receiptInput.trim()}>
            <Search className="h-4 w-4" aria-hidden="true" /> Find sale
          </Button>
        </form>
      </Card>

      {sale.isError && <ErrorState error={sale.error} onRetry={sale.refetch} />}
      {sale.data && (
        <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
          <Card>
            <CardHeader
              title={`Sale ${sale.data.receiptNumber}`}
              description={`${formatDateTime(sale.data.completedAt)} · ${sale.data.cashierName} · ${sale.data.customerName || "walk-in"} · total ${formatMoney(sale.data.totals.grandTotal)}`}
            />
            <Table>
              <thead>
                <tr>
                  <Th>Product</Th>
                  <Th align="right">Sold</Th>
                  <Th align="right">Can return</Th>
                  <Th>Return qty</Th>
                  <Th>Condition</Th>
                  <Th>Back to shelf</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sale.data.lines.map((l) => {
                  const left = remaining(l);
                  const v = selected[l.saleItemId] || {};
                  const qtyOk = !v.quantity || (toMilli(v.quantity) > 0n && toMilli(v.quantity) <= left);
                  return (
                    <tr key={l.saleItemId} className={left === 0n ? "opacity-50" : undefined}>
                      <Td>
                        <p className="font-medium text-slate-900">{l.name}</p>
                        <p className="text-xs text-slate-500">
                          {formatMoney(l.lineTotal)} paid for {formatQuantity(l.quantity)}
                        </p>
                      </Td>
                      <Td align="right">{formatQuantity(l.quantity)}</Td>
                      <Td align="right">{milliToString(left)}</Td>
                      <Td>
                        <div className="flex items-center gap-1">
                          <Input
                            className="w-20"
                            inputMode="decimal"
                            disabled={left === 0n}
                            aria-invalid={!qtyOk || undefined}
                            aria-label={`Return quantity for ${l.name}`}
                            value={v.quantity || ""}
                            onChange={(e) => update(l.saleItemId, { quantity: e.target.value.trim() })}
                          />
                          <Button size="sm" variant="ghost" disabled={left === 0n} onClick={() => update(l.saleItemId, { quantity: milliToString(left) })}>
                            All
                          </Button>
                        </div>
                      </Td>
                      <Td>
                        <Select
                          className="w-32"
                          disabled={left === 0n}
                          value={v.condition || "SALEABLE"}
                          onChange={(e) => update(l.saleItemId, { condition: e.target.value })}
                          aria-label={`Condition of ${l.name}`}
                        >
                          <option value="SALEABLE">Saleable</option>
                          <option value="DAMAGED">Damaged</option>
                          <option value="EXPIRED">Expired</option>
                        </Select>
                      </Td>
                      <Td>
                        <input
                          type="checkbox"
                          className="h-4 w-4"
                          disabled={left === 0n || (v.condition && v.condition !== "SALEABLE")}
                          checked={(v.condition || "SALEABLE") === "SALEABLE" && v.restock !== false}
                          onChange={(e) => update(l.saleItemId, { restock: e.target.checked })}
                          aria-label={`Restock ${l.name}`}
                        />
                      </Td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </Card>

          <div className="space-y-4">
            <Card className="space-y-3 p-5">
              <Field label="Reason">
                {(a11y) => (
                  <Select {...a11y} value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
                    {REASONS.map(([v, l]) => (
                      <option key={v} value={v}>
                        {l}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <Field label="Notes">{(a11y) => <Textarea {...a11y} value={notes} onChange={(e) => setNotes(e.target.value)} />}</Field>
              <Button className="w-full" variant="outline" disabled={!items.length} loading={doPreview.isPending} onClick={() => doPreview.mutate()}>
                Calculate refund
              </Button>
            </Card>

            {preview && (
              <Card className="space-y-3 p-5">
                <div className="flex items-baseline justify-between">
                  <span className="text-sm text-slate-600">Refund due</span>
                  <span className="text-2xl font-bold tabular">{formatMoney(preview.refundTotal)}</span>
                </div>
                {!preview.withinWindow && <Alert tone="warning">Outside the return window — a manager must approve.</Alert>}
                {preview.approvalRequired && <Alert tone="info">This return needs manager approval.</Alert>}
                {preview.loyaltyPointsToReverse > 0 && (
                  <p className="text-xs text-slate-500">{preview.loyaltyPointsToReverse} loyalty points will be taken back.</p>
                )}
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Refund to</p>
                  {preview.tenders.map((t) => {
                    const r = refunds.find((x) => x.tenderType === t.tenderType);
                    return (
                      <div key={t.tenderType} className="mb-2 flex items-center gap-2">
                        <span className="w-20 text-sm font-medium">{t.tenderType}</span>
                        <Input
                          className="flex-1"
                          inputMode="decimal"
                          value={r?.amount ?? ""}
                          onChange={(e) => {
                            const amount = e.target.value.trim();
                            setRefunds((list) => [...list.filter((x) => x.tenderType !== t.tenderType), ...(amount ? [{ tenderType: t.tenderType, amount }] : [])]);
                          }}
                          aria-label={`Refund amount to ${t.tenderType}`}
                        />
                        <span className="w-24 text-right text-xs text-slate-500">max {formatMoney(t.available)}</span>
                      </div>
                    );
                  })}
                  {!balanced && (
                    <p className="text-xs font-medium text-red-600">
                      Refund amounts add up to {formatMoney(centsToString(refundSum))}; they must equal {formatMoney(preview.refundTotal)}.
                    </p>
                  )}
                </div>
                <Button
                  className="w-full"
                  size="lg"
                  disabled={!balanced || !shift.data}
                  loading={create.isPending}
                  onClick={() => (preview.approvalRequired ? setApproval(true) : create.mutate(null))}
                >
                  <RotateCcw className="h-4 w-4" aria-hidden="true" /> Complete return
                </Button>
              </Card>
            )}
          </div>
        </div>
      )}

      {approval && (
        <ApprovalDialog
          action="RETURN"
          title="Manager approval for this return"
          description={`Refund ${preview ? formatMoney(preview.refundTotal) : ""} on receipt ${receipt}`}
          value={preview?.refundTotal}
          registerId={shift.data?.registerId}
          onClose={() => setApproval(false)}
          onApproved={(token) => {
            setApproval(false);
            create.mutate(token);
          }}
        />
      )}
    </div>
  );
}

export default function NewReturnPage() {
  return (
    <Suspense>
      <NewReturn />
    </Suspense>
  );
}
