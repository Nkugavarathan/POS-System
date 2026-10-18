"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Banknote, CreditCard, Gift, Plus, Trash2 } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { centsToString, changeDue, compare, parseAmount, subtract, toCents } from "@/lib/money";
import { formatMoney } from "@/lib/format";

function quickCash(total) {
  const t = toCents(total);
  const options = new Set([centsToString(t)]);
  for (const step of [100n, 500n, 1000n, 5000n]) {
    const s = step * 100n;
    options.add(centsToString(((t + s - 1n) / s) * s));
  }
  return [...options].filter((v) => toCents(v) >= t).slice(0, 5);
}

/**
 * Tender entry. Non-cash tenders (card, points) are applied first; cash covers the rest and is
 * the only tender that gives change. `onPay(payments)` performs the checkout; while it runs the
 * dialog cannot be submitted again (no double charge).
 */
export function PaymentDialog({ open, total, customer, onPay, onClose, paying, error }) {
  const [cards, setCards] = useState([]);
  const [loyalty, setLoyalty] = useState("");
  const [tendered, setTendered] = useState(total);
  const cashRef = useRef(null);

  useEffect(() => {
    if (open) {
      setCards([]);
      setLoyalty("");
      setTendered(total);
      setTimeout(() => cashRef.current?.select(), 50);
    }
  }, [open, total]);

  const nonCash = useMemo(() => {
    let sum = 0n;
    cards.forEach((c) => (sum += toCents(parseAmount(c.amount) ?? "0")));
    sum += toCents(parseAmount(loyalty) ?? "0");
    return centsToString(sum);
  }, [cards, loyalty]);
  const cashDue = compare(subtract(total, nonCash), "0") > 0 ? subtract(total, nonCash) : "0.00";
  const tenderedValue = parseAmount(tendered) ?? "0.00";
  const cashShort = compare(cashDue, "0") > 0 && compare(tenderedValue, cashDue) < 0;
  const overpaidNonCash = compare(nonCash, total) > 0;
  const cardsValid = cards.every((c) => parseAmount(c.amount) && /^[A-Za-z0-9-]{2,20}$/.test(c.authCode) && (!c.last4 || /^\d{4}$/.test(c.last4)));
  const maxPoints = customer?.balanceValue ?? "0";
  const loyaltyValid = !loyalty || (parseAmount(loyalty) && compare(parseAmount(loyalty), maxPoints) <= 0);
  const canPay = !paying && !cashShort && !overpaidNonCash && cardsValid && loyaltyValid;

  const submit = () => {
    if (!canPay) return;
    const payments = [];
    cards.forEach((c) =>
      payments.push({
        tenderType: "CARD",
        amount: parseAmount(c.amount),
        card: { brand: c.brand || undefined, last4: c.last4 || undefined, authCode: c.authCode.trim() },
      }),
    );
    if (loyalty && parseAmount(loyalty)) payments.push({ tenderType: "LOYALTY", amount: parseAmount(loyalty) });
    if (compare(cashDue, "0") > 0) payments.push({ tenderType: "CASH", amountTendered: tenderedValue });
    onPay(payments);
  };

  return (
    <Modal open={open} onOpenChange={(o) => !o && !paying && onClose()} title="Payment" size="lg" dismissible={!paying}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={paying}>
            Back to cart
          </Button>
          <Button size="xl" variant="success" onClick={submit} loading={paying} disabled={!canPay}>
            Complete sale · {formatMoney(total)}
          </Button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="space-y-5"
      >
        {error && <Alert tone="danger">{error}</Alert>}
        <div className="flex items-end justify-between rounded-lg bg-primary-50 px-5 py-4">
          <span className="text-sm font-medium text-primary-800">Amount due</span>
          <span className="text-4xl font-bold tabular text-primary-900">{formatMoney(total)}</span>
        </div>

        {cards.map((c, i) => (
          <div key={i} className="grid gap-3 rounded-lg border border-slate-200 p-3 sm:grid-cols-[1fr_1fr_1fr_1fr_auto] sm:items-end">
            <Field label="Card amount">
              {(a) => <Input {...a} inputMode="decimal" value={c.amount} onChange={(e) => setCards(cards.map((x, j) => (j === i ? { ...x, amount: e.target.value } : x)))} />}
            </Field>
            <Field label="Approval code" hint="From terminal slip">
              {(a) => <Input {...a} value={c.authCode} onChange={(e) => setCards(cards.map((x, j) => (j === i ? { ...x, authCode: e.target.value } : x)))} />}
            </Field>
            <Field label="Last 4 digits">
              {(a) => <Input {...a} inputMode="numeric" maxLength={4} value={c.last4} onChange={(e) => setCards(cards.map((x, j) => (j === i ? { ...x, last4: e.target.value.replace(/\D/g, "") } : x)))} />}
            </Field>
            <Field label="Brand">
              {(a) => (
                <Select {...a} value={c.brand} onChange={(e) => setCards(cards.map((x, j) => (j === i ? { ...x, brand: e.target.value } : x)))}>
                  <option value="">—</option>
                  <option>VISA</option>
                  <option>MASTERCARD</option>
                  <option>AMEX</option>
                  <option>OTHER</option>
                </Select>
              )}
            </Field>
            <Button variant="ghost" aria-label="Remove card payment" onClick={() => setCards(cards.filter((_, j) => j !== i))}>
              <Trash2 className="h-4 w-4 text-red-600" />
            </Button>
          </div>
        ))}

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            onClick={() => setCards([...cards, { amount: compare(subtract(total, nonCash), "0") > 0 ? subtract(total, nonCash) : "", authCode: "", last4: "", brand: "" }])}
          >
            <CreditCard className="h-4 w-4" aria-hidden="true" /> <Plus className="h-3 w-3" aria-hidden="true" /> Card (external terminal)
          </Button>
          {customer && Number(customer.balance) > 0 && (
            <div className="flex items-center gap-2">
              <Gift className="h-4 w-4 text-secondary-700" aria-hidden="true" />
              <Input
                aria-label="Pay with loyalty points (amount)"
                className="w-40"
                inputMode="decimal"
                placeholder={`Points (max ${formatMoney(maxPoints)})`}
                value={loyalty}
                onChange={(e) => setLoyalty(e.target.value)}
              />
            </div>
          )}
        </div>
        {!loyaltyValid && <p className="text-sm text-red-600">Points can pay at most {formatMoney(maxPoints)}.</p>}
        {overpaidNonCash && <p className="text-sm text-red-600">Card and points payments are more than the amount due.</p>}
        {cards.length > 0 && !cardsValid && <p className="text-sm text-amber-700">Enter each card amount and the approval code from the terminal.</p>}

        {compare(cashDue, "0") > 0 && (
          <div className="rounded-lg border border-slate-200 p-4">
            <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
              <Banknote className="h-4 w-4" aria-hidden="true" /> Cash due {formatMoney(cashDue)}
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
              <Field label="Cash received" error={cashShort ? "Less than the cash due" : null}>
                {(a) => <Input {...a} ref={cashRef} inputMode="decimal" className="h-14 text-2xl font-semibold tabular" value={tendered} onChange={(e) => setTendered(e.target.value)} />}
              </Field>
              <div className="text-right">
                <p className="text-sm text-slate-500">Change</p>
                <p className="text-3xl font-bold tabular text-emerald-700">{formatMoney(changeDue(cashDue, tenderedValue))}</p>
              </div>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              {quickCash(cashDue).map((v) => (
                <Button key={v} variant="outline" size="sm" onClick={() => setTendered(v)}>
                  {formatMoney(v)}
                </Button>
              ))}
            </div>
          </div>
        )}
        <button type="submit" className="hidden" aria-hidden="true" tabIndex={-1} />
      </form>
    </Modal>
  );
}
