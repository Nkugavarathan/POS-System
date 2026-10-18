"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { Input } from "@/components/ui/form";
import { useDebouncedValue } from "@/lib/hooks";
import { formatMoney, formatQuantity } from "@/lib/format";
import { catalogApi } from "./api";

/**
 * Accessible combobox for picking a product by name, SKU or barcode. Calls `onPick(product)`
 * with a PosProductDto. Keyboard: ↑/↓ to move, Enter to choose, Esc to close.
 */
export function ProductPicker({ onPick, placeholder = "Search product by name, SKU or barcode", autoFocus, className }) {
  const listId = useId();
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const debounced = useDebouncedValue(q.trim(), 200);
  const wrapper = useRef(null);
  const { data = [], isFetching } = useQuery({
    queryKey: ["products", "picker", debounced],
    queryFn: () => catalogApi.posSearch(debounced),
    enabled: debounced.length >= 2,
    staleTime: 15_000,
  });

  useEffect(() => {
    const close = (e) => wrapper.current && !wrapper.current.contains(e.target) && setOpen(false);
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const choose = (p) => {
    onPick(p);
    setQ("");
    setOpen(false);
  };

  return (
    <div ref={wrapper} className={`relative ${className ?? ""}`}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <Input
        role="combobox"
        aria-expanded={open && data.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-label={placeholder}
        autoFocus={autoFocus}
        className="pl-9"
        placeholder={placeholder}
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
          setActive(0);
        }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setActive((i) => Math.min(i + 1, data.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActive((i) => Math.max(i - 1, 0));
          } else if (e.key === "Enter" && open && data[active]) {
            e.preventDefault();
            choose(data[active]);
          } else if (e.key === "Escape") {
            setOpen(false);
          }
        }}
      />
      {open && debounced.length >= 2 && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-30 mt-1 max-h-72 w-full overflow-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg"
        >
          {data.length === 0 && (
            <li className="px-3 py-2 text-sm text-slate-500">{isFetching ? "Searching…" : "No matching products"}</li>
          )}
          {data.map((p, i) => (
            <li
              key={p.id}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(p);
              }}
              onMouseEnter={() => setActive(i)}
              className={`flex cursor-pointer items-center justify-between gap-3 px-3 py-2 text-sm ${
                i === active ? "bg-primary-50" : ""
              }`}
            >
              <span className="min-w-0">
                <span className="block truncate font-medium text-slate-800">{p.name}</span>
                <span className="font-mono text-xs text-slate-500">
                  {p.sku} {p.barcode && `· ${p.barcode}`}
                </span>
              </span>
              <span className="shrink-0 text-right text-xs text-slate-500">
                <span className="block font-medium text-slate-800">{formatMoney(p.sellingPrice)}</span>
                {formatQuantity(p.quantityOnHand)} in stock
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
