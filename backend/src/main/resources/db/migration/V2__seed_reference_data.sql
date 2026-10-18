-- V2: reference data required in every environment: permissions, roles and their default
-- grants, tender types and global settings. No user accounts are created here; demo users
-- live only in db/seed-dev, which is loaded by the dev profile.

INSERT INTO permissions (code, module, description) VALUES
 ('SYSTEM_CONFIG',          'system',     'Manage stores, settings, tender types and tax rates'),
 ('ROLE_MANAGE',            'system',     'Manage roles and their permissions'),
 ('USER_VIEW',              'users',      'View users'),
 ('USER_MANAGE',            'users',      'Create, update and disable users'),
 ('REGISTER_MANAGE',        'store',      'Manage registers / terminals'),
 ('CATEGORY_MANAGE',        'catalog',    'Manage categories'),
 ('PRODUCT_VIEW',           'catalog',    'View products and selling prices'),
 ('PRODUCT_VIEW_COST',      'catalog',    'View product cost prices'),
 ('PRODUCT_MANAGE',         'catalog',    'Create and update products'),
 ('PRICE_MANAGE',           'catalog',    'Change selling prices and promotions'),
 ('INVENTORY_VIEW',         'inventory',  'View stock levels, batches and movements'),
 ('INVENTORY_RECEIVE',      'inventory',  'Receive goods into stock'),
 ('INVENTORY_ADJUST',       'inventory',  'Post stock adjustments'),
 ('INVENTORY_WRITE_OFF',    'inventory',  'Write off damaged or expired stock'),
 ('INVENTORY_COUNT',        'inventory',  'Create and record stock counts'),
 ('INVENTORY_COUNT_POST',   'inventory',  'Post stock counts to the ledger'),
 ('INVENTORY_APPROVE',      'inventory',  'Approve inventory actions above configured limits'),
 ('SUPPLIER_MANAGE',        'purchasing', 'Manage suppliers'),
 ('PURCHASE_ORDER_MANAGE',  'purchasing', 'Create and edit purchase orders'),
 ('PURCHASE_ORDER_APPROVE', 'purchasing', 'Approve purchase orders above the threshold'),
 ('POS_SELL',               'pos',        'Use the POS to sell'),
 ('POS_HOLD_CART',          'pos',        'Hold and resume carts'),
 ('SHIFT_OPERATE',          'pos',        'Open and close own cashier shift'),
 ('SHIFT_VIEW_ALL',         'pos',        'View and force-close any shift'),
 ('SHIFT_VARIANCE_APPROVE', 'pos',        'Approve cash variances on shift close'),
 ('DISCOUNT_APPLY',         'pos',        'Apply discounts within the role limit'),
 ('DISCOUNT_APPROVE',       'pos',        'Approve discounts above a cashier limit'),
 ('PRICE_OVERRIDE',         'pos',        'Override or approve selling price overrides'),
 ('VOID_APPROVE',           'pos',        'Approve line and cart voids'),
 ('CASH_MOVEMENT',          'pos',        'Record pay-ins, pay-outs and cash drops'),
 ('CASH_MOVEMENT_APPROVE',  'pos',        'Approve pay-outs above the threshold'),
 ('SALE_VIEW_OWN',          'sales',      'View own sales'),
 ('SALE_VIEW_ALL',          'sales',      'View all sales in accessible stores'),
 ('RECEIPT_REPRINT',        'sales',      'Reprint receipts'),
 ('RETURN_CREATE',          'returns',    'Process returns'),
 ('RETURN_APPROVE',         'returns',    'Approve returns and refunds'),
 ('CUSTOMER_VIEW',          'customers',  'Search and view customers'),
 ('CUSTOMER_MANAGE',        'customers',  'Create and update customers'),
 ('LOYALTY_ADJUST',         'customers',  'Manually adjust loyalty points'),
 ('DASHBOARD_VIEW',         'reports',    'View the dashboard'),
 ('REPORT_VIEW',            'reports',    'View operational reports'),
 ('REPORT_EXPORT',          'reports',    'Export reports to CSV'),
 ('REPORT_FINANCIAL',       'reports',    'View cost, valuation and estimated margin'),
 ('AUDIT_VIEW',             'audit',      'View the audit log');

