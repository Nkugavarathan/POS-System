"use client";

import { use, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Boxes, Plus, Tag, Trash2 } from "lucide-react";
import { PageHeader, Card, CardHeader, Table, Th, Td } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton, EmptyState } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { ConfirmDialog, Modal } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { ProductForm } from "@/features/catalog/product-form";
import { catalogApi, catalogKeys } from "@/features/catalog/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

function toLocalInput(iso) {
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function PromotionDialog({ product, promotion, onClose }) {
  const queryClient = useQueryClient();
  const now = new Date();
  const [form, setForm] = useState({
    name: promotion?.name ?? "",
    type: promotion?.type ?? "PERCENT",
    value: promotion?.value ?? "",
    startsAt: toLocalInput(promotion?.startsAt ?? now.toISOString()),
    endsAt: toLocalInput(promotion?.endsAt ?? new Date(now.getTime() + 7 * 864e5).toISOString()),
    active: promotion?.active ?? true,
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });
  const save = useMutation({
    mutationFn: () => {
      const body = {
        ...form,
        startsAt: new Date(form.startsAt).toISOString(),
        endsAt: new Date(form.endsAt).toISOString(),
        version: promotion?.version,
      };
      return promotion
        ? catalogApi.updatePromotion(product.id, promotion.id, body)
        : catalogApi.createPromotion(product.id, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.product(product.id) });
      toast.success("Promotion saved.");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={promotion ? "Edit promotion" : "New promotion"}
      description={`${product.name} · regular price ${formatMoney(product.sellingPrice)}`}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!form.name || !form.value}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Name" className="sm:col-span-2" required>
          {(a) => <Input {...a} value={form.name} onChange={set("name")} placeholder="e.g. Weekend deal" />}
        </Field>
        <Field label="Type">
          {(a) => (
            <Select {...a} value={form.type} onChange={set("type")}>
              <option value="PERCENT">Percent off</option>
              <option value="AMOUNT">Amount off per unit</option>
            </Select>
          )}
        </Field>
        <Field label={form.type === "PERCENT" ? "Percent" : "Amount"} required>
          {(a) => <Input {...a} inputMode="decimal" value={form.value} onChange={set("value")} />}
        </Field>
        <Field label="Starts">{(a) => <Input {...a} type="datetime-local" value={form.startsAt} onChange={set("startsAt")} />}</Field>
        <Field label="Ends">{(a) => <Input {...a} type="datetime-local" value={form.endsAt} onChange={set("endsAt")} />}</Field>
        <Checkbox label="Active" checked={form.active} onChange={set("active")} />
      </div>
    </Modal>
  );
}

export default function ProductDetailPage({ params }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const setErrorRef = useRef(null);
  const [barcode, setBarcode] = useState("");
  const [removing, setRemoving] = useState(null);
  const [promo, setPromo] = useState(undefined);
  const canManage = hasPermission(me, "PRODUCT_MANAGE");
  const canPrice = hasPermission(me, "PRICE_MANAGE");

  const { data: product, error, isPending, refetch } = useQuery({
    queryKey: catalogKeys.product(id),
    queryFn: () => catalogApi.product(id),
  });
  const saved = (p) => {
    queryClient.setQueryData(catalogKeys.product(id), p);
    queryClient.invalidateQueries({ queryKey: ["products", "list"] });
  };

  const update = useMutation({
    mutationFn: (v) => catalogApi.updateProduct(id, v),
    onSuccess: (p) => {
      saved(p);
      toast.success("Product saved.");
    },
    onError: (e) => {
      if (e.code === "CONCURRENT_MODIFICATION") {
        toast.error("Someone else changed this product; the latest version was loaded.");
        refetch();
        return;
      }
      if (e.code === "DUPLICATE_RESOURCE" && e.details?.field) {
        setErrorRef.current?.(e.details.field, { type: "server", message: e.message });
        return;
      }
      if (!applyFieldErrors(e, setErrorRef.current || (() => {}))) toast.error(errorMessage(e));
    },
  });
  const status = useMutation({
    mutationFn: () => catalogApi.setProductStatus(id, { active: !product.active, version: product.version }),
    onSuccess: (p) => {
      saved(p);
      toast.success(p.active ? "Product activated." : "Product deactivated; it can no longer be sold.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const addBarcode = useMutation({
    mutationFn: () => catalogApi.addBarcode(id, { barcode, primary: false }),
    onSuccess: (p) => {
      saved(p);
      setBarcode("");
      toast.success("Barcode added.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const removeBarcode = useMutation({
    mutationFn: (barcodeId) => catalogApi.removeBarcode(id, barcodeId),
    onSuccess: (p) => {
      saved(p);
      setRemoving(null);
      toast.success("Barcode removed.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <AuthGate anyOf={["PRODUCT_VIEW"]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <Link href="/products" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Products
        </Link>
        {isPending ? (
          <Skeleton className="h-96" />
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <>
            <PageHeader
              title={product.name}
              description={
                <span className="flex flex-wrap items-center gap-2">
                  <span className="font-mono">{product.sku}</span>
                  {product.active ? <Badge color="green">active</Badge> : <Badge color="red">inactive</Badge>}
                  <span>· {product.category.name}</span>
                </span>
              }
              actions={
                <>
                  {hasPermission(me, "INVENTORY_VIEW") && (
                    <Link href={`/inventory/${product.id}`}>
                      <Button variant="outline">
                        <Boxes className="h-4 w-4" aria-hidden="true" /> Stock & batches
                      </Button>
                    </Link>
                  )}
                  {canManage && (
                    <Button variant={product.active ? "danger" : "success"} onClick={() => status.mutate()} loading={status.isPending}>
                      {product.active ? "Deactivate" : "Activate"}
                    </Button>
                  )}
                </>
              }
            />

            <div className="grid gap-4 sm:grid-cols-3">
              <Card className="p-4">
                <p className="text-xs text-slate-500">Selling price</p>
                <p className="mt-1 text-2xl font-semibold tabular">{formatMoney(product.sellingPrice)}</p>
                <p className="text-xs text-slate-500">
                  {product.pricesIncludeTax ? "incl." : "excl."} {product.taxRate.code} ({product.taxRate.ratePercent}%)
                </p>
              </Card>
              <Card className="p-4">
                <p className="text-xs text-slate-500">In stock (this store)</p>
                <p className={`mt-1 text-2xl font-semibold tabular ${product.stock.lowStock ? "text-red-600" : ""}`}>
                  {formatQuantity(product.stock.quantityOnHand)} <span className="text-sm font-normal">{product.unitOfMeasure.toLowerCase()}</span>
                </p>
                <p className="text-xs text-slate-500">Reorder at {formatQuantity(product.reorderLevel)}</p>
              </Card>
              <Card className="p-4">
                <p className="text-xs text-slate-500">Cost price</p>
                <p className="mt-1 text-2xl font-semibold tabular">{product.costPrice != null ? formatMoney(product.costPrice) : "-"}</p>
                <p className="text-xs text-slate-500">Updated {formatDateTime(product.updatedAt)}</p>
              </Card>
            </div>

            <Card>
              <CardHeader title="Details" />
              <div className="p-5">
                {canManage ? (
                  <ProductForm
                    key={product.version}
                    product={product}
                    onSubmit={(v) => update.mutate(v)}
                    submitting={update.isPending}
                    canEditPrices={canPrice}
                    setErrorRef={setErrorRef}
                  />
                ) : (
                  <p className="text-sm text-slate-600">{product.description || "No description."}</p>
                )}
              </div>
            </Card>

            <div className="grid gap-6 lg:grid-cols-2">
              <Card>
                <CardHeader title="Barcodes" description="Scanning any of these finds the product." />
                <ul className="divide-y divide-slate-100">
                  {product.barcodes.map((b) => (
                    <li key={b.id} className="flex items-center justify-between px-5 py-2.5">
                      <span className="font-mono text-sm">
                        {b.barcode} <span className="ml-2 text-xs text-slate-400">{b.type.replace("_", "-")}</span>
                        {b.primary && <Badge color="primary" className="ml-2">primary</Badge>}
                      </span>
                      {canManage && product.barcodes.length > 1 && (
                        <Button size="sm" variant="ghost" onClick={() => setRemoving(b)} aria-label={`Remove ${b.barcode}`}>
                          <Trash2 className="h-4 w-4 text-red-600" />
                        </Button>
                      )}
                    </li>
                  ))}
                  {product.barcodes.length === 0 && <li className="px-5 py-4 text-sm text-slate-500">No barcodes yet.</li>}
                </ul>
                {canManage && (
                  <form
                    className="flex gap-2 border-t border-slate-200 p-4"
                    onSubmit={(e) => {
                      e.preventDefault();
                      if (barcode.trim()) addBarcode.mutate();
                    }}
                  >
                    <Input aria-label="New barcode" placeholder="Add barcode" value={barcode} onChange={(e) => setBarcode(e.target.value)} />
                    <Button type="submit" variant="outline" loading={addBarcode.isPending}>
                      Add
                    </Button>
                  </form>
                )}
              </Card>

              <Card>
                <CardHeader
                  title="Promotions"
                  description="The best running promotion applies automatically at the till and online."
                  actions={
                    canPrice && (
                      <Button size="sm" variant="outline" onClick={() => setPromo(null)}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Add
                      </Button>
                    )
                  }
                />
                {product.promotions.length === 0 ? (
                  <EmptyState icon={Tag} title="No promotions" className="py-8" />
                ) : (
                  <Table>
                    <thead>
                      <tr>
                        <Th>Name</Th>
                        <Th>Discount</Th>
                        <Th>Window</Th>
                        <Th />
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {product.promotions.map((p) => (
                        <tr key={p.id}>
                          <Td>
                            {p.name}{" "}
                            {p.running ? <Badge color="green">running</Badge> : !p.active ? <Badge>off</Badge> : null}
                          </Td>
                          <Td>{p.type === "PERCENT" ? `${p.value}%` : formatMoney(p.value)}</Td>
                          <Td className="text-xs text-slate-500">
                            {formatDateTime(p.startsAt)} - {formatDateTime(p.endsAt)}
                          </Td>
                          <Td align="right">
                            {canPrice && (
                              <Button size="sm" variant="ghost" onClick={() => setPromo(p)}>
                                Edit
                              </Button>
                            )}
                          </Td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                )}
              </Card>
            </div>

            <ConfirmDialog
              open={Boolean(removing)}
              onOpenChange={(o) => !o && setRemoving(null)}
              title={`Remove barcode ${removing?.barcode}?`}
              description="Scanning this code will no longer find the product."
              confirmLabel="Remove"
              loading={removeBarcode.isPending}
              onConfirm={() => removeBarcode.mutate(removing.id)}
            />
            {promo !== undefined && <PromotionDialog product={product} promotion={promo} onClose={() => setPromo(undefined)} />}
          </>
        )}
      </div>
    </AuthGate>
  );
}
