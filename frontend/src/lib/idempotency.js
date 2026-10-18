/** A fresh key for one logical attempt of a critical write (reuse it when retrying that attempt). */
export function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return "k" + Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
}

export const idempotent = (key) => ({ headers: { "Idempotency-Key": key } });
