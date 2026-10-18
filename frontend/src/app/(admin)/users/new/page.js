"use client";

import { useRef } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader, Card } from "@/components/ui/layout";
import { AuthGate } from "@/features/auth/auth-gate";
import { UserForm } from "@/features/users/user-form";
import { userKeys, usersApi } from "@/features/users/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";

export default function NewUserPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const setErrorRef = useRef(null);
  const create = useMutation({
    mutationFn: usersApi.create,
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: userKeys.all });
      toast.success(`User ${user.username} created. Share the temporary password securely.`);
      router.push(`/users/${user.id}`);
    },
    onError: (error) => {
      if (error.code === "DUPLICATE_RESOURCE" && error.details?.field && setErrorRef.current) {
        setErrorRef.current(error.details.field, { type: "server", message: error.message });
        return;
      }
      if (!applyFieldErrors(error, setErrorRef.current || (() => {}))) toast.error(errorMessage(error));
    },
  });

  return (
    <AuthGate anyOf={["USER_MANAGE"]}>
      <div className="mx-auto max-w-3xl">
        <PageHeader title="New user" description="The user signs in with the temporary password and must change it." />
        <Card className="p-5">
          <UserForm onSubmit={(v) => create.mutate(v)} submitting={create.isPending} setErrorRef={setErrorRef} />
        </Card>
      </div>
    </AuthGate>
  );
}
