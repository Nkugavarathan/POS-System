"use client";

import { Suspense, useMemo } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, SearchX, SlidersHorizontal } from "lucide-react";
import { cn } from "@/lib/cn";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useActiveStore } from "@/features/shop/hooks";
import { ProductCard, ProductGridSkeleton } from "@/features/shop/components/product-card";
import { ErrorState } from "@/components/ui/feedback";

const SORTS = [
  { value: "name,asc", label: "Name A-Z" },
  { value: "sellingPrice,asc", label: "Price: low to high" },
  { value: "sellingPrice,desc", label: "Price: high to low" },
];

function findCategory(tree, id) {
  for (const c of tree || []) {
    if (c.id === id) return { node: c, parent: null };
    for (const child of c.children || []) if (child.id === id) return { node: child, parent: c };
  }
  return null;
}

function Browse() {
  const params = useSearchParams();
  const router = useRouter();
  const { storeId } = useActiveStore();
  const q = params.get("q") || "";
  const categoryId = params.get("category") ? Number(params.get("category")) : null;
  const sort = params.get("sort") || "name,asc";
  const page = Number(params.get("page") || 0);

  const categories = useQuery({ queryKey: shopKeys.categories, queryFn: shopApi.categories, staleTime: 10 * 60_000 });
  const query = { storeId, q: q || undefined, categoryId: categoryId || undefined, sort, page, size: 24 };
  const products = useQuery({
    queryKey: shopKeys.products(query),
    queryFn: () => shopApi.products(query),
    enabled: Boolean(storeId),
    placeholderData: keepPreviousData,
  });

  const current = useMemo(() => findCategory(categories.data, categoryId), [categories.data, categoryId]);

  function update(next) {
    const sp = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(next)) {
      if (v === null || v === undefined || v === "") sp.delete(k);
      else sp.set(k, String(v));
    }
    if (!("page" in next)) sp.delete("page");
    router.push(`/shop/browse?${sp.toString()}`);
  }

  const title = q ? `Results for “${q}”` : current?.node.name || "All products";
  const data = products.data;

  return (
    <div className="mx-auto grid max-w-7xl gap-8 px-4 py-8 lg:grid-cols-[230px_1fr]">
      <aside className="hidden lg:block" aria-label="Departments">
        <p className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-400">Departments</p>
        <ul className="space-y-0.5 text-sm">
          <li>
            <button
              type="button"
              onClick={() => update({ category: null })}
              className={cn(
                "w-full rounded-lg px-3 py-2 text-left font-medium",
                !categoryId ? "bg-primary-50 text-primary-800" : "text-slate-700 hover:bg-slate-100",
              )}
            >
              Everything
            </button>
          </li>
          {(categories.data || []).map((c) => {
            const open = categoryId === c.id || current?.parent?.id === c.id;
            return (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => update({ category: c.id })}
                  className={cn(
                    "w-full rounded-lg px-3 py-2 text-left font-medium",
                    categoryId === c.id ? "bg-primary-50 text-primary-800" : "text-slate-700 hover:bg-slate-100",
                  )}
                >
                  {c.name}
                </button>
                {open && c.children?.length > 0 && (
                  <ul className="mb-1 ml-3 border-l border-slate-200 pl-2">
                    {c.children.map((child) => (
                      <li key={child.id}>
                        <button
                          type="button"
                          onClick={() => update({ category: child.id })}
                          className={cn(
                            "flex w-full justify-between rounded-lg px-3 py-1.5 text-left",
                            categoryId === child.id ? "font-semibold text-primary-800" : "text-slate-600 hover:text-slate-900",
                          )}
                        >
                          {child.name}
                          <span className="text-xs text-slate-400">{child.productCount}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}
        </ul>
      </aside>

      <section>
        <nav className="text-xs text-slate-500" aria-label="Breadcrumb">
          <Link href="/shop" className="hover:text-slate-800">Home</Link>
          <span className="mx-1.5">/</span>
          {current?.parent && (
            <>
              <button type="button" onClick={() => update({ category: current.parent.id })} className="hover:text-slate-800">
                {current.parent.name}
              </button>
              <span className="mx-1.5">/</span>
            </>
          )}
          <span className="text-slate-800">{title}</span>
        </nav>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900">{title}</h1>
            {data && <p className="text-sm text-slate-500">{data.totalElements.toLocaleString()} products</p>}
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
            <span className="sr-only">Sort by</span>
            <select
              value={sort}
              onChange={(e) => update({ sort: e.target.value })}
              className="h-10 rounded-full border border-slate-200 bg-white px-3 text-sm font-medium focus:border-primary focus:outline-none"
            >
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        {current?.node.children?.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2 lg:hidden">
            {current.node.children.map((child) => (
              <button
                key={child.id}
                type="button"
                onClick={() => update({ category: child.id })}
                className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700"
              >
                {child.name}
              </button>
            ))}
          </div>
        )}

        <div className={cn("mt-6 transition-opacity", products.isFetching && products.isPlaceholderData && "opacity-60")}>
          {products.isError ? (
            <ErrorState error={products.error} onRetry={products.refetch} />
          ) : !data ? (
            <ProductGridSkeleton count={12} />
          ) : data.content.length === 0 ? (
            <div className="flex flex-col items-center rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
              <SearchX className="h-10 w-10 text-slate-300" aria-hidden="true" />
              <p className="mt-3 font-semibold text-slate-800">Nothing matches that</p>
              <p className="mt-1 text-sm text-slate-500">Try another word, or browse a department.</p>
              <Link href="/shop/browse" className="mt-4 text-sm font-semibold text-primary-700 hover:underline">
                Clear search
              </Link>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-4">
              {data.content.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          )}
        </div>

        {data && data.totalPages > 1 && (
          <div className="mt-8 flex items-center justify-center gap-3">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => update({ page: page - 1 })}
              className="inline-flex h-10 items-center gap-1 rounded-full border border-slate-200 bg-white px-4 text-sm font-medium disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Previous
            </button>
            <span className="text-sm text-slate-500">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= data.totalPages}
              onClick={() => update({ page: page + 1 })}
              className="inline-flex h-10 items-center gap-1 rounded-full border border-slate-200 bg-white px-4 text-sm font-medium disabled:opacity-40"
            >
              Next <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        )}
      </section>
    </div>
  );
}

export default function BrowsePage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-7xl px-4 py-8"><ProductGridSkeleton count={12} /></div>}>
      <Browse />
    </Suspense>
  );
}
