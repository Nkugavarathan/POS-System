"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/form";
import { catalogApi, catalogKeys, UNITS } from "./api";

const decimal = (places, label) =>
  z
    .string()
    .trim()
    .regex(new RegExp(`^\\d{1,12}(\\.\\d{1,${places}})?$`), `${label}: up to ${places} decimal places`);

export const productSchema = z
  .object({
    sku: z.string().trim().toUpperCase().regex(/^[A-Z0-9][A-Z0-9-]{1,39}$/, "2-40 upper-case letters, digits or dashes"),
    name: z.string().trim().min(2, "At least 2 characters").max(150),
    description: z.string().max(1000).optional(),
    categoryId: z.coerce.number({ message: "Choose a category" }).int().positive("Choose a category"),
    brand: z.string().max(80).optional(),
    unitOfMeasure: z.enum(["EACH", "PACK", "BOTTLE", "KG", "G", "L", "ML"]),
    costPrice: decimal(4, "Cost"),
    sellingPrice: decimal(2, "Price"),
    taxRateId: z.coerce.number().int().positive("Choose a tax rate"),
    reorderLevel: decimal(3, "Reorder level"),
    trackBatches: z.boolean(),
    trackExpiry: z.boolean(),
    allowPriceOverride: z.boolean(),
    imageUrl: z.union([z.literal(""), z.string().url().startsWith("https://", "Must start with https://")]).optional(),
    barcodes: z.string().optional(),
  })
  .refine((v) => !v.trackExpiry || v.trackBatches, { path: ["trackExpiry"], message: "Expiry tracking needs batch tracking" })
  .refine((v) => ["KG", "G", "L", "ML"].includes(v.unitOfMeasure) || !v.reorderLevel.includes(".") || /\.0+$/.test(v.reorderLevel), {
    path: ["reorderLevel"],
    message: "Whole numbers for this unit",
  });

/** Create/edit form. Barcodes are only entered on create; manage them on the product page afterwards. */
export function ProductForm({ product, onSubmit, submitting, canEditPrices = true, setErrorRef, defaultBarcode = "" }) {
  const isEdit = Boolean(product);
  const categories = useQuery({ queryKey: catalogKeys.categories, queryFn: () => catalogApi.categories({ active: true }) });
  const taxRates = useQuery({ queryKey: catalogKeys.taxRates, queryFn: catalogApi.taxRates });
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(productSchema),
    defaultValues: product
      ? {
          sku: product.sku,
          name: product.name,
          description: product.description ?? "",
          categoryId: product.category.id,
          brand: product.brand ?? "",
          unitOfMeasure: product.unitOfMeasure,
          costPrice: product.costPrice ?? "0",
          sellingPrice: product.sellingPrice,
          taxRateId: product.taxRate.id,
          reorderLevel: product.reorderLevel,
          trackBatches: product.trackBatches,
          trackExpiry: product.trackExpiry,
          allowPriceOverride: product.allowPriceOverride,
          imageUrl: product.imageUrl ?? "",
          barcodes: "",
        }
      : {
          sku: "",
          name: "",
          description: "",
          brand: "",
          unitOfMeasure: "EACH",
          costPrice: "0",
          sellingPrice: "",
          reorderLevel: "0",
          trackBatches: false,
          trackExpiry: false,
          allowPriceOverride: false,
          imageUrl: "",
          barcodes: defaultBarcode,
        },
  });
  if (setErrorRef) setErrorRef.current = setError;

  const submit = handleSubmit((v) =>
    onSubmit({
      ...v,
      description: v.description || null,
      brand: v.brand || null,
      imageUrl: v.imageUrl || null,
      barcodes: isEdit
        ? []
        : (v.barcodes || "")
            .split(/[\s,]+/)
            .map((b) => b.trim())
            .filter(Boolean),
      version: product?.version,
    }),
  );

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-2">
        <Field label="SKU" error={errors.sku?.message} required hint="Your internal stock code">
          {(a) => <Input {...a} {...register("sku")} className="uppercase" />}
        </Field>
        <Field label="Product name" error={errors.name?.message} required>
          {(a) => <Input {...a} {...register("name")} />}
        </Field>
        <Field label="Category" error={errors.categoryId?.message} required>
          {(a) => (
            <Select {...a} {...register("categoryId")}>
              <option value="">Choose…</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.path}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Brand" error={errors.brand?.message}>
          {(a) => <Input {...a} {...register("brand")} />}
        </Field>
        <Field label="Description" className="sm:col-span-2" error={errors.description?.message}>
          {(a) => <Textarea {...a} rows={2} {...register("description")} />}
        </Field>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <Field label="Sold by" error={errors.unitOfMeasure?.message} required>
          {(a) => (
            <Select {...a} {...register("unitOfMeasure")}>
              {UNITS.map((u) => (
                <option key={u.value} value={u.value}>
                  {u.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field
          label="Selling price"
          error={errors.sellingPrice?.message}
          required
          hint={!canEditPrices ? "Requires price permission" : "Per unit, as shown on the shelf"}
        >
          {(a) => <Input {...a} inputMode="decimal" disabled={isEdit && !canEditPrices} {...register("sellingPrice")} />}
        </Field>
        <Field label="Cost price" error={errors.costPrice?.message} hint="Standard / last purchase cost">
          {(a) => <Input {...a} inputMode="decimal" disabled={isEdit && !canEditPrices} {...register("costPrice")} />}
        </Field>
        <Field label="Tax rate" error={errors.taxRateId?.message} required>
          {(a) => (
            <Select {...a} {...register("taxRateId")}>
              <option value="">Choose…</option>
              {taxRates.data
                ?.filter((t) => t.active || t.id === product?.taxRate?.id)
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({t.ratePercent}%)
                  </option>
                ))}
            </Select>
          )}
        </Field>
        <Field label="Reorder level" error={errors.reorderLevel?.message} hint="Low-stock alert at or below this">
          {(a) => <Input {...a} inputMode="decimal" {...register("reorderLevel")} />}
        </Field>
        <Field label="Image URL" error={errors.imageUrl?.message} hint="Optional, https only">
          {(a) => <Input {...a} type="url" {...register("imageUrl")} />}
        </Field>
      </section>

      <section className="flex flex-wrap gap-x-6 gap-y-2">
        <Checkbox label="Track batches" {...register("trackBatches")} />
        <Checkbox label="Track expiry dates" {...register("trackExpiry")} />
        <Checkbox label="Allow price override at the till (with approval)" {...register("allowPriceOverride")} />
        {errors.trackExpiry && <p className="w-full text-xs font-medium text-red-600">{errors.trackExpiry.message}</p>}
      </section>

      {!isEdit && (
        <Field
          label="Barcodes"
          error={errors.barcodes?.message}
          hint="One or more EAN-13, EAN-8, UPC-A/E or internal codes, separated by spaces or commas. Check digits are verified."
        >
          {(a) => <Input {...a} {...register("barcodes")} placeholder="e.g. 4791234567890" />}
        </Field>
      )}

      <div className="flex justify-end">
        <Button type="submit" loading={submitting}>
          {isEdit ? "Save product" : "Create product"}
        </Button>
      </div>
    </form>
  );
}
