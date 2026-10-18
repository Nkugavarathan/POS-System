"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Banknote,
  Keyboard,
  LayoutDashboard,
  LogOut,
  Minus,
  PauseCircle,
  Percent,
  PlayCircle,
  Plus,
  ScanBarcode,
  Search,
  ShieldCheck,
  Tag,
  Trash2,
  User,
  Wallet,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge, Spinner } from "@/components/ui/feedback";
import { ConfirmDialog, Modal } from "@/components/ui/dialog";
import { Input } from "@/components/ui/form";
import { useLogout } from "@/features/auth/hooks";
import { ApprovalDialog } from "@/features/auth/approval-dialog";
import { catalogApi } from "@/features/catalog/api";
import { ScannerDialog } from "@/features/scanner/scanner-dialog";
import { useSessionStore } from "@/stores/session-store";
import { hasAny, hasPermission } from "@/lib/permissions";
import { useDebouncedValue } from "@/lib/hooks";
import { POS_SHORTCUTS, useShortcuts } from "@/lib/keyboard";
import { newIdempotencyKey } from "@/lib/idempotency";
import { errorMessage } from "@/lib/api-error";
import { centsToString, extend, sumCents, toCents } from "@/lib/money";
import { formatMoney, formatQuantity } from "@/lib/format";
import { cn } from "@/lib/cn";
import { cartRequest, useCartStore } from "./cart-store";
import { posApi, posKeys } from "./api";
import { OpenShiftDialog } from "./open-shift-dialog";
import { PaymentDialog } from "./payment-dialog";
import { CashMovementDialog, CloseShiftDialog, CustomerDialog, HeldCartsDialog, LineEditDialog, ReceiptDialog } from "./dialogs";

const BARCODE_PATTERN = /^\d{8,14}$/;

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);
  return <span className="tabular">{now.toLocaleTimeString("en-LK", { hour: "2-digit", minute: "2-digit" })}</span>;
}

