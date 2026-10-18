import {
  BarChart3,
  Boxes,
  ClipboardList,
  FolderTree,
  History,
  LayoutDashboard,
  Package,
  Receipt,
  RotateCcw,
  ScrollText,
  Settings,
  ShoppingCart,
  Truck,
  Users,
  UserSquare2,
  Wallet,
} from "lucide-react";

/**
 * Admin sidebar. `anyOf` lists the permissions that make an entry visible (UI hint only;
 * the backend enforces every call). Grouped for scanability.
 */
export const NAV_SECTIONS = [
  {
    label: "Overview",
    items: [
      { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, anyOf: [] },
      { href: "/pos", label: "Point of sale", icon: ShoppingCart, anyOf: ["POS_SELL"] },
    ],
  },
  {
    label: "Sales",
    items: [
      { href: "/sales", label: "Sales & receipts", icon: Receipt, anyOf: ["SALE_VIEW_OWN", "SALE_VIEW_ALL"] },
      { href: "/returns", label: "Returns", icon: RotateCcw, anyOf: ["RETURN_CREATE", "SALE_VIEW_ALL"] },
      { href: "/shifts", label: "Cashier shifts", icon: Wallet, anyOf: ["SHIFT_VIEW_ALL", "SHIFT_OPERATE"] },
      { href: "/customers", label: "Customers", icon: UserSquare2, anyOf: ["CUSTOMER_VIEW"] },
    ],
  },
  {
    label: "Catalog & stock",
    items: [
      { href: "/products", label: "Products", icon: Package, anyOf: ["PRODUCT_VIEW"] },
      { href: "/categories", label: "Categories", icon: FolderTree, anyOf: ["PRODUCT_VIEW"] },
      { href: "/inventory", label: "Inventory", icon: Boxes, anyOf: ["INVENTORY_VIEW"] },
      { href: "/suppliers", label: "Suppliers", icon: Truck, anyOf: ["SUPPLIER_MANAGE"] },
      { href: "/purchases", label: "Purchase orders", icon: ClipboardList, anyOf: ["PURCHASE_ORDER_MANAGE", "INVENTORY_RECEIVE"] },
    ],
  },
  {
    label: "Insights",
    items: [
      { href: "/reports", label: "Reports", icon: BarChart3, anyOf: ["REPORT_VIEW", "INVENTORY_VIEW"] },
      { href: "/audit", label: "Audit log", icon: ScrollText, anyOf: ["AUDIT_VIEW"] },
    ],
  },
  {
    label: "Administration",
    items: [
      { href: "/users", label: "Users & roles", icon: Users, anyOf: ["USER_VIEW"] },
      { href: "/settings", label: "Store settings", icon: Settings, anyOf: ["SYSTEM_CONFIG", "REGISTER_MANAGE"] },
      { href: "/account", label: "My account", icon: History, anyOf: [] },
    ],
  },
];
