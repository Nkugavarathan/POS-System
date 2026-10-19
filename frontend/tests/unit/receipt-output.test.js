import { describe, expect, it } from "vitest";
import { buildReceiptPdf, receiptBlocks, receiptHtml } from "@/features/sales/receipt-output";

const sale = {
  receiptNumber: "CMB01-R01-20261019-000007",
  reprint: false,
  completedAt: "2026-10-19T05:30:00Z",
  cashierName: "Dilini Silva",
  registerCode: "R01",
  customerName: null,
  onlineOrderNumber: null,
  currency: "LKR",
  pricesIncludeTax: true,
  store: { name: "FreshPick Supermarket - Colombo 03", receiptHeader: "FreshPick Supermarket", addressLine1: "214 Galle Road", city: "Colombo 03", phone: "+94 11 250 3344", taxRegistrationNo: "114567890-7000", receiptFooter: "Thank you <3" },
  lines: [
    { lineNumber: 1, name: "Pond's Magic Freshness Talc Acacia Honey 100g", unitOfMeasure: "EACH", quantity: "1.000", unitPrice: "300.00", originalUnitPrice: "300.00", promoDiscount: "0.00", manualDiscount: "0.00", lineTotal: "300.00" },
    { lineNumber: 2, name: "Macks LED Bulb 12W (MKA12W)", unitOfMeasure: "EACH", quantity: "2.000", unitPrice: "500.00", originalUnitPrice: "500.00", promoDiscount: "50.00", manualDiscount: "0.00", lineTotal: "950.00" },
  ],
  totals: { subtotal: "1300.00", discountTotal: "50.00", taxTotal: "190.68", grandTotal: "1250.00", itemCount: 3 },
  payments: [{ tenderType: "CASH", amount: "1250.00", amountTendered: "2000.00" }],
  changeDue: "750.00",
  loyaltyPointsEarned: 12,
};

describe("receipt output", () => {
  it("lays out lines, totals and change from the stored sale", () => {
    const rows = receiptBlocks(sale).filter((b) => b.t === "row").map((b) => [b.label, b.value]);
    expect(rows).toContainEqual(["TOTAL LKR", "1,250.00"]);
    expect(rows).toContainEqual(["Discounts", "-50.00"]);
    expect(rows).toContainEqual(["Change", "750.00"]);
    const items = receiptBlocks(sale).filter((b) => b.t === "item");
    expect(items[1].detail).toEqual(["2 each x 500.00", "Promo -50.00"]);
  });

  it("produces a print document with only the receipt, escaped", () => {
    const html = receiptHtml(sale);
    expect(html).toContain("@page { size: 80mm auto");
    expect(html).toContain("Pond&#39;s Magic");
    expect(html).toContain("Thank you &lt;3");
    expect(html).not.toContain("<script");
  });

  it("builds a real PDF", async () => {
    const doc = await buildReceiptPdf(sale);
    const bytes = new Uint8Array(doc.output("arraybuffer"));
    expect(String.fromCharCode(...bytes.slice(0, 5))).toBe("%PDF-");
    expect(doc.internal.pageSize.getWidth()).toBeCloseTo(80, 0);
  });
});
