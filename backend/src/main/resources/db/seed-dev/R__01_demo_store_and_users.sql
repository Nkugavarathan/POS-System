-- ============================================================================================
-- DEMO DATA - LOCAL DEVELOPMENT ONLY (loaded by the dev profile from classpath:db/seed-dev).
-- Never enable this location in production. The demo passwords below are public; see README.
--
--   superadmin / SuperAdmin@123   (SUPER_ADMIN, both stores)
--   admin      / Admin@1234       (ADMIN, both stores)
--   manager    / Manager@123      (MANAGER, approval PIN 1234)
--   cashier    / Cashier@123      (CASHIER)
--   cashier2   / Cashier@123      (CASHIER)
--   inventory  / Inventory@123    (INVENTORY_STAFF)
--
-- Idempotent (repeatable migration): existing rows are left untouched, so password changes
-- made through the application survive a re-run.
-- ============================================================================================

INSERT IGNORE INTO stores (code, name, address_line1, city, phone, email, tax_registration_no, currency, timezone,
                           prices_include_tax, receipt_header, receipt_footer, active, created_at, updated_at)
VALUES ('CMB01', 'DEMO Supermarket - Colombo 01', '100 Example Road', 'Colombo', '+94 11 000 0000',
        'colombo01@demo.invalid', 'DEMO-VAT-000001', 'LKR', 'Asia/Colombo', TRUE,
        'DEMO STORE - NOT A REAL BUSINESS', 'Thank you for shopping with us! Goods once sold may be returned within 14 days with this receipt.',
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('KDY01', 'DEMO Supermarket - Kandy 01', '25 Sample Street', 'Kandy', '+94 81 000 0000',
        'kandy01@demo.invalid', 'DEMO-VAT-000002', 'LKR', 'Asia/Colombo', TRUE,
        'DEMO STORE - NOT A REAL BUSINESS', 'Thank you for shopping with us!',
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT IGNORE INTO registers (store_id, code, name, active, created_at, updated_at)
SELECT s.id, r.code, r.name, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM stores s
JOIN (SELECT 'R01' AS code, 'Counter 1' AS name UNION ALL
      SELECT 'R02', 'Counter 2' UNION ALL
      SELECT 'R03', 'Express counter') r
WHERE s.code IN ('CMB01', 'KDY01');

INSERT IGNORE INTO users (username, full_name, email, password_hash, pin_hash, status, must_change_password,
                          created_at, updated_at)
VALUES ('superadmin', 'Demo Super Admin', 'superadmin@demo.invalid',
        '$2a$12$M8.OHQuyAJUE7iOxCfhIOu9vWlcEdipM.OMHhwfxupsTGfF9nqOPa', NULL, 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('admin', 'Demo Administrator', 'admin@demo.invalid',
        '$2a$12$bR3.4hztn.OVshl7Ghjdauy.3EzAYF6bXLR9IIYQpJpxGf6f9/TtO', NULL, 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('manager', 'Demo Store Manager', 'manager@demo.invalid',
        '$2a$12$SrHjN8cPyrb11XmIbkNpaOQdy7KZz3vczo8HJW6a1aMxCyjkOeD5i',
        '$2a$12$IIpNUdAMbfF6QC1t2GbMNuGD9YqoKf7rYeeHCA9Rnk2Umn.X7pIHW', 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('cashier', 'Demo Cashier One', NULL,
        '$2a$12$WChcx9ppQ4GFHqOVa9UtPeJQIhEaoYV1jccfhv4Hbf2z4JqRanOVe', NULL, 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('cashier2', 'Demo Cashier Two', NULL,
        '$2a$12$WChcx9ppQ4GFHqOVa9UtPeJQIhEaoYV1jccfhv4Hbf2z4JqRanOVe', NULL, 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('inventory', 'Demo Inventory Clerk', NULL,
        '$2a$12$BzQ0QPbMigmRcleCDLP.xONVqZNS8jFDFcUnpjIcDZG/Ep1MfWL2C', NULL, 'ACTIVE', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON
    (u.username = 'superadmin' AND r.code = 'SUPER_ADMIN') OR
    (u.username = 'admin'      AND r.code = 'ADMIN') OR
    (u.username = 'manager'    AND r.code = 'MANAGER') OR
    (u.username = 'cashier'    AND r.code = 'CASHIER') OR
    (u.username = 'cashier2'   AND r.code = 'CASHIER') OR
    (u.username = 'inventory'  AND r.code = 'INVENTORY_STAFF');

INSERT IGNORE INTO user_store_access (user_id, store_id, is_default)
SELECT u.id, s.id, s.code = 'CMB01'
FROM users u JOIN stores s
WHERE (u.username IN ('superadmin', 'admin') AND s.code IN ('CMB01', 'KDY01'))
   OR (u.username IN ('manager', 'cashier', 'cashier2', 'inventory') AND s.code = 'CMB01');
