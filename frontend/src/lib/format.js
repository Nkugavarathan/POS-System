/** Display formatting. Money values arrive as decimal strings from the API. */

const LOCALE = "en-LK";

export function formatDateTime(iso, timeZone = "Asia/Colombo") {
  if (!iso) return "";
  return new Intl.DateTimeFormat(LOCALE, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone,
  }).format(new Date(iso));
}

export function formatDate(iso, timeZone = "Asia/Colombo") {
  if (!iso) return "";
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium", timeZone }).format(new Date(iso));
}

export function formatTime(iso, timeZone = "Asia/Colombo") {
  if (!iso) return "";
  return new Intl.DateTimeFormat(LOCALE, { timeStyle: "short", timeZone }).format(new Date(iso));
}

/**
 * Formats a decimal string ("1250.5" → "1,250.50") without converting to a float, so large
 * or precise values are never rounded by JavaScript number arithmetic.
 */
export function formatMoney(value, { currency = null, decimals = 2 } = {}) {
  if (value === null || value === undefined || value === "") return "";
  const str = String(value);
  const negative = str.startsWith("-");
  const [intPartRaw, fracRaw = ""] = (negative ? str.slice(1) : str).split(".");
  const frac = (fracRaw + "0".repeat(decimals)).slice(0, decimals);
  const intPart = intPartRaw.replace(/^0+(?=\d)/, "").replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const body = decimals > 0 ? `${intPart}.${frac}` : intPart;
  return `${negative ? "-" : ""}${currency ? `${currency} ` : ""}${body}`;
}

/** Quantity display: integers without decimals, weights with up to 3. */
export function formatQuantity(value) {
  if (value === null || value === undefined) return "";
  const str = String(value);
  if (!str.includes(".")) return str;
  return str.replace(/0+$/, "").replace(/\.$/, "");
}
