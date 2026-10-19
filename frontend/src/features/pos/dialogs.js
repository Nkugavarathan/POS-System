"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Printer, UserPlus } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { Alert, Badge, EmptyState, Spinner } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Receipt } from "@/features/sales/receipt";
import { downloadReceiptPdf, printReceipt } from "@/features/sales/receipt-output";
import { ApprovalDialog } from "@/features/auth/approval-dialog";
import { newIdempotencyKey } from "@/lib/idempotency";
import { parseAmount, parseQuantity } from "@/lib/money";
import { errorMessage } from "@/lib/api-error";
import { formatDateTime, formatMoney } from "@/lib/format";
import { posApi, posKeys } from "./api";

/** Quantity (weighed items), manual discount and price override for one cart line. */
export function LineEditDialog({ line, mode, onSave, onClose }) {
  const [qty, setQty] = useState(line.quantity);
  const [method, setMethod] = useState(line.manualDiscount?.method ?? "PERCENT");
  const [value, setValue] = useState(line.manualDiscount?.value ?? "");
  const [reason, setReason] = useState(line.manualDiscount?.reason ?? line.priceOverride?.reason ?? "");
  const [price, setPrice] = useState(line.priceOverride?.unitPrice ?? line.displayPrice);
  const inputRef = useRef(null);
  useEffect(() => {
    setTimeout(() => inputRef.current?.select(), 50);
  }, []);

  const title = { quantity: `Quantity · ${line.name}`, discount: `Discount · ${line.name}`, price: `Price override · ${line.name}` }[mode];
  const parsedQty = parseQuantity(qty, line.fractional);
  const parsedValue = parseAmount(value);
  const parsedPrice = parseAmount(price);
  const valid =
    mode === "quantity"
      ? Boolean(parsedQty)
      : mode === "discount"
        ? value === "" || (parsedValue && reason.trim() && (method !== "PERCENT" || Number(parsedValue) <= 100))
        : parsedPrice && reason.trim();

  const save = () => {
    if (!valid) return;
    if (mode === "quantity") onSave({ quantity: parsedQty });
    if (mode === "discount") onSave({ manualDiscount: value === "" ? null : { method, value: parsedValue, reason: reason.trim() } });
    if (mode === "price") onSave({ priceOverride: { unitPrice: parsedPrice, reason: reason.trim() } });
  };

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={title} size="sm"
      footer={
        <>
          {mode === "discount" && line.manualDiscount && (
            <Button variant="ghost" onClick={() => onSave({ manualDiscount: null })}>
              Remove discount
            </Button>
          )}
          {mode === "price" && line.priceOverride && (
            <Button variant="ghost" onClick={() => onSave({ priceOverride: null })}>
              Restore price
            </Button>
          )}
          <Button onClick={save} disabled={!valid}>
            Apply
          </Button>
        </>
      }
    >
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        {mode === "quantity" && (
          <Field label={line.fractional ? `Weight / volume (${line.unitOfMeasure.toLowerCase()})` : "Quantity"} hint={line.fractional ? "Up to 3 decimals, e.g. 0.755" : "Whole number"} error={qty && !parsedQty ? "Invalid quantity" : null}>
            {(a) => <Input {...a} ref={inputRef} inputMode="decimal" className="h-14 text-2xl" value={qty} onChange={(e) => setQty(e.target.value)} />}
          </Field>
        )}
        {mode === "discount" && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Type">
                {(a) => (
                  <Select {...a} value={method} onChange={(e) => setMethod(e.target.value)}>
                    <option value="PERCENT">Percent</option>
                    <option value="AMOUNT">Amount</option>
                  </Select>
                )}
              </Field>
              <Field label={method === "PERCENT" ? "Percent" : "Amount"}>
                {(a) => <Input {...a} ref={inputRef} inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value)} />}
              </Field>
            </div>
            <Field label="Reason" hint="Required for every discount (audited)">
              {(a) => <Input {...a} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder="e.g. Damaged packaging" />}
            </Field>
            <p className="text-xs text-slate-500">Discounts above your limit need a manager&apos;s PIN at payment.</p>
          </>
        )}
        {mode === "price" && (
          <>
            <Field label="New unit price" hint={`Shelf price ${formatMoney(line.displayPrice)}`}>
              {(a) => <Input {...a} ref={inputRef} inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} />}
            </Field>
            <Field label="Reason">
              {(a) => <Input {...a} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder="e.g. Shelf label mismatch" />}
            </Field>
          </>
        )}
        <button type="submit" className="hidden" aria-hidden="true" tabIndex={-1} />
      </form>
    </Modal>
  );
}

