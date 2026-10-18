"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { Search, UserPlus } from "lucide-react";
import { PageHeader, Card, Table, Th, Td, Pagination } from "@/components/ui/layout";
import { Input, Select } from "@/components/ui/form";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { hasPermission } from "@/lib/permissions";
import { useDebouncedValue } from "@/lib/hooks";
import { formatDateTime } from "@/lib/format";
import { userKeys, usersApi } from "@/features/users/api";
import { RolesPanel } from "@/features/users/roles-panel";

const STATUS_COLOR = { ACTIVE: "green", LOCKED: "amber", DISABLED: "red" };

function UsersTable() {
  const [q, setQ] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const debouncedQ = useDebouncedValue(q);
  const params = { q: debouncedQ || undefined, role: role || undefined, status: status || undefined, page, size: 20 };
  const { data, error, isPending, refetch, isFetching } = useQuery({
    queryKey: userKeys.list(params),
    queryFn: () => usersApi.list(params),
    placeholderData: keepPreviousData,
  });
  const roles = useQuery({ queryKey: userKeys.roles, queryFn: usersApi.roles });

  return (
    <Card>
      <div className="flex flex-col gap-3 border-b border-slate-200 p-4 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <Input
            className="pl-9"
            placeholder="Search username or name"
            aria-label="Search users"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <Select aria-label="Filter by role" className="sm:w-48" value={role} onChange={(e) => (setRole(e.target.value), setPage(0))}>
          <option value="">All roles</option>
          {roles.data?.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name}
            </option>
          ))}
        </Select>
        <Select aria-label="Filter by status" className="sm:w-40" value={status} onChange={(e) => (setStatus(e.target.value), setPage(0))}>
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="LOCKED">Locked</option>
          <option value="DISABLED">Disabled</option>
        </Select>
      </div>
      {isPending ? (
        <TableSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.content.length === 0 ? (
        <EmptyState title="No users found" description="Try another search or filter." />
      ) : (
        <div className={isFetching ? "opacity-70 transition-opacity" : undefined}>
          <Table>
            <thead>
              <tr>
                <Th>User</Th>
                <Th>Roles</Th>
                <Th>Status</Th>
                <Th>Last sign-in</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50">
                  <Td>
                    <Link href={`/users/${u.id}`} className="font-medium text-brand-700 hover:underline">
                      {u.fullName}
                    </Link>
                    <p className="text-xs text-slate-500">{u.username}</p>
                  </Td>
                  <Td>
                    <div className="flex flex-wrap gap-1">
                      {u.roles.map((r) => (
                        <Badge key={r}>{r}</Badge>
                      ))}
                    </div>
                  </Td>
                  <Td>
                    <Badge color={STATUS_COLOR[u.status]}>{u.status.toLowerCase()}</Badge>
                  </Td>
                  <Td className="text-slate-500">{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : "Never"}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination {...data} onPageChange={setPage} />
        </div>
      )}
    </Card>
  );
}

export default function UsersPage() {
  const { data: me } = useMe();
  const [tab, setTab] = useState("users");
  const canManage = hasPermission(me, "USER_MANAGE");
  return (
    <AuthGate anyOf={["USER_VIEW"]}>
      <PageHeader
        title="Users & roles"
        description="Staff accounts, their roles and the stores they can work in."
        actions={
          canManage && (
            <Link href="/users/new">
              <Button>
                <UserPlus className="h-4 w-4" aria-hidden="true" />
                New user
              </Button>
            </Link>
          )
        }
      />
      <div role="tablist" aria-label="Users and roles" className="mb-4 flex gap-1 border-b border-slate-200">
        {[
          ["users", "Users"],
          ["roles", "Roles & permissions"],
        ].map(([id, label]) => (
          <button
            key={id}
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={
              "-mb-px border-b-2 px-4 py-2 text-sm font-medium " +
              (tab === id ? "border-brand-700 text-brand-800" : "border-transparent text-slate-500 hover:text-slate-800")
            }
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "users" ? <UsersTable /> : <RolesPanel canManage={hasPermission(me, "ROLE_MANAGE")} />}
    </AuthGate>
  );
}
