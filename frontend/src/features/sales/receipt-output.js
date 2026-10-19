"use client";

import { formatDateTime, formatMoney, formatQuantity } from "@/lib/format";
import { compare } from "@/lib/money";

const TENDER_LABEL = { CASH: "Cash", CARD: "Card", LOYALTY: "Loyalty points" };
const positive = (v) => v != null && compare(v, "0") > 0;

/**
 * The receipt as a list of blocks, built once from the stored sale and rendered both as a
 * print-only document and as a PDF, so the printed and downloaded copies always match.
 *   { t: "center", text, bold?, big? } | { t: "row", label, value, bold?, big? }
 *   { t: "item", name, detail[], amount } | { t: "rule" } | { t: "gap" }
 */
export function receiptBlocks(sale) {
  const s = sale.store;
  const b = [];
  if (sale.reprint) b.push({ t: "center", text: "*** COPY / REPRINT ***", bold: true });
  b.push({ t: "center", text: s.name, bold: true, big: true });
  if (s.receiptHeader && s.receiptHeader !== s.name) b.push({ t: "center", text: s.receiptHeader });
  if (s.addressLine1) b.push({ t: "center", text: s.addressLine1 });
  if (s.addressLine2 || s.city) b.push({ t: "center", text: [s.addressLine2, s.city].filter(Boolean).join(", ") });
  if (s.phone) b.push({ t: "center", text: `Tel ${s.phone}` });
  if (s.taxRegistrationNo) b.push({ t: "center", text: `Tax reg. ${s.taxRegistrationNo}` });
  b.push({ t: "rule" });
  b.push({ t: "row", label: "Receipt", value: sale.receiptNumber });
  b.push({ t: "row", label: "Date", value: formatDateTime(sale.completedAt) });
  b.push({ t: "row", label: "Cashier", value: `${sale.cashierName} - ${sale.registerCode}` });
  if (sale.customerName) b.push({ t: "row", label: "Customer", value: sale.customerName });
  if (sale.onlineOrderNumber) b.push({ t: "row", label: "Online order", value: sale.onlineOrderNumber });
  b.push({ t: "rule" });
  for (const l of sale.lines) {
    const detail = [
      `${formatQuantity(l.quantity)} ${l.unitOfMeasure.toLowerCase()} x ${formatMoney(l.unitPrice)}${
        l.originalUnitPrice !== l.unitPrice ? " (price adj.)" : ""
      }`,
    ];
    if (positive(l.promoDiscount)) detail.push(`Promo -${formatMoney(l.promoDiscount)}`);
    if (positive(l.manualDiscount)) detail.push(`Discount -${formatMoney(l.manualDiscount)}`);
    b.push({ t: "item", name: l.name, detail, amount: formatMoney(l.lineTotal) });
  }
  b.push({ t: "rule" });
  b.push({ t: "row", label: "Subtotal", value: formatMoney(sale.totals.subtotal) });
  if (positive(sale.totals.discountTotal)) b.push({ t: "row", label: "Discounts", value: `-${formatMoney(sale.totals.discountTotal)}` });
  b.push({ t: "row", label: sale.pricesIncludeTax ? "Tax included" : "Tax", value: formatMoney(sale.totals.taxTotal) });
  b.push({ t: "row", label: `TOTAL ${sale.currency}`, value: formatMoney(sale.totals.grandTotal), bold: true, big: true });
  b.push({ t: "rule" });
  for (const p of sale.payments) {
    b.push({
      t: "row",
      label: `${TENDER_LABEL[p.tenderType] ?? p.tenderType}${p.cardLast4 ? ` **** ${p.cardLast4}` : ""}`,
      value: formatMoney(p.tenderType === "CASH" ? p.amountTendered : p.amount),
    });
    if (p.authCode) b.push({ t: "center", text: `Approval ${p.authCode}`, left: true });
    if (p.loyaltyPoints) b.push({ t: "center", text: `${p.loyaltyPoints} points redeemed`, left: true });
  }
  if (positive(sale.changeDue)) b.push({ t: "row", label: "Change", value: formatMoney(sale.changeDue), bold: true });
  if (sale.loyaltyPointsEarned > 0) {
    b.push({ t: "rule" });
    b.push({ t: "center", text: `Points earned: ${sale.loyaltyPointsEarned}`, left: true });
  }
  b.push({ t: "rule" });
  b.push({ t: "center", text: `Items: ${sale.totals.itemCount}` });
  if (s.receiptFooter) {
    b.push({ t: "gap" });
    b.push({ t: "center", text: s.receiptFooter });
  }
  return b;
}

const esc = (v) =>
  String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

