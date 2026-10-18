"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Globe, MapPin, PackageCheck } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/form";
import { Alert, Badge } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api-error";
import { formatMoney, formatQuantity, formatTime } from "@/lib/format";
import { newIdempotencyKey } from "@/lib/idempotency";
import { fulfilmentApi } from "@/features/online-orders/api";
import { PaymentDialog } from "./payment-dialog";

/**
 * Hand over a click & collect order: the customer gives their 6-digit pickup code, the cashier
 * checks the bags against the packed list, takes payment for the final (packed) total, and the
 * order becomes a normal sale on this shift with a receipt. Stock was already deducted at packing.
 */
export function OnlinePickupDialog({ registerId, onClose, onCollected }) {
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const [order, setOrder] = useState(null);
  const [paying, setPaying] = useState(false);
  const [payKey, setPayKey] = useState(null);
  const [payError, setPayError] = useState(null);

  const find = useMutation({
    mutationFn: () => fulfilmentApi.findByCode(code),
    onSuccess: setOrder,
  });

  const collect = useMutation({
    mutationFn: (payments) =>
      fulfilmentApi.collect(order.id, { registerId, pickupCode: code, payments, expectedTotal: order.payableTotal }, payKey),
    onSuccess: (sale) => {
      queryClient.invalidateQueries({ queryKey: ["online-orders"] });
      setPaying(false);
      onCollected(sale);
    },
    onError: (e) => {
      if (e.status === 0) {
        setPayError("Connection problem. Press Complete sale again: the retry cannot charge twice.");
      } else {
        setPayKey(newIdempotencyKey());
        setPayError(errorMessage(e));
      }
    },
  });

  const lines = order?.lines.filter((l) => l.pickStatus !== "UNAVAILABLE") || [];
  const missing = order?.lines.filter((l) => l.pickStatus === "UNAVAILABLE") || [];

  return (
    <>
      <Modal
        open={!paying}
        onOpenChange={(o) => !o && onClose()}
        title="Online order pickup"
        description="Ask the customer for the 6-digit pickup code from their app."
        size="lg"
        footer={
          order ? (
            <>
              <Button variant="outline" onClick={() => setOrder(null)}>
                Different code
              </Button>
              <Button
                variant="success"
                onClick={() => {
                  setPayError(null);
                  setPayKey(newIdempotencyKey());
                  setPaying(true);
                }}
              >
                Take payment · Rs {formatMoney(order.payableTotal)}
              </Button>
            </>
          ) : (
            <Button loading={find.isPending} disabled={!/^\d{6}$/.test(code)} onClick={() => find.mutate()}>
              Find order
            </Button>
          )
        }
      >
        {!order ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (/^\d{6}$/.test(code)) find.mutate();
            }}
            className="space-y-3"
          >
            {find.isError && <Alert tone="danger">{errorMessage(find.error)}</Alert>}
            <Field label="Pickup code">
              {(a11y) => (
                <Input
                  {...a11y}
                  autoFocus
                  inputMode="numeric"
                  maxLength={6}
                  className="h-14 text-center font-mono text-3xl tracking-[0.4em]"
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                />
              )}
            </Field>
          </form>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-slate-50 p-3">
              <div>
                <p className="flex items-center gap-2 font-semibold text-slate-900">
                  <Globe className="h-4 w-4 text-primary" aria-hidden="true" /> #{order.orderNumber} · {order.customerName}
                </p>
                <p className="text-sm text-slate-500">{order.customerPhone}</p>
              </div>
              {order.arrivedAt && (
                <Badge color="green">
                  <MapPin className="mr-1 h-3 w-3" aria-hidden="true" /> checked in {formatTime(order.arrivedAt)}
                </Badge>
              )}
            </div>
            <div className="max-h-72 overflow-y-auto rounded-md border border-slate-200">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-3 py-2 text-left">Packed item</th>
                    <th className="px-3 py-2 text-right">Qty</th>
                    <th className="px-3 py-2 text-right">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {lines.map((l) => (
                    <tr key={l.id}>
                      <td className="px-3 py-2">
                        {l.substitute ? (
                          <>
                            {l.substitute.name} <span className="text-xs text-primary-700">(swap for {l.name})</span>
                          </>
                        ) : (
                          l.name
                        )}
                      </td>
                      <td className="px-3 py-2 text-right tabular">
                        {formatQuantity(l.substitute ? l.substitute.quantity : (l.pickedQuantity ?? l.quantity))}
                      </td>
                      <td className="px-3 py-2 text-right tabular">{formatMoney(l.finalLineTotal ?? l.lineTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {missing.length > 0 && (
              <p className="text-xs text-slate-500">
                Not supplied (not charged): {missing.map((m) => m.name).join(", ")}
              </p>
            )}
            <div className="flex items-baseline justify-between border-t border-slate-200 pt-3">
              <span className="flex items-center gap-2 text-sm text-slate-600">
                <PackageCheck className="h-4 w-4" aria-hidden="true" /> Hand over {lines.length} item{lines.length === 1 ? "" : "s"}
              </span>
              <span className="text-2xl font-bold tabular">Rs {formatMoney(order.payableTotal)}</span>
            </div>
          </div>
        )}
      </Modal>
      {order && (
        <PaymentDialog
          open={paying}
          total={order.payableTotal}
          customer={null}
          paying={collect.isPending}
          error={payError}
          onClose={() => setPaying(false)}
          onPay={(payments) => collect.mutate(payments)}
        />
      )}
    </>
  );
}

