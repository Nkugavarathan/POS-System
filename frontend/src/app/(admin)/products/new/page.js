"use client";

import { Suspense, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader, Card } from "@/components/ui/layout";
import { AuthGate } from "@/features/auth/auth-gate";
import { ProductForm } from "@/features/catalog/product-form";
import { catalogApi } from "@/features/catalog/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";

function NewProductContent() {
  const router = useRouter();
  const params = useSearchParams();
  const queryClient = useQueryClient();
  const setErrorRef = useRef(null);
  const create = useMutation({
    mutationFn: catalogApi.createProduct,
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      toast.success(`${p.name} created. Add opening stock from Inventory.`);
      router.push(`/products/${p.id}`);
    },
    onError: (e) => {
      const setError = setErrorRef.current || (() => {});
      if (e.code === "DUPLICATE_RESOURCE" && e.details?.field) {
        setError(e.details.field, { type: "server", message: e.message });
        return;
      }
      if (e.code === "INVALID_BARCODE") {
        setError("barcodes", { type: "server", message: e.message });
        return;
      }
      if (!applyFieldErrors(e, setError)) toast.error(errorMessage(e));
    },
  });
  return (
    <AuthGate anyOf={["PRODUCT_MANAGE"]}>
      <div className="mx-auto max-w-4xl">
        <PageHeader title="New product" />
        <Card className="p-5">
          <ProductForm defaultBarcode={params.get("barcode") ?? ""} onSubmit={(v) => create.mutate(v)} submitting={create.isPending} setErrorRef={setErrorRef} />
        </Card>
      </div>
    </AuthGate>
  );
}

export default function NewProductPage() {
  return (
    <Suspense>
      <NewProductContent />
    </Suspense>
  );
}