INSERT INTO roles (code, name, description, max_discount_percent, system_role) VALUES
 ('SUPER_ADMIN',     'Super administrator', 'System-level configuration and full access', 100.00, TRUE),
 ('ADMIN',           'Administrator',       'Products, pricing, users, suppliers and reports', 100.00, TRUE),
 ('MANAGER',         'Store manager',       'Store operations, approvals, sales review', 20.00, TRUE),
 ('CASHIER',         'Cashier',             'Checkout counter operations', 5.00, TRUE),
 ('INVENTORY_STAFF', 'Inventory staff',     'Stock receiving, adjustments and counts', 0.00, TRUE);

-- SUPER_ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- ADMIN: everything except system configuration and role management
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code NOT IN ('SYSTEM_CONFIG', 'ROLE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'USER_VIEW', 'PRODUCT_VIEW', 'PRODUCT_VIEW_COST',
  'INVENTORY_VIEW', 'INVENTORY_RECEIVE', 'INVENTORY_ADJUST', 'INVENTORY_WRITE_OFF',
  'INVENTORY_COUNT', 'INVENTORY_COUNT_POST', 'INVENTORY_APPROVE',
  'SUPPLIER_MANAGE', 'PURCHASE_ORDER_MANAGE', 'PURCHASE_ORDER_APPROVE',
  'POS_SELL', 'POS_HOLD_CART', 'SHIFT_OPERATE', 'SHIFT_VIEW_ALL', 'SHIFT_VARIANCE_APPROVE',
  'DISCOUNT_APPLY', 'DISCOUNT_APPROVE', 'PRICE_OVERRIDE', 'VOID_APPROVE',
  'CASH_MOVEMENT', 'CASH_MOVEMENT_APPROVE',
  'SALE_VIEW_OWN', 'SALE_VIEW_ALL', 'RECEIPT_REPRINT', 'RETURN_CREATE', 'RETURN_APPROVE',
  'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'LOYALTY_ADJUST',
  'DASHBOARD_VIEW', 'REPORT_VIEW', 'REPORT_EXPORT')
WHERE r.code = 'MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'PRODUCT_VIEW', 'POS_SELL', 'POS_HOLD_CART', 'SHIFT_OPERATE', 'DISCOUNT_APPLY',
  'CASH_MOVEMENT', 'SALE_VIEW_OWN', 'RECEIPT_REPRINT', 'RETURN_CREATE',
  'CUSTOMER_VIEW', 'CUSTOMER_MANAGE')
WHERE r.code = 'CASHIER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'PRODUCT_VIEW', 'PRODUCT_VIEW_COST', 'INVENTORY_VIEW', 'INVENTORY_RECEIVE',
  'INVENTORY_ADJUST', 'INVENTORY_WRITE_OFF', 'INVENTORY_COUNT',
  'SUPPLIER_MANAGE', 'PURCHASE_ORDER_MANAGE')
WHERE r.code = 'INVENTORY_STAFF';

INSERT INTO tender_types (code, name, is_cash, requires_reference, allows_change, active, sort_order) VALUES
 ('CASH',    'Cash',                     TRUE,  FALSE, TRUE,  TRUE, 1),
 ('CARD',    'Card (external terminal)', FALSE, TRUE,  FALSE, TRUE, 2),
 ('LOYALTY', 'Loyalty points',           FALSE, FALSE, FALSE, TRUE, 3);

-- Global defaults; a store-scoped row with the same key overrides them.
INSERT INTO settings (store_id, setting_key, setting_value, value_type, updated_at) VALUES
 (NULL, 'shift.varianceApprovalThreshold',  '500.00',    'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'cash.payoutApprovalThreshold',     '2000.00',   'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'void.lineApprovalThreshold',       '1000.00',   'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'void.cartApprovalRequired',        'true',      'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'returns.windowDays',               '14',        'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'returns.approvalRequired',         'true',      'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'inventory.writeOffApprovalThreshold', '5000.00', 'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'purchasing.poApprovalThreshold',   '100000.00', 'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'loyalty.enabled',                  'true',      'BOOLEAN', UTC_TIMESTAMP(6)),
 (NULL, 'loyalty.pointsPer100',             '1',         'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'loyalty.pointValue',               '1.00',      'DECIMAL', UTC_TIMESTAMP(6)),
 (NULL, 'loyalty.minRedeemPoints',          '100',       'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'heldCart.ttlHours',                '12',        'INTEGER', UTC_TIMESTAMP(6)),
 (NULL, 'receipt.paperWidth',               '80mm',      'STRING',  UTC_TIMESTAMP(6));
