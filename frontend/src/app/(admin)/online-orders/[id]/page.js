"use client";

import { use, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowLeft,
  Ban,
  CheckCircle2,
  Hourglass,
  MapPin,
  PackageCheck,
  Phone,
  PlayCircle,
  RefreshCcw,
  RotateCcw,
  ScanLine,
  ThumbsUp,
  XCircle,
} from "lucide-react";
import { Card, CardHeader, PageHeader } from "@/components/ui/layout";
import { Alert, Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/form";
import { ConfirmDialog, Modal } from "@/components/ui/dialog";
import { ProductPicker } from "@/features/catalog/product-picker";
import { fulfilmentApi, fulfilmentKeys } from "@/features/online-orders/api";
import { STATUS_COLOR, STATUS_LABEL } from "@/features/shop/api";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/lib/api-error";
import { formatDateTime, formatMoney, formatQuantity, formatTime } from "@/lib/format";
import { parseQuantity, toMilli } from "@/lib/money";

const WEIGHED = new Set(["KG", "G", "L", "ML"]);

const PICK = {
  PENDING: { label: "To pick", color: "gray", icon: Hourglass },
  PICKED: { label: "Picked", color: "green", icon: CheckCircle2 },
  SUBSTITUTE_PROPOSED: { label: "Awaiting customer", color: "secondary", icon: RefreshCcw },
  SUBSTITUTED: { label: "Substituted", color: "primary", icon: RefreshCcw },
  UNAVAILABLE: { label: "Unavailable", color: "red", icon: Ban },
};

function QuantityDialog({ line, onClose, onSubmit, pending }) {
  const [value, setValue] = useState(line ? formatQuantity(line.quantity) : "");
  const qty = line ? parseQuantity(value, WEIGHED.has(line.unitOfMeasure)) : null;
  return (
    <Modal
      open={Boolean(line)}
      onOpenChange={(o) => !o && onClose()}
      title={`Pack ${line?.name ?? ""}`}
      description={line ? `Ordered ${formatQuantity(line.quantity)}. Packing less charges the customer less.` : ""}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={pending} disabled={!qty} onClick={() => onSubmit(qty)}>
            Pack {qty ?? ""}
          </Button>
        </>
      }
    >
      <Field label="Quantity packed" error={value && !qty ? "Enter a valid quantity" : null}>
        {(a11y) => <Input {...a11y} autoFocus inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value)} />}
      </Field>
    </Modal>
  );
}

