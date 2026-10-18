import { describe, expect, it } from "vitest";
import { hasAny, hasPermission, landingPath } from "@/lib/permissions";
import { NAV_SECTIONS } from "@/lib/navigation";
import { formatMoney, formatQuantity } from "@/lib/format";

const cashier = { permissions: ["POS_SELL", "SALE_VIEW_OWN", "PRODUCT_VIEW"] };
const admin = { permissions: ["DASHBOARD_VIEW", "USER_VIEW", "USER_MANAGE", "PRODUCT_VIEW", "SYSTEM_CONFIG"] };

describe("permissions", () => {
  it("checks single and any-of permissions", () => {
    expect(hasPermission(cashier, "POS_SELL")).toBe(true);
    expect(hasPermission(cashier, "USER_MANAGE")).toBe(false);
    expect(hasAny(cashier, ["USER_VIEW", "POS_SELL"])).toBe(true);
    expect(hasAny(cashier, [])).toBe(true);
    expect(hasPermission(null, "POS_SELL")).toBe(false);
  });

  it("lands cashiers on the POS and managers on the dashboard", () => {
    expect(landingPath(cashier)).toBe("/pos");
    expect(landingPath(admin)).toBe("/dashboard");
  });

  it("hides admin navigation from cashiers", () => {
    const visible = (user) =>
      NAV_SECTIONS.flatMap((s) => s.items)
        .filter((i) => hasAny(user, i.anyOf))
        .map((i) => i.href);
    expect(visible(cashier)).toContain("/pos");
    expect(visible(cashier)).not.toContain("/users");
    expect(visible(cashier)).not.toContain("/settings");
    expect(visible(admin)).toContain("/users");
  });
});

describe("formatting", () => {
  it("formats decimal strings without float conversion", () => {
    expect(formatMoney("1250.5")).toBe("1,250.50");
    expect(formatMoney("1234567890123.45")).toBe("1,234,567,890,123.45");
    expect(formatMoney("-0.05")).toBe("-0.05");
    expect(formatMoney("12", { currency: "LKR" })).toBe("LKR 12.00");
    expect(formatMoney(null)).toBe("");
  });

  it("formats quantities compactly", () => {
    expect(formatQuantity("2")).toBe("2");
    expect(formatQuantity("0.750")).toBe("0.75");
    expect(formatQuantity("3.000")).toBe("3");
  });
});
