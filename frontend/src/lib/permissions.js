/**
 * UI-only permission helpers. They hide controls the user cannot use; every action is
 * still authorized by the backend, which is the only authority.
 */
export function hasPermission(user, permission) {
  return Boolean(user?.permissions?.includes(permission));
}

export function hasAny(user, permissions = []) {
  if (!permissions.length) return true;
  return permissions.some((p) => hasPermission(user, p));
}

/** Where a user lands after signing in. */
export function landingPath(user) {
  if (hasPermission(user, "DASHBOARD_VIEW")) return "/dashboard";
  if (hasPermission(user, "POS_SELL")) return "/pos";
  if (hasPermission(user, "INVENTORY_VIEW")) return "/inventory";
  return "/dashboard";
}
