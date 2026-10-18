"use client";

import { useEffect, useRef } from "react";

/** True when the event comes from a text field where typing must not trigger shortcuts. */
export function isTypingTarget(target) {
  if (!target || !(target instanceof HTMLElement)) return false;
  if (target.dataset.shortcutsAllowed === "true") return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

/**
 * Global keyboard shortcuts. `bindings` maps a key (KeyboardEvent.key, e.g. "F2", "Delete",
 * "+", or "Ctrl+Enter") to a handler. Function keys and Ctrl/Meta combos fire everywhere;
 * plain keys are ignored while typing in an input (except inputs marked
 * data-shortcuts-allowed="true", like the till's search box). Disabled while a dialog is open.
 */
export function useShortcuts(bindings, { enabled = true } = {}) {
  const ref = useRef(bindings);
  ref.current = bindings;
  useEffect(() => {
    if (!enabled) return undefined;
    const onKey = (e) => {
      if (document.querySelector('[role="dialog"], [role="alertdialog"]')) return;
      const combo = `${e.ctrlKey || e.metaKey ? "Ctrl+" : ""}${e.key}`;
      const handler = ref.current[combo] ?? (!e.ctrlKey && !e.metaKey ? ref.current[e.key] : undefined);
      if (!handler) return;
      const isFunctionKey = /^F\d{1,2}$/.test(e.key) || combo.startsWith("Ctrl+");
      if (!isFunctionKey && isTypingTarget(e.target)) return;
      e.preventDefault();
      handler(e);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [enabled]);
}

export const POS_SHORTCUTS = [
  ["F2", "Scan with camera"],
  ["F3", "Search / barcode box"],
  ["F4", "Customer"],
  ["F6", "Discount on selected line"],
  ["F7", "Online order pickup"],
  ["F8", "Hold cart"],
  ["F9", "Held carts"],
  ["F12 / Ctrl+Enter", "Pay"],
  ["+ / −", "Change quantity"],
  ["Delete", "Remove selected line"],
  ["↑ / ↓", "Select line"],
];
