"use client";

import { useEffect, useState } from "react";

/** Returns `value` after it has stopped changing for `delay` ms (search boxes). */
export function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

/**
 * False during server rendering and the first client render, true afterwards. Use it to
 * show browser-only state (a persisted basket) without hydration mismatches.
 */
export function useMounted() {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}
