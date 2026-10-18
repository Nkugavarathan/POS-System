"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronRight, FolderTree, Pencil, Plus } from "lucide-react";
import { PageHeader, Card } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { ConfirmDialog, Modal } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { catalogApi, catalogKeys } from "@/features/catalog/api";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";

function CategoryDialog({ category, categories, onClose }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    code: category?.code ?? "",
    name: category?.name ?? "",
    parentId: category?.parentId ?? "",
    description: category?.description ?? "",
    sortOrder: category?.sortOrder ?? 0,
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const save = useMutation({
    mutationFn: () => {
      const body = {
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        parentId: form.parentId ? Number(form.parentId) : null,
        description: form.description || null,
        sortOrder: Number(form.sortOrder) || 0,
        version: category?.version,
      };
      return category ? catalogApi.updateCategory(category.id, body) : catalogApi.createCategory(body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.categories });
      toast.success("Category saved.");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const parents = categories.filter((c) => c.id !== category?.id && !c.path.startsWith(`${category?.path} /`));
  const codeValid = /^[A-Z0-9_-]{2,30}$/.test(form.code.trim().toUpperCase());
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={category ? `Edit ${category.name}` : "New category"}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!codeValid || !form.name.trim()}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Code" hint="e.g. DAIRY" error={form.code && !codeValid ? "2-30 letters, digits, _ or -" : null} required>
          {(a) => <Input {...a} value={form.code} onChange={set("code")} maxLength={30} />}
        </Field>
        <Field label="Name" required>
          {(a) => <Input {...a} value={form.name} onChange={set("name")} maxLength={100} />}
        </Field>
        <Field label="Parent category">
          {(a) => (
            <Select {...a} value={form.parentId} onChange={set("parentId")}>
              <option value="">(top level)</option>
              {parents.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.path}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Sort order">
          {(a) => <Input {...a} type="number" min="0" value={form.sortOrder} onChange={set("sortOrder")} />}
        </Field>
        <Field label="Description" className="sm:col-span-2">
          {(a) => <Textarea {...a} rows={2} value={form.description} onChange={set("description")} maxLength={255} />}
        </Field>
      </div>
    </Modal>
  );
}

function Tree({ nodes, byId, depth, canEdit, onEdit, onToggle }) {
  return nodes.map((node) => (
    <div key={node.id}>
      <div
        className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-2.5 hover:bg-slate-50"
        style={{ paddingLeft: `${16 + depth * 24}px` }}
      >
        <div className="flex min-w-0 items-center gap-2">
          {depth > 0 && <ChevronRight className="h-3.5 w-3.5 text-slate-300" aria-hidden="true" />}
          <span className={node.active ? "font-medium text-slate-800" : "font-medium text-slate-400 line-through"}>
            {node.name}
          </span>
          <span className="font-mono text-xs text-slate-400">{node.code}</span>
          <Badge color="gray">{node.productCount} products</Badge>
          {!node.active && <Badge color="red">inactive</Badge>}
        </div>
        {canEdit && (
          <div className="flex shrink-0 gap-1">
            <Button size="sm" variant="ghost" onClick={() => onEdit(byId[node.id])} aria-label={`Edit ${node.name}`}>
              <Pencil className="h-4 w-4" />
            </Button>
            <Button size="sm" variant="ghost" onClick={() => onToggle(byId[node.id])}>
              {node.active ? "Deactivate" : "Activate"}
            </Button>
          </div>
        )}
      </div>
      <Tree nodes={node.children} byId={byId} depth={depth + 1} canEdit={canEdit} onEdit={onEdit} onToggle={onToggle} />
    </div>
  ));
}

export default function CategoriesPage() {
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const canEdit = hasPermission(me, "CATEGORY_MANAGE");
  const [editing, setEditing] = useState(undefined);
  const [toggling, setToggling] = useState(null);
  const tree = useQuery({ queryKey: catalogKeys.tree, queryFn: catalogApi.categoryTree });
  const flat = useQuery({ queryKey: catalogKeys.categories, queryFn: () => catalogApi.categories() });
  const byId = Object.fromEntries((flat.data || []).map((c) => [c.id, c]));

  const toggle = useMutation({
    mutationFn: ({ category, force }) =>
      catalogApi.setCategoryStatus(category.id, { active: !category.active, version: category.version, force }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.categories });
      setToggling(null);
      toast.success("Category updated.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <AuthGate anyOf={["PRODUCT_VIEW"]}>
      <PageHeader
        title="Categories"
        description="Organize products into a tree. Filters on Products include sub-categories."
        actions={
          canEdit && (
            <Button onClick={() => setEditing(null)}>
              <Plus className="h-4 w-4" aria-hidden="true" /> New category
            </Button>
          )
        }
      />
      <Card>
        {tree.isPending || flat.isPending ? (
          <TableSkeleton rows={8} cols={3} />
        ) : tree.error ? (
          <ErrorState error={tree.error} onRetry={tree.refetch} />
        ) : tree.data.length === 0 ? (
          <EmptyState icon={FolderTree} title="No categories yet" description="Create the first category to organize products." />
        ) : (
          <Tree nodes={tree.data} byId={byId} depth={0} canEdit={canEdit} onEdit={setEditing} onToggle={setToggling} />
        )}
      </Card>
      {editing !== undefined && flat.data && (
        <CategoryDialog category={editing} categories={flat.data} onClose={() => setEditing(undefined)} />
      )}
      <ConfirmDialog
        open={Boolean(toggling)}
        onOpenChange={(o) => !o && setToggling(null)}
        title={toggling?.active ? `Deactivate ${toggling?.name}?` : `Activate ${toggling?.name}?`}
        description={
          toggling?.active
            ? toggling?.productCount > 0
              ? `${toggling.productCount} active products use this category. They stay sellable, but new products cannot be added here.`
              : "The category is hidden from product forms."
            : "The category becomes available again."
        }
        confirmLabel={toggling?.active ? "Deactivate" : "Activate"}
        tone={toggling?.active ? "danger" : "primary"}
        loading={toggle.isPending}
        onConfirm={() => toggle.mutate({ category: toggling, force: true })}
      />
    </AuthGate>
  );
}
