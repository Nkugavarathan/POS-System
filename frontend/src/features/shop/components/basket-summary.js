"use client";

import { Leaf, Wallet } from "lucide-react";
import { formatMoney } from "@/lib/format";
import { compare } from "@/lib/money";

/** Totals panel from a server quote (or an order). */
export function BasketSummary({ quote, children, title = "Summary" }) {
  const hasSavings = quote && compare(quote.savings ?? "0", "0") > 0;
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">{title}</h2>
      {quote ? (
        <dl className="mt-4 space-y-2 text-sm">
          <div className="flex justify-between text-slate-600">
            <dt>Items</dt>
            <dd className="tabular">Rs {formatMoney(quote.subtotal)}</dd>
          </div>
          {compare(quote.discountTotal, "0") > 0 && (
            <div className="flex justify-between text-red-600">
              <dt>Offers &amp; rescue deals</dt>
              <dd className="tabular">− Rs {formatMoney(quote.discountTotal)}</dd>
            </div>
          )}
          <div className="flex justify-between text-slate-500">
            <dt>Tax {quote.pricesIncludeTax ? "(included)" : ""}</dt>
            <dd className="tabular">Rs {formatMoney(quote.taxTotal)}</dd>
          </div>
          <div className="flex items-baseline justify-between border-t border-slate-100 pt-3">
            <dt className="font-semibold text-slate-900">Estimated total</dt>
            <dd className="text-2xl font-extrabold tabular text-slate-900">Rs {formatMoney(quote.grandTotal)}</dd>
          </div>
          {hasSavings && (
            <p className="flex items-center gap-1.5 rounded-xl bg-secondary-50 px-3 py-2 text-xs font-semibold text-secondary-900">
              <Leaf className="h-4 w-4" aria-hidden="true" /> You&rsquo;re saving Rs {formatMoney(quote.savings)} on this basket
            </p>
          )}
          <p className="flex items-start gap-1.5 pt-1 text-xs text-slate-500">
            <Wallet className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            Nothing to pay now. You pay at the counter when you collect — the final amount can only go down if something
            is swapped or unavailable.
          </p>
        </dl>
      ) : (
        <div className="mt-4 space-y-2">
          <div className="h-4 animate-pulse rounded bg-slate-100" />
          <div className="h-4 w-2/3 animate-pulse rounded bg-slate-100" />
          <div className="h-8 animate-pulse rounded bg-slate-100" />
        </div>
      )}
      {children && <div className="mt-5">{children}</div>}
    </div>
  );
}
