"use client";

import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

const TENDER_LABEL = { CASH: "Cash", CARD: "Card", LOYALTY: "Loyalty points" };

/**
 * Printable receipt (80 mm thermal by default, or A4). Rendered from the stored sale, never
 * recalculated. Reprints are clearly marked as copies.
 */
export function Receipt({ sale, paper = "80mm" }) {
  const s = sale.store;
  const isA4 = paper === "A4";
  const line = "border-t border-dashed border-slate-400 my-2";
  return (
    <div className={`print-area mx-auto bg-white text-black ${isA4 ? "receipt-a4 p-8" : "receipt-80mm p-2"}`}>
      {sale.reprint && <p className="mb-1 text-center font-bold">*** COPY / REPRINT ***</p>}
      <div className="text-center">
        <p className="text-base font-bold">{s.name}</p>
        {s.receiptHeader && <p>{s.receiptHeader}</p>}
        {s.addressLine1 && <p>{s.addressLine1}</p>}
        {(s.addressLine2 || s.city) && <p>{[s.addressLine2, s.city].filter(Boolean).join(", ")}</p>}
        {s.phone && <p>Tel {s.phone}</p>}
        {s.taxRegistrationNo && <p>Tax reg. {s.taxRegistrationNo}</p>}
      </div>
      <div className={line} />
      <div className="flex justify-between">
        <span>Receipt</span>
        <span className="font-semibold">{sale.receiptNumber}</span>
      </div>
      <div className="flex justify-between">
        <span>Date</span>
        <span>{formatDateTime(sale.completedAt)}</span>
      </div>
      <div className="flex justify-between">
        <span>Cashier</span>
        <span>
          {sale.cashierName} · {sale.registerCode}
        </span>
      </div>
      {sale.customerName && (
        <div className="flex justify-between">
          <span>Customer</span>
          <span>{sale.customerName}</span>
        </div>
      )}
      {sale.onlineOrderNumber && (
        <div className="flex justify-between">
          <span>Online order</span>
          <span>{sale.onlineOrderNumber}</span>
        </div>
      )}
      <div className={line} />
      <table className="w-full">
        <tbody>
          {sale.lines.map((l) => (
            <tr key={l.lineNumber} className="align-top">
              <td className="pb-1">
                <div>{l.name}</div>
                <div className="text-[0.9em] text-slate-700">
                  {formatQuantity(l.quantity)} {l.unitOfMeasure.toLowerCase()} × {formatMoney(l.unitPrice)}
                  {l.originalUnitPrice !== l.unitPrice && " (price adj.)"}
                </div>
                {Number(l.promoDiscount) > 0 && <div className="text-[0.9em]">Promo −{formatMoney(l.promoDiscount)}</div>}
                {Number(l.manualDiscount) > 0 && <div className="text-[0.9em]">Discount −{formatMoney(l.manualDiscount)}</div>}
              </td>
              <td className="pb-1 text-right tabular">{formatMoney(l.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className={line} />
      <Row label="Subtotal" value={sale.totals.subtotal} />
      {Number(sale.totals.discountTotal) > 0 && <Row label="Discounts" value={`-${sale.totals.discountTotal}`} />}
      <Row label={sale.pricesIncludeTax ? "Tax included" : "Tax"} value={sale.totals.taxTotal} />
      <div className="mt-1 flex justify-between text-base font-bold">
        <span>TOTAL {sale.currency}</span>
        <span className="tabular">{formatMoney(sale.totals.grandTotal)}</span>
      </div>
      <div className={line} />
      {sale.payments.map((p, i) => (
        <div key={i}>
          <Row
            label={`${TENDER_LABEL[p.tenderType] ?? p.tenderType}${p.cardLast4 ? ` •••• ${p.cardLast4}` : ""}`}
            value={p.tenderType === "CASH" ? p.amountTendered : p.amount}
          />
          {p.authCode && <p className="text-[0.9em]">Approval {p.authCode}</p>}
          {p.loyaltyPoints && <p className="text-[0.9em]">{p.loyaltyPoints} points redeemed</p>}
        </div>
      ))}
      {Number(sale.changeDue) > 0 && <Row label="Change" value={sale.changeDue} bold />}
      {(sale.loyaltyPointsEarned > 0 || sale.loyaltyPointsRedeemed > 0) && (
        <>
          <div className={line} />
          {sale.loyaltyPointsEarned > 0 && <p>Points earned: {sale.loyaltyPointsEarned}</p>}
        </>
      )}
      <div className={line} />
      <p className="text-center">Items: {sale.totals.itemCount}</p>
      {s.receiptFooter && <p className="mt-2 text-center">{s.receiptFooter}</p>}
    </div>
  );
}

function Row({ label, value, bold }) {
  return (
    <div className={`flex justify-between ${bold ? "font-bold" : ""}`}>
      <span>{label}</span>
      <span className="tabular">{formatMoney(value)}</span>
    </div>
  );
}
