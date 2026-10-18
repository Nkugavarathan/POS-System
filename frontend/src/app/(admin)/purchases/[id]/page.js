"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Ban, Lock, PackagePlus, Pencil, Send } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Alert, Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { ConfirmDialog } from "@/components/ui/dialog";
import { PO_STATUS, purchasingApi } from "@/features/backoffice/api";
import { PoEditor } from "@/features/backoffice/po-editor";
import { errorMessage } from "@/lib/api-error";
import { formatDate, formatDateTime, formatMoney, formatQuantity } from "@/lib/format";
import { newIdempotencyKey } from "@/lib/idempotency";
import { toMilli } from "@/lib/money";

function ReceiveForm({ po, onDone }) {
  const queryClient = useQueryClient();
  const outstanding = po.items.filter((l) => toMilli(l.outstanding) > 0n);
  const [rows, setRows] = useState(() =>
    Object.fromEntries(
      outstanding.map((l) => [
        l.id,
        { quantity: formatQuantity(l.outstanding), unitCost: formatQuantity(l.expectedUnitCost), batchNumber: "", expiryDate: "" },
      ]),
    ),
  );
  const [invoice, setInvoice] = useState("");
  const [key] = useState(() => newIdempotencyKey());
  const items = outstanding
    .filter((l) => rows[l.id].quantity && toMilli(rows[l.id].quantity) > 0n)
    .map((l) => ({
      purchaseOrderItemId: l.id,
      productId: l.productId,
      quantity: rows[l.id].quantity,
      unitCost: rows[l.id].unitCost,
      batchNumber: rows[l.id].batchNumber || null,
      expiryDate: rows[l.id].expiryDate || null,
    }));
  const missingExpiry = outstanding.some((l) => l.trackExpiry && rows[l.id].quantity && !rows[l.id].expiryDate);
  const receive = useMutation({
    mutationFn: () => purchasingApi.receive(po.id, { supplierId: po.supplierId, supplierInvoiceNo: invoice || null, items }, key),
    onSuccess: (grn) => {
      toast.success(`${grn.grnNumber} received — stock updated`);
      queryClient.invalidateQueries({ queryKey: ["purchases"] });
      onDone();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const set = (id, k) => (e) => setRows({ ...rows, [id]: { ...rows[id], [k]: e.target.value } });

  return (
    <Card className="mb-6 border-primary-300 ring-2 ring-primary/10">
      <CardHeader
        title="Receive delivery"
        description="Enter what actually arrived. Stock, batches and moving-average cost update when you post."
        actions={
          <Field label="Supplier invoice no." className="w-48">
            {(a) => <Input {...a} value={invoice} onChange={(e) => setInvoice(e.target.value)} />}
          </Field>
        }
      />
      <Table>
        <thead>
          <tr>
            <Th>Product</Th>
            <Th align="right">Outstanding</Th>
            <Th>Received qty</Th>
            <Th>Unit cost</Th>
            <Th>Batch</Th>
            <Th>Expiry</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {outstanding.map((l) => (
            <tr key={l.id}>
              <Td className="font-medium text-slate-900">{l.name}</Td>
              <Td align="right">{formatQuantity(l.outstanding)}</Td>
              <Td>
                <Input className="w-24" inputMode="decimal" value={rows[l.id].quantity} onChange={set(l.id, "quantity")} aria-label={`Received ${l.name}`} />
              </Td>
              <Td>
                <Input className="w-28" inputMode="decimal" value={rows[l.id].unitCost} onChange={set(l.id, "unitCost")} aria-label={`Cost of ${l.name}`} />
              </Td>
              <Td>
                <Input className="w-28" value={rows[l.id].batchNumber} onChange={set(l.id, "batchNumber")} disabled={!l.trackBatches} placeholder={l.trackBatches ? "" : "n/a"} aria-label={`Batch of ${l.name}`} />
              </Td>
              <Td>
                <Input
                  type="date"
                  className="w-40"
                  value={rows[l.id].expiryDate}
                  onChange={set(l.id, "expiryDate")}
                  disabled={!l.trackExpiry}
                  aria-invalid={(l.trackExpiry && rows[l.id].quantity && !rows[l.id].expiryDate) || undefined}
                  aria-label={`Expiry of ${l.name}`}
                />
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
      <div className="flex items-center justify-between border-t border-slate-200 px-5 py-3">
        {missingExpiry ? <p className="text-xs text-red-600">Expiry date is required for date-tracked products.</p> : <span />}
        <div className="flex gap-2">
          <Button variant="outline" onClick={onDone}>
            Cancel
          </Button>
          <Button loading={receive.isPending} disabled={!items.length || missingExpiry} onClick={() => receive.mutate()}>
            <PackagePlus className="h-4 w-4" aria-hidden="true" /> Post goods receipt
          </Button>
        </div>
      </div>
    </Card>
  );
}

export default function PurchaseOrderPage({ params }) {
  const { id } = use(params);
  const poId = Number(id);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState(null); // edit | receive
  const [confirm, setConfirm] = useState(null); // cancel | close
  const po = useQuery({ queryKey: ["purchases", poId], queryFn: () => purchasingApi.order(poId) });
  const act = useMutation({
    mutationFn: (fn) => fn(),
    onSuccess: (data) => {
      queryClient.setQueryData(["purchases", poId], data);
      queryClient.invalidateQueries({ queryKey: ["purchases"] });
      setMode(null);
      setConfirm(null);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (po.isError) return <ErrorState error={po.error} onRetry={po.refetch} />;
  const p = po.data;
  if (!p) return <Skeleton className="h-96" />;
  const receivable = p.status === "ORDERED" || p.status === "PARTIALLY_RECEIVED";

  return (
    <div>
      <Link href="/purchases" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Purchase orders
      </Link>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span className="font-mono">{p.poNumber}</span>
            <Badge color={PO_STATUS[p.status][1]}>{PO_STATUS[p.status][0]}</Badge>
          </span>
        }
        description={`${p.supplierName} · created ${formatDateTime(p.createdAt)} by ${p.createdBy}${p.approvedBy ? ` · approved by ${p.approvedBy}` : ""}`}
        actions={
          <>
            {p.status === "DRAFT" && (
              <>
                <Button variant="outline" onClick={() => setMode(mode === "edit" ? null : "edit")}>
                  <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
                </Button>
                <Button loading={act.isPending} onClick={() => act.mutate(() => purchasingApi.submit(poId))}>
                  <Send className="h-4 w-4" aria-hidden="true" /> Submit order
                </Button>
              </>
            )}
            {receivable && (
              <Button onClick={() => setMode("receive")} disabled={mode === "receive"}>
                <PackagePlus className="h-4 w-4" aria-hidden="true" /> Receive
              </Button>
            )}
            {p.status === "PARTIALLY_RECEIVED" && (
              <Button variant="outline" onClick={() => setConfirm("close")}>
                <Lock className="h-4 w-4" aria-hidden="true" /> Close
              </Button>
            )}
            {(p.status === "DRAFT" || p.status === "ORDERED") && (
              <Button variant="ghost" onClick={() => setConfirm("cancel")}>
                <Ban className="h-4 w-4" aria-hidden="true" /> Cancel
              </Button>
            )}
          </>
        }
      />

      {mode === "edit" && p.status === "DRAFT" ? (
        <PoEditor
          initial={p}
          saving={act.isPending}
          onSubmit={(body) => act.mutate(() => purchasingApi.updateOrder(poId, body))}
          error={act.isError ? errorMessage(act.error) : null}
        />
      ) : (
        <>
          {mode === "receive" && receivable && <ReceiveForm po={p} onDone={() => setMode(null)} />}
          {p.notes && <Alert className="mb-4">{p.notes}</Alert>}
          <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
            <Card>
              <CardHeader title="Lines" description={p.expectedDate ? `Expected ${formatDate(p.expectedDate)}` : undefined} />
              <Table>
                <thead>
                  <tr>
                    <Th>Product</Th>
                    <Th align="right">Ordered</Th>
                    <Th align="right">Received</Th>
                    <Th align="right">Outstanding</Th>
                    <Th align="right">Unit cost</Th>
                    <Th align="right">Total</Th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {p.items.map((l) => (
                    <tr key={l.id}>
                      <Td>
                        <p className="font-medium text-slate-900">{l.name}</p>
                        <p className="text-xs text-slate-500">{l.sku}</p>
                      </Td>
                      <Td align="right">{formatQuantity(l.quantityOrdered)}</Td>
                      <Td align="right">{formatQuantity(l.quantityReceived)}</Td>
                      <Td align="right">{formatQuantity(l.outstanding)}</Td>
                      <Td align="right">{formatMoney(l.expectedUnitCost, { decimals: 2 })}</Td>
                      <Td align="right">{formatMoney(l.lineTotal)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              <div className="flex justify-end border-t border-slate-200 px-5 py-3 text-sm">
                Total <span className="ml-3 font-bold tabular">{formatMoney(p.expectedTotal)}</span>
              </div>
            </Card>
            <Card>
              <CardHeader title="Goods receipts" />
              {p.receipts.length === 0 ? (
                <p className="px-5 py-6 text-sm text-slate-500">Nothing received yet.</p>
              ) : (
                <ul className="divide-y divide-slate-100 text-sm">
                  {p.receipts.map((g) => (
                    <li key={g.id} className="px-5 py-2.5">
                      <div className="flex justify-between">
                        <span className="font-mono">{g.grnNumber}</span>
                        <span className="tabular">{formatMoney(g.totalCost)}</span>
                      </div>
                      <p className="text-xs text-slate-500">
                        {formatDateTime(g.receivedAt)}
                        {g.supplierInvoiceNo ? ` · inv ${g.supplierInvoiceNo}` : ""}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={confirm === "cancel" ? "Cancel this purchase order?" : "Close this purchase order?"}
        description={
          confirm === "cancel"
            ? "The supplier order is withdrawn. Nothing has been received against it."
            : "Outstanding quantities will no longer be expected. Received stock stays."
        }
        confirmLabel={confirm === "cancel" ? "Cancel order" : "Close order"}
        loading={act.isPending}
        onConfirm={() => act.mutate(() => (confirm === "cancel" ? purchasingApi.cancel(poId) : purchasingApi.close(poId)))}
      />
    </div>
  );
}