/** Attach a customer by loyalty card or phone; optionally register a new one. */
export function CustomerDialog({ onPick, onClose, canCreate }) {
  const [value, setValue] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ fullName: "", phone: "", email: "" });
  const lookup = useMutation({ mutationFn: () => posApi.customerLookup(value.trim()), onSuccess: onPick });
  const create = useMutation({
    mutationFn: () => posApi.createCustomer({ ...form, email: form.email || null, enrolLoyalty: true, marketingConsent: false }),
    onSuccess: (c) =>
      onPick({ id: c.id, fullName: c.fullName, cardNumber: c.loyalty?.cardNumber, balance: 0, balanceValue: "0.00", maskedPhone: c.phone }),
  });
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title="Customer" description="Scan the loyalty card or enter the phone number." size="sm">
      {!creating ? (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (value.trim()) lookup.mutate();
          }}
        >
          {lookup.error && <Alert tone={lookup.error.status === 404 ? "warning" : "danger"}>{lookup.error.status === 404 ? "No customer found." : errorMessage(lookup.error)}</Alert>}
          <Input autoFocus aria-label="Loyalty card or phone" placeholder="LC00000001 or 077 123 4567" value={value} onChange={(e) => setValue(e.target.value)} />
          <div className="flex justify-between">
            {canCreate ? (
              <Button variant="ghost" onClick={() => setCreating(true)}>
                <UserPlus className="h-4 w-4" aria-hidden="true" /> New customer
              </Button>
            ) : (
              <span />
            )}
            <Button type="submit" loading={lookup.isPending}>
              Find
            </Button>
          </div>
        </form>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
        >
          {create.error && <Alert tone="danger">{errorMessage(create.error)}</Alert>}
          <Field label="Full name">{(a) => <Input {...a} autoFocus value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />}</Field>
          <Field label="Phone">{(a) => <Input {...a} inputMode="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />}</Field>
          <Field label="Email (optional)">{(a) => <Input {...a} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />}</Field>
          <p className="text-xs text-slate-500">A loyalty card number is issued automatically.</p>
          <div className="flex justify-between">
            <Button variant="ghost" onClick={() => setCreating(false)}>
              Back
            </Button>
            <Button type="submit" loading={create.isPending} disabled={!form.fullName.trim() || !form.phone.trim()}>
              Create & attach
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

export function HeldCartsDialog({ onResume, onClose }) {
  const queryClient = useQueryClient();
  const held = useQuery({ queryKey: posKeys.held, queryFn: posApi.heldCarts });
  const resume = useMutation({
    mutationFn: posApi.resume,
    onSuccess: (cart) => {
      queryClient.invalidateQueries({ queryKey: posKeys.held });
      onResume(cart);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: posApi.cancelHeld,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: posKeys.held }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title="Held carts" description="Resumed carts are re-priced at today's prices." size="md">
      {held.isPending ? (
        <Spinner />
      ) : held.data?.length === 0 ? (
        <EmptyState title="No held carts" />
      ) : (
        <ul className="divide-y divide-slate-100">
          {held.data?.map((c) => (
            <li key={c.id} className="flex items-center justify-between gap-3 py-3">
              <div>
                <p className="font-medium">{c.label || `Cart #${c.id}`}</p>
                <p className="text-xs text-slate-500">
                  {c.itemCount} lines · {c.heldByName} · {formatDateTime(c.heldAt)}
                </p>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="ghost" onClick={() => cancel.mutate(c.id)}>
                  Discard
                </Button>
                <Button size="sm" onClick={() => resume.mutate(c.id)} loading={resume.isPending && resume.variables === c.id}>
                  Resume
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}

export function CashMovementDialog({ shift, onClose }) {
  const [type, setType] = useState("PAY_OUT");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [key, setKey] = useState(newIdempotencyKey);
  const [approval, setApproval] = useState(null);
  const parsed = parseAmount(amount);
  const save = useMutation({
    mutationFn: (approvalToken) => posApi.cashMovement(shift.id, { type, amount: parsed, reason, approvalToken }, key),
    onSuccess: () => {
      toast.success("Cash movement recorded.");
      onClose();
    },
    onError: (e) => {
      if (e.code === "APPROVAL_REQUIRED") return setApproval(e.details);
      if (e.status && e.status < 500) setKey(newIdempotencyKey());
      toast.error(errorMessage(e));
    },
  });
  return (
    <>
      <Modal open onOpenChange={(o) => !o && onClose()} title="Cash in / out" description="Pay-ins, pay-outs and safe drops are counted in the shift's expected cash." size="sm"
        footer={
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={!parsed || !reason.trim()}>
            Record
          </Button>
        }
      >
        <div className="space-y-4">
          <Field label="Type">
            {(a) => (
              <Select {...a} value={type} onChange={(e) => setType(e.target.value)}>
                <option value="PAY_OUT">Pay-out (petty cash)</option>
                <option value="PAY_IN">Pay-in (float top-up)</option>
                <option value="CASH_DROP">Cash drop to safe</option>
              </Select>
            )}
          </Field>
          <Field label="Amount">{(a) => <Input {...a} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />}</Field>
          <Field label="Reason">{(a) => <Input {...a} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} />}</Field>
        </div>
      </Modal>
      {approval && (
        <ApprovalDialog action={approval.action} value={approval.value} title="Manager approval: pay-out" onClose={() => setApproval(null)}
          onApproved={(token) => {
            setApproval(null);
            save.mutate(token);
          }}
        />
      )}
    </>
  );
}

const DENOMINATIONS = ["5000", "2000", "1000", "500", "100", "50", "20", "10", "5", "2", "1"];

export function CloseShiftDialog({ shift, onClosed, onClose }) {
  const summary = useQuery({ queryKey: ["pos", "summary", shift.id], queryFn: () => posApi.shiftSummary(shift.id) });
  const [counts, setCounts] = useState({});
  const [manual, setManual] = useState("");
  const [useDenoms, setUseDenoms] = useState(true);
  const [notes, setNotes] = useState("");
  const [key, setKey] = useState(newIdempotencyKey);
  const [approval, setApproval] = useState(null);
  const denomTotal = DENOMINATIONS.reduce((sum, d) => sum + Number(d) * (Number(counts[d]) || 0), 0);
  const counted = useDenoms ? denomTotal.toFixed(2) : parseAmount(manual);
  const close = useMutation({
    mutationFn: (approvalToken) =>
      posApi.closeShift(
        shift.id,
        { countedCash: counted, denominations: useDenoms ? Object.fromEntries(Object.entries(counts).filter(([, v]) => Number(v) > 0).map(([k, v]) => [k, Number(v)])) : undefined, notes, approvalToken },
        key,
      ),
    onSuccess: onClosed,
    onError: (e) => {
      if (e.code === "APPROVAL_REQUIRED") return setApproval(e.details);
      if (e.status && e.status < 500) setKey(newIdempotencyKey());
      toast.error(errorMessage(e));
    },
  });
  const s = summary.data;
  return (
    <>
      <Modal open onOpenChange={(o) => !o && onClose()} title={`Close shift ${shift.shiftNumber}`} description="Count the cash in the drawer. The variance against expected cash is recorded." size="lg"
        footer={
          <>
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => close.mutate(undefined)} loading={close.isPending} disabled={counted === null}>
              Close shift
            </Button>
          </>
        }
      >
        {!s ? (
          <Spinner />
        ) : (
          <div className="grid gap-6 md:grid-cols-2">
            <div className="space-y-1 text-sm">
              {[
                ["Opening float", s.shift.openingFloat],
                ["Cash sales", s.cashSales],
                ["Cash refunds", `-${s.cashRefunds}`],
                ["Pay-ins", s.payIns],
                ["Pay-outs", `-${s.payOuts}`],
                ["Cash drops", `-${s.cashDrops}`],
              ].map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <span className="text-slate-500">{k}</span>
                  <span className="tabular">{formatMoney(v)}</span>
                </div>
              ))}
              <div className="flex justify-between border-t border-slate-200 pt-2 font-semibold">
                <span>Expected cash</span>
                <span className="tabular">{formatMoney(s.expectedCash)}</span>
              </div>
              <div className="pt-3 text-xs text-slate-500">
                {s.salesCount} sales · {s.returnsCount} returns · {s.voidsCount} voids
                <div className="mt-1 flex flex-wrap gap-1">
                  {s.tenderTotals.map((t) => (
                    <Badge key={t.tenderType}>
                      {t.tenderType} {formatMoney(t.amount)}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
            <div className="space-y-3">
              <Checkbox label="Count by denomination" checked={useDenoms} onChange={(e) => setUseDenoms(e.target.checked)} />
              {useDenoms ? (
                <div className="grid grid-cols-3 gap-2">
                  {DENOMINATIONS.map((d) => (
                    <label key={d} className="text-xs text-slate-500">
                      {d}
                      <Input inputMode="numeric" className="h-9" value={counts[d] ?? ""} onChange={(e) => setCounts({ ...counts, [d]: e.target.value.replace(/\D/g, "") })} />
                    </label>
                  ))}
                </div>
              ) : (
                <Field label="Counted cash">{(a) => <Input {...a} inputMode="decimal" value={manual} onChange={(e) => setManual(e.target.value)} />}</Field>
              )}
              <div className="flex justify-between rounded-md bg-slate-50 px-3 py-2 text-sm">
                <span>Counted</span>
                <span className="font-semibold tabular">{counted ? formatMoney(counted) : "-"}</span>
              </div>
              {counted && (
                <div className={`flex justify-between rounded-md px-3 py-2 text-sm ${Number(counted) - Number(s.expectedCash) === 0 ? "bg-emerald-50 text-emerald-800" : "bg-secondary-50 text-secondary-900"}`}>
                  <span>Variance</span>
                  <span className="font-semibold tabular">{formatMoney((Number(counted) - Number(s.expectedCash)).toFixed(2))}</span>
                </div>
              )}
              <Field label="Notes">{(a) => <Input {...a} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />}</Field>
            </div>
          </div>
        )}
      </Modal>
      {approval && (
        <ApprovalDialog action={approval.action} value={approval.value} title="Manager approval: cash variance"
          description={`Expected ${approval.expectedCash}. A manager must approve this variance.`}
          onClose={() => setApproval(null)}
          onApproved={(token) => {
            setApproval(null);
            close.mutate(token);
          }}
        />
      )}
    </>
  );
}

export function ReceiptDialog({ sale, onClose, onNewSale, paper = "80mm" }) {
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={`Sale complete · ${sale.receiptNumber}`} size="md"
      footer={
        <>
          <Button variant="outline" onClick={() => downloadReceiptPdf(sale)}>
            <Download className="h-4 w-4" aria-hidden="true" /> Download
          </Button>
          <Button variant="outline" onClick={() => printReceipt(sale)}>
            <Printer className="h-4 w-4" aria-hidden="true" /> Print
          </Button>
          {onNewSale && (
            <Button size="lg" onClick={onNewSale} autoFocus>
              New sale (Enter)
            </Button>
          )}
        </>
      }
    >
      {Number(sale.changeDue) > 0 && (
        <div className="no-print mb-4 rounded-lg bg-emerald-50 px-4 py-3 text-center">
          <p className="text-sm text-emerald-800">Give change</p>
          <p className="text-4xl font-bold tabular text-emerald-700">{formatMoney(sale.changeDue)}</p>
        </div>
      )}
      <div className="max-h-[50vh] overflow-y-auto rounded border border-slate-200 bg-slate-50 py-3">
        <Receipt sale={sale} paper={paper} />
      </div>
    </Modal>
  );
}

