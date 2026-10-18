"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { Card, CardHeader, Table, Td, Th } from "@/components/ui/layout";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { ProductPicker } from "@/features/catalog/product-picker";
import { formatMoney, formatQuantity } from "@/lib/format";
import { centsToString, parseQuantity, toMilli } from "@/lib/money";
import { purchasingApi } from "./api";

const COST = /^\d{1,11}(\.\d{1,4})?$/;

/** qty (3 dp) × cost (4 dp) → cents, rounded half-up, for the on-screen estimate only. */
function lineCents(qty, cost) {
  if (!COST.test(cost || "") || !qty) return 0n;
  const [i, f = ""] = cost.split(".");
  const costMilliCents = BigInt(i) * 10000n + BigInt((f + "0000").slice(0, 4)); // 1/10000 units
  const product = costMilliCents * toMilli(qty); // 1/10000 × 1/1000
  return (product + 50_000n) / 100_000n; // → cents
}

/**
 * Purchase order form. `initial` = existing PoDto (draft edit) or null; `onSubmit(body)` saves.
 */
export function PoEditor({ initial, onSubmit, saving, error }) {
  const suppliers = useQuery({ queryKey: ["suppliers", "active"], queryFn: () => purchasingApi.suppliers({ active: true, size: 100 }) });
  const [supplierId, setSupplierId] = useState(initial?.supplierId ?? "");
  const [expectedDate, setExpectedDate] = useState(initial?.expectedDate ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [lines, setLines] = useState(
    () =>
      initial?.items.map((l) => ({
        productId: l.productId,
        name: l.name,
        sku: l.sku,
        fractional: ["KG", "G", "L", "ML"].includes(l.unitOfMeasure),
        quantity: formatQuantity(l.quantityOrdered),
        unitCost: formatQuantity(l.expectedUnitCost),
      })) ?? [],
  );

  const add = (p) => {
    if (lines.some((l) => l.productId === p.id)) return;
    setLines([...lines, { productId: p.id, name: p.name, sku: p.sku, fractional: p.fractionalQuantity, quantity: "1", unitCost: "" }]);
  };
  const patch = (i, v) => setLines(lines.map((l, j) => (j === i ? { ...l, ...v } : l)));
  const invalid = lines.some((l) => !parseQuantity(l.quantity, l.fractional) || !COST.test(l.unitCost));
  const total = lines.reduce((a, l) => a + lineCents(l.quantity, l.unitCost), 0n);

  return (
    <div className="space-y-6">
      {error && <Alert tone="danger">{error}</Alert>}
      <Card className="grid gap-4 p-5 md:grid-cols-3">
        <Field label="Supplier" required>
          {(a) => (
            <Select {...a} value={supplierId} onChange={(e) => setSupplierId(Number(e.target.value) || "")}>
              <option value="">Choose…</option>
              {(suppliers.data?.content || []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.code})
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Expected delivery">{(a) => <Input {...a} type="date" value={expectedDate || ""} onChange={(e) => setExpectedDate(e.target.value)} />}</Field>
        <Field label="Notes">{(a) => <Textarea {...a} rows={1} value={notes || ""} onChange={(e) => setNotes(e.target.value)} />}</Field>
      </Card>
      <Card>
        <CardHeader title="Items" actions={<ProductPicker className="w-80" onPick={add} placeholder="Add product…" />} />
        {lines.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-slate-500">Search above to add products.</p>
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Product</Th>
                <Th>Quantity</Th>
                <Th>Unit cost</Th>
                <Th align="right">Line total</Th>
                <Th />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {lines.map((l, i) => (
                <tr key={l.productId}>
                  <Td>
                    <p className="font-medium text-slate-900">{l.name}</p>
                    <p className="text-xs text-slate-500">{l.sku}</p>
                  </Td>
                  <Td>
                    <Input
                      className="w-24"
                      inputMode="decimal"
                      value={l.quantity}
                      aria-invalid={!parseQuantity(l.quantity, l.fractional) || undefined}
                      onChange={(e) => patch(i, { quantity: e.target.value.trim() })}
                      aria-label={`Quantity of ${l.name}`}
                    />
                  </Td>
                  <Td>
                    <Input
                      className="w-28"
                      inputMode="decimal"
                      value={l.unitCost}
                      aria-invalid={(l.unitCost && !COST.test(l.unitCost)) || undefined}
                      onChange={(e) => patch(i, { unitCost: e.target.value.trim() })}
                      aria-label={`Unit cost of ${l.name}`}
                    />
                  </Td>
                  <Td align="right">{formatMoney(centsToString(lineCents(l.quantity, l.unitCost)))}</Td>
                  <Td>
                    <Button size="icon" variant="ghost" aria-label={`Remove ${l.name}`} onClick={() => setLines(lines.filter((_, j) => j !== i))}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
        <div className="flex items-center justify-between border-t border-slate-200 px-5 py-3">
          <p className="text-sm text-slate-600">
            Estimated total <span className="ml-2 text-lg font-bold tabular text-slate-900">{formatMoney(centsToString(total))}</span>
          </p>
          <Button
            loading={saving}
            disabled={!supplierId || !lines.length || invalid}
            onClick={() =>
              onSubmit({
                supplierId,
                expectedDate: expectedDate || null,
                notes: notes || null,
                items: lines.map((l) => ({ productId: l.productId, quantity: l.quantity, unitCost: l.unitCost })),
                version: initial?.version ?? null,
              })
            }
          >
            Save draft
          </Button>
        </div>
      </Card>
    </div>
  );
}