export function PosTerminal({ user }) {
  const queryClient = useQueryClient();
  const logout = useLogout();
  const storeId = useSessionStore((s) => s.activeStoreId) ?? user.defaultStoreId;
  const store = user.stores.find((s) => s.id === storeId);
  const cart = useCartStore();
  const searchRef = useRef(null);

  const [query, setQuery] = useState("");
  const [activeResult, setActiveResult] = useState(0);
  const [dialog, setDialog] = useState(null); // scanner | customer | held | cash | close | payment | shortcuts | clear
  const [lineEdit, setLineEdit] = useState(null); // { line, mode }
  const [approval, setApproval] = useState(null); // { action, value, title, then }
  const [scanResult, setScanResult] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [payKey, setPayKey] = useState(null);
  const [payError, setPayError] = useState(null);

  const shift = useQuery({ queryKey: posKeys.shift, queryFn: posApi.currentShift, staleTime: 60_000 });
  const registerId = shift.data?.registerId;

  // ----- server quote (authoritative prices) ----------------------------------------------
  const request = useMemo(() => cartRequest(cart, registerId), [cart, registerId]);
  const debouncedRequest = useDebouncedValue(request, 200);
  const quote = useQuery({
    queryKey: ["pos", "quote", debouncedRequest],
    queryFn: () => posApi.quote(debouncedRequest),
    enabled: Boolean(registerId) && debouncedRequest.items.length > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
  const quoteFresh = quote.data && !quote.isFetching && JSON.stringify(request) === JSON.stringify(debouncedRequest);
  const quoteLines = quote.data?.lines ?? [];
  const pendingApprovals = (quote.data?.requiredApprovals ?? []).filter((a) => !a.satisfied);

  // Instant estimate while the quote is in flight (integer cents, same rounding as the server).
  const estimate = centsToString(sumCents(cart.lines.map((l) => extend(l.priceOverride?.unitPrice ?? l.displayPrice, l.quantity))));
  const grandTotal = cart.lines.length === 0 ? "0.00" : quote.data && !quote.error ? quote.data.totals.grandTotal : estimate;

  // ----- search & scanning ------------------------------------------------------------------
  const debouncedQuery = useDebouncedValue(query.trim(), 150);
  const textSearch = debouncedQuery.length >= 2 && !BARCODE_PATTERN.test(debouncedQuery);
  const results = useQuery({
    queryKey: ["products", "pos-search", debouncedQuery],
    queryFn: () => catalogApi.posSearch(debouncedQuery),
    enabled: textSearch,
    staleTime: 15_000,
  });

  const addProduct = useCallback(
    (product) => {
      const key = cart.addProduct(product, product.fractionalQuantity ? "1" : "1");
      if (product.fractionalQuantity) {
        const line = useCartStore.getState().lines.find((l) => l.key === key);
        setLineEdit({ line, mode: "quantity" });
      }
      if (Number(product.quantityOnHand) <= 0) toast.warning(`${product.name}: no stock recorded in the system.`);
      return key;
    },
    [cart],
  );

  const lookupBarcode = useCallback(
    async (code, format) => {
      try {
        const product = await catalogApi.byBarcode(code, format);
        addProduct(product);
        setScanResult({ ok: true, message: `Added ${product.name} · ${formatMoney(product.sellingPrice)}` });
        return true;
      } catch (e) {
        const message =
          e.code === "BARCODE_NOT_FOUND"
            ? `Unknown barcode ${code}.`
            : e.code === "INVALID_BARCODE"
              ? `${code} is not a valid barcode (check digit).`
              : errorMessage(e);
        setScanResult({ ok: false, message });
        toast.error(message, {
          action:
            e.code === "BARCODE_NOT_FOUND" && hasPermission(user, "PRODUCT_MANAGE")
              ? { label: "Register product", onClick: () => window.open(`/products/new?barcode=${encodeURIComponent(code)}`, "_blank") }
              : undefined,
        });
        return false;
      }
    },
    [addProduct, user],
  );

  const submitSearch = async () => {
    const value = query.trim();
    if (!value) return;
    if (BARCODE_PATTERN.test(value) || (!textSearch && value.length > 0 && !results.data?.length)) {
      if (await lookupBarcode(value)) setQuery("");
      return;
    }
    const pick = results.data?.[activeResult];
    if (pick) {
      addProduct(pick);
      setQuery("");
    }
  };

  // ----- line operations --------------------------------------------------------------------
  const selected = cart.lines.find((l) => l.key === cart.selectedKey);

  const voidMutation = useMutation({ mutationFn: posApi.recordVoid });
  const removeWithVoid = async (lines, type, approvalToken) => {
    const index = (l) => cart.lines.indexOf(l);
    const items = lines.map((l) => ({
      productId: l.productId,
      name: l.name,
      quantity: l.quantity,
      amount: quoteLines[index(l)]?.lineTotal ?? centsToString(extend(l.displayPrice, l.quantity)),
    }));
    try {
      await voidMutation.mutateAsync({ registerId, type, reasonCode: type === "CART" ? "CART_CANCELLED" : "LINE_REMOVED", items, approvalToken });
      if (type === "CART") cart.clear();
      else lines.forEach((l) => cart.removeLine(l.key));
    } catch (e) {
      if (e.code === "APPROVAL_REQUIRED") {
        setApproval({
          action: e.details.action,
          value: null,
          title: type === "CART" ? "Manager approval: cancel cart" : "Manager approval: remove line",
          then: (token) => removeWithVoid(lines, type, token),
        });
      } else toast.error(errorMessage(e));
    }
  };

  const hold = useMutation({
    mutationFn: () =>
      posApi.hold({
        registerId,
        customerId: cart.customer?.id ?? null,
        label: cart.customer?.fullName ?? null,
        items: cart.lines.map((l) => ({ productId: l.productId, quantity: l.quantity })),
      }),
    onSuccess: () => {
      cart.clear();
      queryClient.invalidateQueries({ queryKey: posKeys.held });
      toast.success("Cart held. Resume it from Held carts (F9).");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const resumeHeld = async (held) => {
    setDialog(null);
    try {
      const products = await Promise.all(held.items.map((i) => catalogApi.product(i.productId)));
      cart.loadLines(
        held.items.map((item, i) => {
          const p = products[i];
          return {
            productId: p.id,
            name: p.name,
            sku: p.sku,
            barcode: p.barcodes[0]?.barcode,
            unitOfMeasure: p.unitOfMeasure,
            fractional: p.fractionalQuantity,
            allowPriceOverride: p.allowPriceOverride,
            displayPrice: p.sellingPrice,
            quantity: formatQuantity(item.quantity),
            manualDiscount: null,
            priceOverride: null,
          };
        }),
      );
      toast.success("Cart resumed at current prices.");
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  // ----- checkout ---------------------------------------------------------------------------
  const startPayment = () => {
    if (cart.lines.length === 0 || !registerId) return;
    if (!quoteFresh) {
      toast.info("Updating prices… try again in a moment.");
      return;
    }
    if (quote.data.warnings.some((w) => w.code === "INSUFFICIENT_STOCK")) {
      toast.error("Some items are out of stock in the system. Remove them or ask a manager to correct stock.");
      return;
    }
    if (pendingApprovals.length) {
      const a = pendingApprovals[0];
      setApproval({ action: a.action, value: a.value, title: "Manager approval required", description: a.reason, then: (t) => cart.addApprovalToken(t) });
      return;
    }
    setPayError(null);
    setPayKey(newIdempotencyKey()); // one key per payment attempt; network retries reuse it
    setDialog("payment");
  };

  const checkout = useMutation({
    mutationFn: (payments) => posApi.checkout({ ...request, expectedGrandTotal: quote.data.totals.grandTotal, payments }, payKey),
    onSuccess: (sale) => {
      setDialog(null);
      setReceipt(sale);
      cart.clear();
      queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (e) => {
      if (e.code === "QUOTE_MISMATCH") {
        setDialog(null);
        queryClient.invalidateQueries({ queryKey: ["pos", "quote"] });
        toast.warning(e.message);
      } else if (e.code === "APPROVAL_REQUIRED") {
        setDialog(null);
        setApproval({ action: e.details.action, value: e.details.value || null, title: "Manager approval required", description: e.message, then: (t) => cart.addApprovalToken(t) });
      } else if (e.status === 0) {
        setPayError("Connection problem. Press Complete sale again: the retry cannot create a duplicate sale.");
      } else {
        setPayKey(newIdempotencyKey());
        setPayError(errorMessage(e));
      }
    },
  });

  // ----- keyboard -----------------------------------------------------------------------------
  const idx = cart.lines.findIndex((l) => l.key === cart.selectedKey);
  useShortcuts(
    {
      F2: () => setDialog("scanner"),
      F3: () => searchRef.current?.focus(),
      "/": () => searchRef.current?.focus(),
      F4: () => setDialog("customer"),
      F6: () => selected && setLineEdit({ line: selected, mode: "discount" }),
      F8: () => cart.lines.length && hold.mutate(),
      F9: () => setDialog("held"),
      F12: startPayment,
      "Ctrl+Enter": startPayment,
      "+": () => selected && cart.increment(selected.key, 1),
      "-": () => selected && cart.increment(selected.key, -1),
      Delete: () => selected && removeWithVoid([selected], "LINE"),
      ArrowDown: () => cart.lines[idx + 1] && cart.select(cart.lines[idx + 1].key),
      ArrowUp: () => idx > 0 && cart.select(cart.lines[idx - 1].key),
    },
    { enabled: Boolean(shift.data) && !receipt && !lineEdit && !approval },
  );
  useEffect(() => {
    if (!dialog && !receipt && !lineEdit && shift.data) searchRef.current?.focus();
  }, [dialog, receipt, lineEdit, shift.data]);

  // ----- render -------------------------------------------------------------------------------
  if (shift.isPending) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Spinner label="Loading shift" />
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col bg-slate-100">
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center justify-between gap-3 bg-primary-900 px-4 text-primary-foreground">
        <div className="flex min-w-0 items-center gap-4">
          <span className="truncate font-semibold">{store?.name}</span>
          {shift.data && (
            <span className="hidden items-center gap-2 text-sm text-primary-100 md:flex">
              <Badge color="green">Shift open</Badge>
              {shift.data.registerCode} · {shift.data.shiftNumber}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1 text-sm">
          <span className="mr-2 hidden sm:inline">
            <User className="mr-1 inline h-4 w-4" aria-hidden="true" />
            {user.fullName} · <Clock />
          </span>
          {shift.data && hasPermission(user, "CASH_MOVEMENT") && (
            <Button size="sm" variant="ghost" className="text-primary-foreground hover:bg-primary-800" onClick={() => setDialog("cash")}>
              <Banknote className="h-4 w-4" aria-hidden="true" /> <span className="hidden lg:inline">Cash in/out</span>
            </Button>
          )}
          {shift.data && (
            <Button size="sm" variant="ghost" className="text-primary-foreground hover:bg-primary-800" onClick={() => setDialog("close")}>
              <Wallet className="h-4 w-4" aria-hidden="true" /> <span className="hidden lg:inline">Close shift</span>
            </Button>
          )}
          <Button size="sm" variant="ghost" className="text-primary-foreground hover:bg-primary-800" aria-label="Keyboard shortcuts" onClick={() => setDialog("shortcuts")}>
            <Keyboard className="h-4 w-4" />
          </Button>
          {hasAny(user, ["DASHBOARD_VIEW", "SALE_VIEW_OWN", "PRODUCT_VIEW"]) && (
            <Link href="/dashboard">
              <Button size="sm" variant="ghost" className="text-primary-foreground hover:bg-primary-800" aria-label="Back office">
                <LayoutDashboard className="h-4 w-4" />
              </Button>
            </Link>
          )}
          <Button size="sm" variant="ghost" className="text-primary-foreground hover:bg-primary-800" aria-label="Sign out" onClick={() => logout.mutate()}>
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col gap-3 p-3 lg:flex-row">
        {/* Left: search + cart */}
        <section className="flex min-h-0 flex-1 flex-col rounded-lg bg-white shadow-sm">
          <div className="flex gap-2 border-b border-slate-200 p-3">
            <Button size="lg" variant="secondary" onClick={() => setDialog("scanner")} className="shrink-0">
              <ScanBarcode className="h-5 w-5" aria-hidden="true" /> Scan <kbd className="ml-1 hidden rounded bg-white/40 px-1 text-xs sm:inline">F2</kbd>
            </Button>
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <Input
                ref={searchRef}
                data-shortcuts-allowed="true"
                aria-label="Scan or type a barcode, or search products"
                placeholder="Scan / type barcode or search name, SKU (F3)"
                className="h-12 pl-10 text-lg"
                autoComplete="off"
                value={query}
                disabled={!shift.data}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setActiveResult(0);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    submitSearch();
                  } else if (e.key === "ArrowDown" && results.data?.length) {
                    e.preventDefault();
                    setActiveResult((i) => Math.min(i + 1, results.data.length - 1));
                  } else if (e.key === "ArrowUp" && results.data?.length) {
                    e.preventDefault();
                    setActiveResult((i) => Math.max(i - 1, 0));
                  } else if (e.key === "Escape") setQuery("");
                }}
              />
              {textSearch && query && (
                <ul role="listbox" className="absolute z-20 mt-1 max-h-80 w-full overflow-auto rounded-md border border-slate-200 bg-white shadow-lg">
                  {results.isFetching && !results.data && <li className="px-4 py-3 text-sm text-slate-500">Searching…</li>}
                  {results.data?.length === 0 && <li className="px-4 py-3 text-sm text-slate-500">No products found</li>}
                  {results.data?.map((p, i) => (
                    <li
                      key={p.id}
                      role="option"
                      aria-selected={i === activeResult}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        addProduct(p);
                        setQuery("");
                      }}
                      className={cn("flex cursor-pointer items-center justify-between px-4 py-2.5", i === activeResult && "bg-primary-50")}
                    >
                      <span>
                        <span className="block font-medium">{p.name}</span>
                        <span className="font-mono text-xs text-slate-500">{p.sku}</span>
                      </span>
                      <span className="text-right">
                        <span className="block font-semibold tabular">{formatMoney(p.sellingPrice)}</span>
                        <span className="text-xs text-slate-500">{formatQuantity(p.quantityOnHand)} in stock</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Cart */}
          <div className="min-h-0 flex-1 overflow-y-auto">
            {cart.lines.length === 0 ? (
              <div className="flex h-full flex-col items-center justify-center p-10 text-center text-slate-400">
                <ScanBarcode className="h-14 w-14" aria-hidden="true" />
                <p className="mt-3 text-lg font-medium text-slate-500">Scan an item to start</p>
                <p className="text-sm">USB scanners type straight into the search box.</p>
              </div>
            ) : (
              <table className="min-w-full text-sm">
                <thead className="sticky top-0 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-3 py-2 text-left">Item</th>
                    <th className="px-3 py-2 text-center">Qty</th>
                    <th className="px-3 py-2 text-right">Price</th>
                    <th className="hidden px-3 py-2 text-right md:table-cell">Discount</th>
                    <th className="hidden px-3 py-2 text-right xl:table-cell">Tax</th>
                    <th className="px-3 py-2 text-right">Total</th>
                    <th className="px-3 py-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {cart.lines.map((l, i) => {
                    // A stale quote (cart just changed) must not show another item's price.
                    const q = quoteLines[i]?.productId === l.productId ? quoteLines[i] : undefined;
                    const discount = q ? centsToString(sumCents([q.promoDiscount, q.manualDiscount, q.allocatedTransactionDiscount].map(toCents))) : "0.00";
                    const isSelected = l.key === cart.selectedKey;
                    return (
                      <tr
                        key={l.key}
                        onClick={() => cart.select(l.key)}
                        aria-selected={isSelected}
                        className={cn("cursor-pointer", isSelected ? "bg-primary-50" : "hover:bg-slate-50", q && !q.stockSufficient && "bg-red-50")}
                      >
                        <td className="px-3 py-2.5">
                          <p className="font-medium text-slate-900">{l.name}</p>
                          <p className="font-mono text-xs text-slate-500">
                            {l.barcode || l.sku}
                            {q?.promotionName && <Badge color="secondary" className="ml-2">{q.promotionName}</Badge>}
                            {l.manualDiscount && <Badge color="primary" className="ml-2">discount</Badge>}
                            {l.priceOverride && <Badge color="amber" className="ml-2">price adj.</Badge>}
                            {q && !q.stockSufficient && <Badge color="red" className="ml-2">out of stock</Badge>}
                          </p>
                        </td>
                        <td className="px-3 py-2.5">
                          <div className="flex items-center justify-center gap-1">
                            {!l.fractional && (
                              <Button size="icon" variant="outline" aria-label={`Decrease ${l.name}`} onClick={(e) => (e.stopPropagation(), cart.increment(l.key, -1))} disabled={Number(l.quantity) <= 1}>
                                <Minus className="h-4 w-4" />
                              </Button>
                            )}
                            <button
                              type="button"
                              className="min-w-12 rounded px-2 py-1 text-center font-semibold tabular hover:bg-slate-100"
                              onClick={(e) => (e.stopPropagation(), setLineEdit({ line: l, mode: "quantity" }))}
                              aria-label={`Quantity ${l.quantity}, edit`}
                            >
                              {formatQuantity(l.quantity)}
                              {l.fractional && <span className="ml-0.5 text-xs font-normal">{l.unitOfMeasure.toLowerCase()}</span>}
                            </button>
                            {!l.fractional && (
                              <Button size="icon" variant="outline" aria-label={`Increase ${l.name}`} onClick={(e) => (e.stopPropagation(), cart.increment(l.key, 1))}>
                                <Plus className="h-4 w-4" />
                              </Button>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-2.5 text-right tabular">{formatMoney(q?.unitPrice ?? l.priceOverride?.unitPrice ?? l.displayPrice)}</td>
                        <td className="hidden px-3 py-2.5 text-right tabular text-secondary-700 md:table-cell">{Number(discount) > 0 ? `-${formatMoney(discount)}` : ""}</td>
                        <td className="hidden px-3 py-2.5 text-right tabular text-slate-500 xl:table-cell">{q ? formatMoney(q.taxAmount) : ""}</td>
                        <td className="px-3 py-2.5 text-right text-base font-semibold tabular">
                          {formatMoney(q?.lineTotal ?? centsToString(extend(l.priceOverride?.unitPrice ?? l.displayPrice, l.quantity)))}
                        </td>
                        <td className="px-2 py-2.5">
                          <div className="flex justify-end gap-0.5">
                            {hasPermission(user, "DISCOUNT_APPLY") && (
                              <Button size="icon" variant="ghost" aria-label={`Discount ${l.name}`} onClick={(e) => (e.stopPropagation(), setLineEdit({ line: l, mode: "discount" }))}>
                                <Percent className="h-4 w-4" />
                              </Button>
                            )}
                            {l.allowPriceOverride && (
                              <Button size="icon" variant="ghost" aria-label={`Override price of ${l.name}`} onClick={(e) => (e.stopPropagation(), setLineEdit({ line: l, mode: "price" }))}>
                                <Tag className="h-4 w-4" />
                              </Button>
                            )}
                            <Button size="icon" variant="ghost" aria-label={`Remove ${l.name}`} onClick={(e) => (e.stopPropagation(), removeWithVoid([l], "LINE"))}>
                              <Trash2 className="h-4 w-4 text-red-600" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </section>

        {/* Right: customer, totals, actions */}
        <aside className="flex w-full shrink-0 flex-col gap-3 lg:w-96">
          <div className="rounded-lg bg-white p-4 shadow-sm">
            {cart.customer ? (
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-xs text-slate-500">Customer</p>
                  <p className="font-semibold">{cart.customer.fullName}</p>
                  <p className="text-xs text-slate-500">
                    {cart.customer.cardNumber} · {cart.customer.balance ?? cart.customer.pointsBalance ?? 0} pts
                    {quote.data?.loyalty?.pointsToEarn > 0 && <span className="text-emerald-700"> · +{quote.data.loyalty.pointsToEarn} on this sale</span>}
                  </p>
                </div>
                <Button size="icon" variant="ghost" aria-label="Remove customer" onClick={() => cart.setCustomer(null)}>
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ) : (
              <Button variant="outline" className="w-full" onClick={() => setDialog("customer")} disabled={!shift.data}>
                <User className="h-4 w-4" aria-hidden="true" /> Add customer / loyalty <kbd className="ml-auto text-xs text-slate-400">F4</kbd>
              </Button>
            )}
          </div>

          <div className="flex flex-1 flex-col rounded-lg bg-white p-4 shadow-sm">
            <dl className="space-y-1.5 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Subtotal</dt>
                <dd className="tabular">{formatMoney(quote.data?.totals.subtotal ?? estimate)}</dd>
              </div>
              <div className="flex justify-between text-secondary-700">
                <dt>Discounts</dt>
                <dd className="tabular">-{formatMoney(quote.data?.totals.discountTotal ?? "0")}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">{quote.data?.pricesIncludeTax === false ? "Tax" : "Tax included"}</dt>
                <dd className="tabular">{formatMoney(quote.data?.totals.taxTotal ?? "0")}</dd>
              </div>
              <div className="flex justify-between text-slate-500">
                <dt>Items</dt>
                <dd className="tabular">{quote.data?.totals.itemCount ?? cart.lines.length}</dd>
              </div>
            </dl>
            <div className="mt-4 rounded-lg bg-primary-50 px-4 py-3">
              <p className="text-xs font-medium uppercase tracking-wide text-primary-800">Total</p>
              <p className="text-5xl font-bold tabular text-primary-900" aria-live="polite">
                {formatMoney(grandTotal)}
              </p>
              {quote.isFetching && <p className="text-xs text-primary-700">Updating prices…</p>}
              {quote.error && <p className="text-xs text-red-600">{errorMessage(quote.error)}</p>}
            </div>

            {pendingApprovals.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  const a = pendingApprovals[0];
                  setApproval({ action: a.action, value: a.value, title: "Manager approval required", description: a.reason, then: (t) => cart.addApprovalToken(t) });
                }}
                className="mt-3 flex items-center gap-2 rounded-md border border-secondary-300 bg-secondary-50 px-3 py-2 text-left text-sm text-secondary-900"
              >
                <ShieldCheck className="h-4 w-4 shrink-0" aria-hidden="true" /> {pendingApprovals[0].reason} Tap to approve.
              </button>
            )}

            <div className="mt-auto grid grid-cols-2 gap-2 pt-4">
              <Button variant="outline" onClick={() => hold.mutate()} disabled={!cart.lines.length || hold.isPending}>
                <PauseCircle className="h-4 w-4" aria-hidden="true" /> Hold <kbd className="text-xs text-slate-400">F8</kbd>
              </Button>
              <Button variant="outline" onClick={() => setDialog("held")} disabled={!shift.data}>
                <PlayCircle className="h-4 w-4" aria-hidden="true" /> Held <kbd className="text-xs text-slate-400">F9</kbd>
              </Button>
              <Button variant="outline" className="col-span-2" onClick={() => setDialog("clear")} disabled={!cart.lines.length}>
                <Trash2 className="h-4 w-4 text-red-600" aria-hidden="true" /> Cancel sale
              </Button>
              <Button size="xl" variant="success" className="col-span-2" onClick={startPayment} disabled={!cart.lines.length || !shift.data}>
                Pay {formatMoney(grandTotal)} <kbd className="ml-2 rounded bg-white/20 px-1 text-xs">F12</kbd>
              </Button>
            </div>
          </div>
        </aside>
      </div>

      {/* Dialogs */}
      {!shift.data && <OpenShiftDialog storeId={storeId} />}
      <ScannerDialog open={dialog === "scanner"} onClose={() => setDialog(null)} onScan={(code, format) => lookupBarcode(code, format)} lastResult={scanResult} />
      {dialog === "customer" && (
        <CustomerDialog
          canCreate={hasPermission(user, "CUSTOMER_MANAGE")}
          onClose={() => setDialog(null)}
          onPick={(c) => {
            cart.setCustomer({ ...c, balance: c.pointsBalance ?? c.balance ?? 0, balanceValue: c.pointsValue ?? c.balanceValue ?? "0.00" });
            setDialog(null);
            toast.success(`${c.fullName} attached.`);
          }}
        />
      )}
      {dialog === "held" && <HeldCartsDialog onClose={() => setDialog(null)} onResume={resumeHeld} />}
      {dialog === "cash" && shift.data && <CashMovementDialog shift={shift.data} onClose={() => setDialog(null)} />}
      {dialog === "close" && shift.data && (
        <CloseShiftDialog
          shift={shift.data}
          onClose={() => setDialog(null)}
          onClosed={(summary) => {
            setDialog(null);
            cart.clear();
            queryClient.setQueryData(posKeys.shift, null);
            toast.success(`Shift closed. Variance ${formatMoney(summary.shift.variance)}.`);
          }}
        />
      )}
      <PaymentDialog
        open={dialog === "payment"}
        total={quote.data?.totals.grandTotal ?? "0.00"}
        customer={cart.customer}
        paying={checkout.isPending}
        error={payError}
        onClose={() => setDialog(null)}
        onPay={(payments) => checkout.mutate(payments)}
      />
      {dialog === "shortcuts" && (
        <Modal open onOpenChange={(o) => !o && setDialog(null)} title="Keyboard shortcuts" size="sm">
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            {POS_SHORTCUTS.map(([k, v]) => (
              <div key={k} className="contents">
                <dt>
                  <kbd className="rounded border border-slate-300 bg-slate-50 px-1.5 py-0.5 font-mono text-xs">{k}</kbd>
                </dt>
                <dd>{v}</dd>
              </div>
            ))}
          </dl>
        </Modal>
      )}
      <ConfirmDialog
        open={dialog === "clear"}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Cancel this sale?"
        description="All items are removed. The cancellation is recorded for loss-prevention reporting and may need a manager."
        confirmLabel="Cancel sale"
        onConfirm={() => {
          setDialog(null);
          removeWithVoid(cart.lines, "CART");
        }}
      />
      {lineEdit && (
        <LineEditDialog
          line={lineEdit.line}
          mode={lineEdit.mode}
          onClose={() => setLineEdit(null)}
          onSave={(patch) => {
            if ("quantity" in patch) cart.setQuantity(lineEdit.line.key, patch.quantity);
            if ("manualDiscount" in patch) cart.setLineDiscount(lineEdit.line.key, patch.manualDiscount);
            if ("priceOverride" in patch) cart.setPriceOverride(lineEdit.line.key, patch.priceOverride);
            setLineEdit(null);
          }}
        />
      )}
      {approval && (
        <ApprovalDialog
          action={approval.action}
          value={approval.value}
          title={approval.title}
          description={approval.description}
          registerId={registerId}
          onClose={() => setApproval(null)}
          onApproved={(token, grant) => {
            const then = approval.then;
            setApproval(null);
            toast.success(`Approved by ${grant.approver.fullName}.`);
            then?.(token);
          }}
        />
      )}
      {receipt && (
        <ReceiptDialog
          sale={receipt}
          onClose={() => setReceipt(null)}
          onNewSale={() => {
            setReceipt(null);
            searchRef.current?.focus();
          }}
        />
      )}
    </div>
  );
}