function SubstituteDialog({ line, onClose, onSubmit, pending }) {
  const [product, setProduct] = useState(null);
  const [qty, setQty] = useState("");
  const quantity = product ? parseQuantity(qty, product.fractionalQuantity) : null;
  const close = () => {
    setProduct(null);
    setQty("");
    onClose();
  };
  return (
    <Modal
      open={Boolean(line)}
      onOpenChange={(o) => !o && close()}
      title="Offer a substitute"
      description={line ? `For ${formatQuantity(line.quantity)} × ${line.name} (Rs ${formatMoney(line.lineTotal)})` : ""}
      footer={
        <>
          <Button variant="outline" onClick={close}>
            Cancel
          </Button>
          <Button
            variant="secondary"
            loading={pending}
            disabled={!product || !quantity}
            onClick={() => onSubmit({ productId: product.id, quantity }, close)}
          >
            Send to customer
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Alert tone="info">
          The customer approves it from their phone. They are charged the lower of the substitute&rsquo;s price and the
          original line ({line ? `Rs ${formatMoney(line.lineTotal)}` : ""}) — never more.
        </Alert>
        <ProductPicker
          autoFocus
          onPick={(p) => {
            setProduct(p);
            setQty(formatQuantity(line.quantity));
          }}
        />
        {product && (
          <div className="flex items-end gap-3 rounded-md border border-slate-200 p-3">
            <div className="flex-1">
              <p className="text-sm font-medium text-slate-900">{product.name}</p>
              <p className="text-xs text-slate-500">
                Rs {formatMoney(product.sellingPrice)} · {product.sku} · {formatQuantity(product.quantityOnHand)} in stock
              </p>
            </div>
            <Field label="Qty" className="w-24">
              {(a11y) => <Input {...a11y} inputMode="decimal" value={qty} onChange={(e) => setQty(e.target.value)} />}
            </Field>
          </div>
        )}
      </div>
    </Modal>
  );
}

export default function PackingPage({ params }) {
  const { id } = use(params);
  const orderId = Number(id);
  const queryClient = useQueryClient();
  const [qtyLine, setQtyLine] = useState(null);
  const [subLine, setSubLine] = useState(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [scan, setScan] = useState("");
  const scanRef = useRef(null);

  const order = useQuery({
    queryKey: fulfilmentKeys.order(orderId),
    queryFn: () => fulfilmentApi.get(orderId),
    // Picks up the customer's substitution answers and check-in while the packer works.
    refetchInterval: 5000,
  });

  const apply = (data) => {
    queryClient.setQueryData(fulfilmentKeys.order(orderId), data);
    queryClient.invalidateQueries({ queryKey: ["online-orders", "board"] });
    queryClient.invalidateQueries({ queryKey: fulfilmentKeys.summary });
  };
  const act = useMutation({
    mutationFn: ({ fn }) => fn(),
    onSuccess: (data, v) => {
      apply(data);
      if (v.success) toast.success(v.success);
      v.after?.();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const run = (fn, success, after) => act.mutate({ fn, success, after });

  if (order.isError) return <ErrorState error={order.error} onRetry={order.refetch} />;
  const o = order.data;
  if (!o) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  const packing = o.status === "PACKING";
  const allResolved = o.progress.resolved === o.progress.total && o.progress.pendingSubstitutions === 0;
  const open = ["PLACED", "CONFIRMED", "PACKING", "READY_FOR_PICKUP"].includes(o.status);

  const onScan = (e) => {
    e.preventDefault();
    const code = scan.trim();
    setScan("");
    if (!code) return;
    const line = o.lines.find((l) => l.pickStatus === "PENDING" && (l.barcode === code || l.sku === code));
    if (!line) {
      const done = o.lines.find((l) => l.barcode === code || l.sku === code);
      toast.error(done ? `${done.name} is already ${PICK[done.pickStatus].label.toLowerCase()}.` : "That item is not in this order.");
      return;
    }
    if (WEIGHED.has(line.unitOfMeasure)) setQtyLine(line);
    else run(() => fulfilmentApi.pick(orderId, line.id), `Picked ${line.name}`);
    scanRef.current?.focus();
  };

  return (
    <div>
      <Link href="/online-orders" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Online orders
      </Link>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span className="font-mono">#{o.orderNumber}</span>
            <Badge color={STATUS_COLOR[o.status]}>{STATUS_LABEL[o.status]}</Badge>
          </span>
        }
        description={`Placed ${formatDateTime(o.placedAt)} · ${o.lines.length} lines · Rs ${formatMoney(o.payableTotal)} to collect`}
        actions={
          <>
            {o.status === "PLACED" && (
              <Button loading={act.isPending} onClick={() => run(() => fulfilmentApi.confirm(orderId), "Order confirmed — customer notified")}>
                <ThumbsUp className="h-4 w-4" aria-hidden="true" /> Confirm
              </Button>
            )}
            {(o.status === "PLACED" || o.status === "CONFIRMED") && (
              <Button
                variant={o.status === "CONFIRMED" ? "primary" : "outline"}
                loading={act.isPending}
                onClick={() => run(() => fulfilmentApi.startPacking(orderId), "Packing started — customer notified", () => scanRef.current?.focus())}
              >
                <PlayCircle className="h-4 w-4" aria-hidden="true" /> Start packing
              </Button>
            )}
            {packing && (
              <Button
                variant="success"
                loading={act.isPending}
                disabled={!allResolved}
                title={allResolved ? undefined : "Resolve every line first"}
                onClick={() => run(() => fulfilmentApi.ready(orderId), "Ready for pickup — customer notified")}
              >
                <PackageCheck className="h-4 w-4" aria-hidden="true" /> Mark ready
              </Button>
            )}
            {open && (
              <Button variant="ghost" onClick={() => setCancelOpen(true)}>
                <XCircle className="h-4 w-4" aria-hidden="true" /> Cancel
              </Button>
            )}
          </>
        }
      />

      {o.arrivedAt && o.status === "READY_FOR_PICKUP" && (
        <Alert tone="success" title={`${o.customerName} is at the store (since ${formatTime(o.arrivedAt)})`} className="mb-4">
          Bring the bags to the counter. The cashier collects it from the POS with the customer&rsquo;s 6-digit pickup code.
        </Alert>
      )}
      {o.status === "READY_FOR_PICKUP" && !o.arrivedAt && (
        <Alert tone="info" className="mb-4">
          Waiting for the customer{o.collectBy ? ` (held until ${formatDateTime(o.collectBy)})` : ""}. Payment and handover
          happen at the POS: <strong>Online pickup</strong>, then the customer&rsquo;s code.
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader
            title="Pick list"
            description={`${o.progress.resolved} of ${o.progress.total} resolved${
              o.progress.pendingSubstitutions ? ` · ${o.progress.pendingSubstitutions} waiting for the customer` : ""
            }`}
            actions={
              packing && (
                <form onSubmit={onScan} className="relative w-64">
                  <ScanLine className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                  <Input
                    ref={scanRef}
                    autoFocus
                    className="pl-9"
                    placeholder="Scan item to pick"
                    aria-label="Scan item barcode to pick"
                    value={scan}
                    onChange={(e) => setScan(e.target.value)}
                  />
                </form>
              )
            }
          />
          <div className="h-1.5 bg-slate-100">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${o.progress.total ? (o.progress.resolved / o.progress.total) * 100 : 0}%` }}
            />
          </div>
          <ul className="divide-y divide-slate-100">
            {o.lines.map((l) => {
              const meta = PICK[l.pickStatus];
              return (
                <li key={l.id} className={cn("flex flex-wrap items-center gap-4 px-5 py-3", l.pickStatus === "PENDING" && packing && "bg-white", l.pickStatus !== "PENDING" && "bg-slate-50/60")}>
                  <span className="w-6 text-center text-sm font-semibold text-slate-400">{l.lineNumber}</span>
                  <div className="min-w-48 flex-1">
                    <p className={cn("font-medium", l.pickStatus === "UNAVAILABLE" ? "text-slate-400 line-through" : "text-slate-900")}>{l.name}</p>
                    <p className="text-xs text-slate-500">
                      {l.sku} {l.barcode ? `· ${l.barcode}` : ""} · Rs {formatMoney(l.unitPrice)}
                      {l.promotionName ? ` · ${l.promotionName}` : ""}
                    </p>
                    {l.substitute && (
                      <p className="mt-0.5 text-xs font-medium text-primary-700">
                        → {l.substitute.name} × {formatQuantity(l.substitute.quantity)} (Rs {formatMoney(l.substitute.lineTotal)})
                      </p>
                    )}
                    {l.preference === "SKIP" && <p className="mt-0.5 text-xs text-amber-700">Customer said: no substitutes</p>}
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold tabular text-slate-900">× {formatQuantity(l.quantity)}</p>
                    {l.pickedQuantity && l.pickStatus === "PICKED" && toMilli(l.pickedQuantity) !== toMilli(l.quantity) && (
                      <p className="text-xs text-amber-700">packed {formatQuantity(l.pickedQuantity)}</p>
                    )}
                  </div>
                  <Badge color={meta.color} className="w-36 justify-center">
                    <meta.icon className="mr-1 h-3 w-3" aria-hidden="true" /> {meta.label}
                  </Badge>
                  {packing && (
                    <div className="flex gap-1">
                      {l.pickStatus === "PENDING" ? (
                        <>
                          <Button
                            size="sm"
                            variant="success"
                            onClick={() =>
                              WEIGHED.has(l.unitOfMeasure) ? setQtyLine(l) : run(() => fulfilmentApi.pick(orderId, l.id), `Picked ${l.name}`)
                            }
                          >
                            Pick
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => setQtyLine(l)} title="Pack a smaller quantity">
                            Part
                          </Button>
                          {l.preference === "SUBSTITUTE" && (
                            <Button size="sm" variant="secondary" onClick={() => setSubLine(l)}>
                              Swap
                            </Button>
                          )}
                          <Button size="sm" variant="ghost" onClick={() => run(() => fulfilmentApi.unavailable(orderId, l.id), `${l.name} marked unavailable`)}>
                            None
                          </Button>
                        </>
                      ) : (
                        <Button size="sm" variant="ghost" onClick={() => run(() => fulfilmentApi.reset(orderId, l.id))}>
                          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" /> Undo
                        </Button>
                      )}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </Card>

        <div className="space-y-4">
          <Card className="p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Customer</p>
            <p className="mt-1 font-semibold text-slate-900">{o.customerName}</p>
            {o.customerPhone && (
              <p className="flex items-center gap-1.5 text-sm text-slate-600">
                <Phone className="h-3.5 w-3.5" aria-hidden="true" /> {o.customerPhone}
              </p>
            )}
            <p className="mt-3 flex items-center gap-1.5 text-sm text-slate-600">
              <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
              {o.pickupAfter ? `Pickup from ${formatDateTime(o.pickupAfter)}` : "Pickup as soon as ready"}
            </p>
            {o.customerNote && <p className="mt-3 rounded-md bg-amber-50 p-3 text-sm text-amber-900">&ldquo;{o.customerNote}&rdquo;</p>}
          </Card>
          <Card className="p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Amounts</p>
            <dl className="mt-2 space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Ordered</dt>
                <dd className="tabular">Rs {formatMoney(o.grandTotal)}</dd>
              </div>
              {o.finalTotal != null && (
                <div className="flex justify-between font-semibold">
                  <dt>Final (packed)</dt>
                  <dd className="tabular">Rs {formatMoney(o.finalTotal)}</dd>
                </div>
              )}
            </dl>
          </Card>
          <Card className="p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Activity</p>
            <ol className="mt-3 space-y-2.5">
              {[...(o.events || [])].reverse().map((e, i) => (
                <li key={`${e.at}-${i}`} className="text-sm">
                  <p className="text-slate-800">{e.message}</p>
                  <p className="text-xs text-slate-400">
                    {formatTime(e.at)} · {e.actorType === "CUSTOMER" ? "customer" : e.actorType === "STAFF" ? "staff" : "system"}
                  </p>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>

      <QuantityDialog
        key={qtyLine?.id ?? "none"}
        line={qtyLine}
        pending={act.isPending}
        onClose={() => setQtyLine(null)}
        onSubmit={(qty) => run(() => fulfilmentApi.pick(orderId, qtyLine.id, qty), `Packed ${qty} × ${qtyLine.name}`, () => setQtyLine(null))}
      />
      <SubstituteDialog
        line={subLine}
        pending={act.isPending}
        onClose={() => setSubLine(null)}
        onSubmit={(body, close) =>
          run(() => fulfilmentApi.substitute(orderId, subLine.id, body), "Substitute sent — waiting for the customer", close)
        }
      />
      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title={`Cancel order #${o.orderNumber}?`}
        description="Packed stock goes back to inventory and the customer is notified with your reason."
        confirmLabel="Cancel order"
        loading={act.isPending}
        onConfirm={() =>
          reason.trim() &&
          run(() => fulfilmentApi.cancel(orderId, reason.trim()), "Order cancelled", () => {
            setCancelOpen(false);
            setReason("");
          })
        }
      >
        <Field label="Reason (sent to the customer)" required>
          {(a11y) => <Textarea {...a11y} value={reason} onChange={(e) => setReason(e.target.value)} />}
        </Field>
      </ConfirmDialog>
    </div>
  );
}