/** A standalone HTML document containing only the receipt, sized for an 80 mm roll. */
export function receiptHtml(sale) {
  const body = receiptBlocks(sale)
    .map((x) => {
      switch (x.t) {
        case "center":
          return `<p class="${x.left ? "" : "c"}${x.bold ? " b" : ""}${x.big ? " big" : ""}">${esc(x.text)}</p>`;
        case "row":
          return `<div class="row${x.bold ? " b" : ""}${x.big ? " big" : ""}"><span>${esc(x.label)}</span><span>${esc(x.value)}</span></div>`;
        case "item":
          return `<div class="row"><span>${esc(x.name)}</span><span>${esc(x.amount)}</span></div>${x.detail
            .map((d) => `<p class="d">${esc(d)}</p>`)
            .join("")}`;
        case "rule":
          return `<hr>`;
        default:
          return `<div style="height:6px"></div>`;
      }
    })
    .join("\n");
  return `<!doctype html><html><head><meta charset="utf-8"><title>Receipt ${esc(sale.receiptNumber)}</title>
<style>
  @page { size: 80mm auto; margin: 0; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #fff; color: #000; }
  body { width: 80mm; padding: 4mm; font: 11px/1.35 ui-monospace, Menlo, Consolas, "Courier New", monospace; }
  p { margin: 0; }
  .c { text-align: center; }
  .b { font-weight: 700; }
  .big { font-size: 13px; }
  .d { padding-left: 6px; color: #333; }
  .row { display: flex; justify-content: space-between; gap: 8px; }
  .row span:last-child { text-align: right; white-space: nowrap; }
  hr { border: 0; border-top: 1px dashed #555; margin: 6px 0; }
</style></head><body>${body}</body></html>`;
}

/**
 * Prints only the receipt: it is written into a hidden iframe and printed from there, so the
 * page behind it (dialogs, menus, the till) never reaches the printer.
 */
export function printReceipt(sale) {
  const frame = document.createElement("iframe");
  frame.setAttribute("aria-hidden", "true");
  frame.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden";
  document.body.appendChild(frame);
  const cleanup = () => setTimeout(() => frame.remove(), 500);
  frame.onload = () => {
    const win = frame.contentWindow;
    win.addEventListener("afterprint", cleanup, { once: true });
    win.focus();
    win.print();
    // Some browsers never fire afterprint; remove the frame after a while regardless.
    setTimeout(cleanup, 60_000);
  };
  frame.srcdoc = receiptHtml(sale);
}

/** Builds an 80 mm wide PDF of the receipt (height fits the content). Returns the jsPDF doc. */
export async function buildReceiptPdf(sale) {
  const { jsPDF } = await import("jspdf");
  const blocks = receiptBlocks(sale);
  const W = 80;
  const M = 4;
  const inner = W - 2 * M;
  const LH = 3.6; // mm per line at 8 pt
  const measure = new jsPDF({ unit: "mm", format: [W, 200] });
  measure.setFont("courier", "normal");
  measure.setFontSize(8);

  // Lay out first to know the page height.
  const ops = [];
  let y = M + LH;
  const wrap = (text, width) => measure.splitTextToSize(String(text), width);
  for (const x of blocks) {
    if (x.t === "rule") {
      ops.push({ k: "rule", y: y - LH / 2 });
      y += LH;
    } else if (x.t === "gap") {
      y += LH / 2;
    } else if (x.t === "center") {
      for (const part of wrap(x.text, inner)) {
        ops.push({ k: "text", text: part, x: x.left ? M : W / 2, align: x.left ? "left" : "center", y, bold: x.bold, big: x.big });
        y += x.big ? LH * 1.2 : LH;
      }
    } else if (x.t === "row") {
      const valueW = measure.getTextWidth(String(x.value)) * (x.big ? 1.2 : 1);
      const labels = wrap(x.label, inner - valueW - 2);
      labels.forEach((part, i) => {
        ops.push({ k: "text", text: part, x: M, align: "left", y, bold: x.bold, big: x.big });
        if (i === 0) ops.push({ k: "text", text: String(x.value), x: W - M, align: "right", y, bold: x.bold, big: x.big });
        y += x.big ? LH * 1.2 : LH;
      });
    } else if (x.t === "item") {
      const valueW = measure.getTextWidth(x.amount);
      wrap(x.name, inner - valueW - 2).forEach((part, i) => {
        ops.push({ k: "text", text: part, x: M, align: "left", y });
        if (i === 0) ops.push({ k: "text", text: x.amount, x: W - M, align: "right", y });
        y += LH;
      });
      for (const d of x.detail) {
        for (const part of wrap(d, inner - 2)) {
          ops.push({ k: "text", text: part, x: M + 2, align: "left", y, grey: true });
          y += LH;
        }
      }
    }
  }

  const doc = new jsPDF({ unit: "mm", format: [W, Math.max(60, y + M)] });
  doc.setProperties({ title: `Receipt ${sale.receiptNumber}`, subject: sale.store.name });
  doc.setLineDashPattern([0.8, 0.8], 0);
  doc.setDrawColor(90);
  for (const op of ops) {
    if (op.k === "rule") {
      doc.line(M, op.y, W - M, op.y);
      continue;
    }
    doc.setFont("courier", op.bold ? "bold" : "normal");
    doc.setFontSize(op.big ? 9.6 : 8);
    doc.setTextColor(op.grey ? 70 : 0);
    doc.text(op.text, op.x, op.y, { align: op.align });
  }
  return doc;
}

/** Downloads the receipt as a PDF file. */
export async function downloadReceiptPdf(sale) {
  const doc = await buildReceiptPdf(sale);
  doc.save(`receipt-${sale.receiptNumber}.pdf`);
}
