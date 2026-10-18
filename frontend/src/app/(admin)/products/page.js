"use client";

import Link from "next/link";
import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ArrowDownUp, Package, Plus, Search } from "lucide-react";
import { PageHeader, Card, Table, Th, Td, Pagination } from "@/components/ui/layout";
import { Checkbox, Input, Select } from "@/components/ui/form";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { catalogApi, catalogKeys } from "@/features/catalog/api";
import { hasPermission } from "@/lib/permissions";
import { useDebouncedValue } from "@/lib/hooks";
import { formatMoney, formatQuantity } from "@/lib/format";

const SORTS = [
  ["name,asc", "Name A-Z"],
  ["name,desc", "Name Z-A"],
  ["sellingPrice,asc", "Price: low to high"],
  ["sellingPrice,desc", "Price: high to low"],
  ["updatedAt,desc", "Recently updated"],
];

export default function ProductsPage() {
  const { data: me } = useMe();
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [status, setStatus] = useState("true");
  const [lowStock, setLowStock] = useState(false);
  const [sort, setSort] = useState("name,asc");
  const [page, setPage] = useState(0);
  const debouncedQ = useDebouncedValue(q);
  const reset = (fn) => (e) => {
    fn(e.target.type === "checkbox" ? e.target.checked : e.target.value);
    setPage(0);
  };

  const params = {
    q: debouncedQ || undefined,
    categoryId: categoryId || undefined,
    active: status === "" ? undefined : status,
    lowStock: lowStock || undefined,
    sort,
    page,
    size: 25,
  };
  const { data, error, isPending, isFetching, refetch } = useQuery({
    queryKey: catalogKeys.products(params),
    queryFn: () => catalogApi.products(params),
    placeholderData: keepPreviousData,
  });
  const categories = useQuery({ queryKey: catalogKeys.categories, queryFn: () => catalogApi.categories() });
  const showCost = hasPermission(me, "PRODUCT_VIEW_COST");

  return (
    <AuthGate anyOf={["PRODUCT_VIEW"]}>
      <PageHeader
        title="Products"
        description="Catalog, prices and stock at the active store."
        actions={
          hasPermission(me, "PRODUCT_MANAGE") && (
            <Link href="/products/new">
              <Button>
                <Plus className="h-4 w-4" aria-hidden="true" /> New product
              </Button>
            </Link>
          )
        }
      />
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[1fr_auto_auto_auto_auto] md:items-center">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
              className="pl-9"
              placeholder="Search name, SKU, brand or scan a barcode"
              aria-label="Search products"
              value={q}
              onChange={reset(setQ)}
            />
          </div>
          <Select aria-label="Category" value={categoryId} onChange={reset(setCategoryId)} className="md:w-52">
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.path}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={reset(setStatus)} className="md:w-32">
            <option value="true">Active</option>
            <option value="false">Inactive</option>
            <option value="">All</option>
          </Select>
          <label className="flex items-center gap-2">
            <ArrowDownUp className="h-4 w-4 text-slate-400" aria-hidden="true" />
            <span className="sr-only">Sort</span>
            <Select value={sort} onChange={reset(setSort)} className="md:w-44">
              {SORTS.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
          </label>
          <Checkbox label="Low stock only" checked={lowStock} onChange={reset(setLowStock)} />
        </div>
        {isPending ? (
          <TableSkeleton rows={10} cols={6} />
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : data.content.length === 0 ? (
          <EmptyState icon={Package} title="No products match" description="Change the filters or create a product." />
        ) : (
          <div className={isFetching ? "opacity-70 transition-opacity" : undefined}>
            <Table>
              <thead>
                <tr>
                  <Th>Product</Th>
                  <Th>Category</Th>
                  <Th align="right">Price</Th>
                  {showCost && <Th align="right">Cost</Th>}
                  <Th align="right">In stock</Th>
                  <Th>Tax</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/products/${p.id}`} className="font-medium text-primary-700 hover:underline">
                        {p.name}
                      </Link>
                      <p className="font-mono text-xs text-slate-500">
                        {p.sku}
                        {p.primaryBarcode && ` · ${p.primaryBarcode}`}
                      </p>
                    </Td>
                    <Td className="text-slate-600">{p.category.name}</Td>
                    <Td align="right" className="font-medium">
                      {formatMoney(p.sellingPrice)}
                    </Td>
                    {showCost && <Td align="right" className="text-slate-500">{formatMoney(p.costPrice, { decimals: 2 })}</Td>}
                    <Td align="right">
                      <span className={p.stock.lowStock ? "font-semibold text-red-600" : undefined}>
                        {formatQuantity(p.stock.quantityOnHand)}
                      </span>
                      <span className="ml-1 text-xs text-slate-400">{p.unitOfMeasure.toLowerCase()}</span>
                      {p.stock.lowStock && (
                        <Badge color="red" className="ml-2">
                          low
                        </Badge>
                      )}
                    </Td>
                    <Td className="text-xs text-slate-500">{p.taxRate.code}</Td>
                    <Td>{p.active ? <Badge color="green">active</Badge> : <Badge>inactive</Badge>}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination {...data} onPageChange={setPage} />
          </div>
        )}
      </Card>
    </AuthGate>
  );
}
