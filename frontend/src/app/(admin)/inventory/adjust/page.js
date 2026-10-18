"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Trash2 } from "lucide-react";
import { PageHeader, Card, Table, Th, Td } from "@/components/ui/layout";
import { Field, Input, Select } from "@/components/ui/form";
import { Alert, EmptyState } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { ProductPicker } from "@/features/catalog/product-picker";
import { inventoryApi } from "@/features/inventory/api";
import { ApprovalDialog } from "@/features/auth/approval-dialog";
import { newIdempotencyKey } from "@/lib/idempotency";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatQuantity } from "@/lib/format";

const TYPES = [
  { value: "OPENING_BALANCE", label: "Opening balance (initial stock)", perm: "INVENTORY_ADJUST", hint: "Positive quantities with unit cost." },
  { value: "ADJUSTMENT", label: "Correction (+ found / − missing)", perm: "INVENTORY_ADJUST", hint: "Signed quantities, e.g. -2 or 5." },
  { value: "WRITE_OFF_DAMAGED", label: "Write-off: damaged", perm: "INVENTORY_WRITE_OFF", hint: "Quantities removed (positive)." },
  { value: "WRITE_OFF_EXPIRED", label: "Write-off: expired", perm: "INVENTORY_WRITE_OFF", hint: "Quantities removed (positive)." },
];

function AdjustContent() {
  const router = useRouter();
  const params = useSearchParams();
  const { data: me } = useMe();
  const allowed = TYPES.filter((t) => hasPermission(me, t.perm));
  const [type, setType] = useState(allowed[0]?.value ?? "ADJUSTMENT");
  const [reason, setReason] = useState("");
  const [lines, setLines] = useState([]);
  const [approval, setApproval] = useState(null);
  // One key per logical submission; a retry after a network error reuses it.
  const [key, setKey] = useState(newIdempotencyKey);
  const preselect = params.get("productId");
  const pre = useQuery({
    queryKey: ["inventory", "detail", preselect],
    queryFn: () => inventoryApi.detail(preselect),
    enabled: Boolean(preselect),
  });
  useEffect(() => {
    if (pre.data && lines.length === 0) {
      const s = pre.data.stock;
      setLines([{ productId: s.productId, name: s.name, sku: s.sku, onHand: s.quantityOnHand, quantity: "", unitCost: "", batchNumber: "", expiryDate: "", trackBatches: s.trackBatches }]);
    }
  }, [pre.data, lines.length]);

  const typeInfo = TYPES.find((t) => t.value === type);
  const isIncoming = type === "OPENING_BALANCE" || (type === "ADJUSTMENT" && lines.some((l) => Number(l.quantity) > 0));

  const submit = useMutation({
    mutationFn: (approvalToken) =>
      inventoryApi.adjust(
        {
          type,
          reason,
          approvalToken: approvalToken || undefined,
          items: lines.map((l) => ({
            productId: l.productId,
            quantity: l.quantity,
            unitCost: l.unitCost || undefined,
            batchNumber: l.batchNumber || undefined,
            expiryDate: l.expiryDate || undefined,
          })),
        },
        key,
      ),
    onSuccess: (a) => {
      toast.success(`${a.adjustmentNumber} posted (${a.items.length} lines).`);
      router.push("/inventory?tab=adjustments");
    },
    onError: (e) => {
      if (e.code === "APPROVAL_REQUIRED") {
        setApproval({ action: e.details?.action || "WRITE_OFF_OVER_LIMIT", value: e.details?.value });
        return;
      }
      // A rejected request will not be replayed; the next attempt is a new logical request.
      if (e.status && e.status < 500) setKey(newIdempotencyKey());
      toast.error(errorMessage(e));
    },
  });

  const valid = useMemo(
    () => reason.trim() && lines.length > 0 && lines.every((l) => l.quantity !== "" && Number(l.quantity) !== 0 && !Number.isNaN(Number(l.quantity))),
    [reason, lines],
  );
  const update = (i, patch) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader title="Adjust stock" description="Posts a document to the stock ledger. It cannot be edited afterwards; post a correction instead." />
      <Card className="space-y-5 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Type" hint={typeInfo?.hint}>
            {(a) => (
              <Select {...a} value={type} onChange={(e) => setType(e.target.value)}>
                {allowed.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Reason" required>
            {(a) => <Input {...a} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder="e.g. Shelf audit aisle 4" />}
          </Field>
        </div>
        <ProductPicker
          onPick={(p) => {
            if (lines.some((l) => l.productId === p.id)) return toast.info(`${p.name} is already listed.`);
            setLines([...lines, { productId: p.id, name: p.name, sku: p.sku, onHand: p.quantityOnHand, quantity: "", unitCost: "", batchNumber: "", expiryDate: "" }]);
          }}
        />
        {lines.length === 0 ? (
          <EmptyState title="Add products to adjust" description="Search by name, SKU or barcode above." className="py-8" />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Product</Th>
                <Th align="right">On hand</Th>
                <Th>Quantity</Th>
                {isIncoming && <Th>Unit cost</Th>}
                {isIncoming && <Th>Batch / expiry</Th>}
                <Th />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {lines.map((l, i) => (
                <tr key={l.productId}>
                  <Td>
                    <span className="font-medium">{l.name}</span>
                    <span className="block font-mono text-xs text-slate-500">{l.sku}</span>
                  </Td>
                  <Td align="right">{formatQuantity(l.onHand)}</Td>
                  <Td>
                    <Input aria-label={`Quantity for ${l.name}`} className="w-28" inputMode="decimal" value={l.quantity} onChange={(e) => update(i, { quantity: e.target.value })} />
                  </Td>
                  {isIncoming && (
                    <Td>
                      <Input aria-label={`Unit cost for ${l.name}`} className="w-28" inputMode="decimal" placeholder="avg" value={l.unitCost} onChange={(e) => update(i, { unitCost: e.target.value })} />
                    </Td>
                  )}
                  {isIncoming && (
                    <Td>
                      <div className="flex gap-2">
                        <Input aria-label="Batch number" className="w-28" placeholder="batch" value={l.batchNumber} onChange={(e) => update(i, { batchNumber: e.target.value })} />
                        <Input aria-label="Expiry date" type="date" className="w-40" value={l.expiryDate} onChange={(e) => update(i, { expiryDate: e.target.value })} />
                      </div>
                    </Td>
                  )}
                  <Td align="right">
                    <Button size="sm" variant="ghost" aria-label={`Remove ${l.name}`} onClick={() => setLines(lines.filter((_, j) => j !== i))}>
                      <Trash2 className="h-4 w-4 text-red-600" />
                    </Button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
        {type.startsWith("WRITE_OFF") && (
          <Alert tone="warning">Write-offs above the configured value need a manager to approve with their PIN.</Alert>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => router.back()}>
            Cancel
          </Button>
          <Button onClick={() => submit.mutate(undefined)} disabled={!valid} loading={submit.isPending}>
            Post adjustment
          </Button>
        </div>
      </Card>
      {approval && (
        <ApprovalDialog
          action={approval.action}
          title="Manager approval: write-off"
          description="This write-off exceeds the value you can post alone."
          onClose={() => setApproval(null)}
          onApproved={(token) => {
            setApproval(null);
            submit.mutate(token);
          }}
          value={approval.value}
        />
      )}
    </div>
  );
}

export default function AdjustPage() {
  return (
    <AuthGate anyOf={["INVENTORY_ADJUST", "INVENTORY_WRITE_OFF"]}>
      <Suspense>
        <AdjustContent />
      </Suspense>
    </AuthGate>
  );
}
