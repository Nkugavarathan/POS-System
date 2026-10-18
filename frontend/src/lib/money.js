/**
 * Exact decimal arithmetic for instant on-screen estimates, using BigInt minor units (cents)
 * and milli-units for quantities. The server's quote is always the authoritative price; these
 * helpers only keep the cart responsive while the quote is in flight. Never use JS floats for money.
 */

export function toCents(value) {
  if (value === null || value === undefined || value === "") return 0n;
  const str = String(value).trim();
  const negative = str.startsWith("-");
  const [i, f = ""] = (negative ? str.slice(1) : str).split(".");
  const cents = BigInt(i || "0") * 100n + BigInt((f + "00").slice(0, 2) || "0");
  // round half-up on the third decimal
  const roundUp = f.length > 2 && Number(f[2]) >= 5 ? 1n : 0n;
  const total = cents + roundUp;
  return negative ? -total : total;
}

export function toMilli(value) {
  if (value === null || value === undefined || value === "") return 0n;
  const [i, f = ""] = String(value).trim().split(".");
  return BigInt(i || "0") * 1000n + BigInt((f + "000").slice(0, 3) || "0");
}

export function centsToString(cents) {
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  const str = `${abs / 100n}.${String(abs % 100n).padStart(2, "0")}`;
  return negative ? `-${str}` : str;
}

/** price × quantity rounded half-up to cents (same rule as the server). */
export function extend(price, quantity) {
  const product = toCents(price) * toMilli(quantity); // cents × milli
  const rounded = (product + 500n) / 1000n;
  return rounded;
}

export function sumCents(values) {
  return values.reduce((acc, v) => acc + v, 0n);
}

/** Change for cash: tendered − due (≥ 0), as a decimal string. */
export function changeDue(due, tendered) {
  const diff = toCents(tendered) - toCents(due);
  return centsToString(diff > 0n ? diff : 0n);
}

export function compare(a, b) {
  const x = toCents(a);
  const y = toCents(b);
  return x === y ? 0 : x > y ? 1 : -1;
}

export function subtract(a, b) {
  return centsToString(toCents(a) - toCents(b));
}

export function add(a, b) {
  return centsToString(toCents(a) + toCents(b));
}

/** Normalizes user input like "1,250.5" to "1250.50"; returns null if invalid. */
export function parseAmount(input) {
  if (input === null || input === undefined) return null;
  const s = String(input).replace(/,/g, "").trim();
  if (!/^\d{1,12}(\.\d{0,2})?$/.test(s)) return null;
  return centsToString(toCents(s));
}

export function parseQuantity(input, fractional) {
  const s = String(input ?? "").replace(/,/g, "").trim();
  if (fractional ? !/^\d{1,6}(\.\d{1,3})?$/.test(s) : !/^\d{1,6}$/.test(s)) return null;
  if (toMilli(s) <= 0n) return null;
  return s;
}
