"use client";

import { use, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, KeyRound, Lock, Unlock } from "lucide-react";
import { PageHeader, Card, CardHeader } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { ConfirmDialog, Modal } from "@/components/ui/dialog";
import { Field, Input, PasswordInput } from "@/components/ui/form";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { UserForm } from "@/features/users/user-form";
import { userKeys, usersApi } from "@/features/users/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime } from "@/lib/format";

function ResetPasswordDialog({ user, onClose }) {
  const [password, setPassword] = useState("");
  const valid = password.length >= 8 && /[A-Za-z]/.test(password) && /\d/.test(password);
  const reset = useMutation({
    mutationFn: () => usersApi.resetPassword(user.id, password),
    onSuccess: () => {
      toast.success("Temporary password set. The user's sessions were signed out.");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Reset password for ${user.username}`}
      description="The user must choose a new password at the next sign-in."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => reset.mutate()} disabled={!valid} loading={reset.isPending}>
            Set temporary password
          </Button>
        </>
      }
    >
      <Field label="Temporary password" hint="At least 8 characters with a letter and a digit.">
        {(a) => <PasswordInput {...a} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
      </Field>
    </Modal>
  );
}

export default function UserDetailPage({ params }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const setErrorRef = useRef(null);
  const [confirmStatus, setConfirmStatus] = useState(null);
  const [resetOpen, setResetOpen] = useState(false);
  const canManage = hasPermission(me, "USER_MANAGE");

  const { data: user, error, isPending, refetch } = useQuery({
    queryKey: userKeys.detail(id),
    queryFn: () => usersApi.get(id),
  });

  const onSaved = (updated) => {
    queryClient.setQueryData(userKeys.detail(id), updated);
    queryClient.invalidateQueries({ queryKey: ["users", "list"] });
  };

  const update = useMutation({
    mutationFn: (values) => usersApi.update(id, { ...values, version: user.version }),
    onSuccess: (u) => {
      onSaved(u);
      toast.success("User saved.");
    },
    onError: (e) => {
      if (e.code === "CONCURRENT_MODIFICATION") {
        toast.error("Someone else changed this user. The latest version was loaded.");
        refetch();
        return;
      }
      if (!applyFieldErrors(e, setErrorRef.current || (() => {}))) toast.error(errorMessage(e));
    },
  });

  const setStatus = useMutation({
    mutationFn: (status) => usersApi.setStatus(id, { status, version: user.version }),
    onSuccess: (u) => {
      onSaved(u);
      setConfirmStatus(null);
      toast.success(`User is now ${u.status.toLowerCase()}.`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <AuthGate anyOf={["USER_VIEW"]}>
      <div className="mx-auto max-w-3xl space-y-6">
        <Link href="/users" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Users
        </Link>
        {isPending ? (
          <Skeleton className="h-64" />
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <>
            <PageHeader
              title={user.fullName}
              description={
                <span className="flex flex-wrap items-center gap-2">
                  {user.username}
                  <Badge color={user.status === "ACTIVE" ? "green" : "red"}>{user.status.toLowerCase()}</Badge>
                  {user.temporarilyLocked && <Badge color="amber">locked after failed sign-ins</Badge>}
                  {user.mustChangePassword && <Badge color="amber">temporary password</Badge>}
                </span>
              }
              actions={
                canManage && (
                  <>
                    <Button variant="outline" onClick={() => setResetOpen(true)}>
                      <KeyRound className="h-4 w-4" aria-hidden="true" /> Reset password
                    </Button>
                    {user.status === "ACTIVE" ? (
                      <Button variant="danger" onClick={() => setConfirmStatus("DISABLED")} disabled={me?.id === user.id}>
                        <Lock className="h-4 w-4" aria-hidden="true" /> Disable
                      </Button>
                    ) : (
                      <Button variant="success" onClick={() => setConfirmStatus("ACTIVE")}>
                        <Unlock className="h-4 w-4" aria-hidden="true" /> Reactivate
                      </Button>
                    )}
                  </>
                )
              }
            />
            <Card>
              <CardHeader
                title="Profile, roles and stores"
                description={`Last sign-in: ${user.lastLoginAt ? formatDateTime(user.lastLoginAt) : "never"} · Approval PIN: ${
                  user.hasApprovalPin ? "set" : "not set"
                }`}
              />
              <div className="p-5">
                {canManage ? (
                  <UserForm
                    key={user.version}
                    user={user}
                    onSubmit={(v) => update.mutate(v)}
                    submitting={update.isPending}
                    setErrorRef={setErrorRef}
                  />
                ) : (
                  <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div>
                      <dt className="text-slate-500">Roles</dt>
                      <dd>{user.roles.join(", ")}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Stores</dt>
                      <dd>{user.stores.map((s) => s.name).join(", ")}</dd>
                    </div>
                  </dl>
                )}
              </div>
            </Card>
            <ConfirmDialog
              open={Boolean(confirmStatus)}
              onOpenChange={(o) => !o && setConfirmStatus(null)}
              title={confirmStatus === "DISABLED" ? `Disable ${user.username}?` : `Reactivate ${user.username}?`}
              description={
                confirmStatus === "DISABLED"
                  ? "The user is signed out everywhere immediately and cannot sign in until reactivated."
                  : "The user can sign in again. Failed sign-in counters are reset."
              }
              confirmLabel={confirmStatus === "DISABLED" ? "Disable user" : "Reactivate"}
              tone={confirmStatus === "DISABLED" ? "danger" : "primary"}
              loading={setStatus.isPending}
              onConfirm={() => setStatus.mutate(confirmStatus)}
            />
            {resetOpen && <ResetPasswordDialog user={user} onClose={() => setResetOpen(false)} />}
          </>
        )}
      </div>
    </AuthGate>
  );
}
