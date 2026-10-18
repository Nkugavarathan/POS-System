-- ============================================================================================
-- LOCAL DEVELOPMENT ONLY. Brings rows created by earlier versions of the starter data up to date
-- (names, addresses, brands). Safe to re-run: every statement is a no-op once applied.
-- ============================================================================================
UPDATE stores SET name = 'FreshPick Supermarket - Colombo 03', address_line1 = '214 Galle Road', city = 'Colombo 03', phone = '+94 11 250 3344', email = 'colombo03@freshpick.local', tax_registration_no = '114567890-7000', receipt_header = 'FreshPick Supermarket' WHERE code = 'CMB01' AND name LIKE 'DEMO%';
UPDATE stores SET name = 'FreshPick Supermarket - Kandy', address_line1 = '45 Dalada Veediya', city = 'Kandy', phone = '+94 81 222 6177', email = 'kandy@freshpick.local', tax_registration_no = '114567890-7001', receipt_header = 'FreshPick Supermarket' WHERE code = 'KDY01' AND name LIKE 'DEMO%';
UPDATE users SET full_name = 'Nimal Perera' WHERE full_name = 'Demo Super Admin';
UPDATE users SET full_name = 'Kavindi Fernando' WHERE full_name = 'Demo Administrator';
UPDATE users SET full_name = 'Ruwan Jayasinghe' WHERE full_name = 'Demo Store Manager';
UPDATE users SET full_name = 'Dilini Silva' WHERE full_name = 'Demo Cashier One';
UPDATE users SET full_name = 'Tharindu Bandara' WHERE full_name = 'Demo Cashier Two';
UPDATE users SET full_name = 'Saman Kumara' WHERE full_name = 'Demo Inventory Clerk';
UPDATE users SET email = REPLACE(email, '@demo.invalid', '@freshpick.local') WHERE email LIKE '%@demo.invalid';
UPDATE products SET brand = 'FreshPick Bakery' WHERE brand = 'DEMO Bakers';
UPDATE products SET brand = 'Pure Care' WHERE brand = 'DEMO Care';
UPDATE products SET brand = 'Sparkle Home' WHERE brand = 'DEMO Clean';
UPDATE products SET brand = 'Ceylon Cocoa Co.' WHERE brand = 'DEMO Cocoa';
UPDATE products SET brand = 'FreshPick Dairy' WHERE brand = 'DEMO Dairy';
UPDATE products SET brand = 'Uva Hills Estates' WHERE brand = 'DEMO Estates';
UPDATE products SET brand = 'Green Valley Farms' WHERE brand = 'DEMO Farms';
UPDATE products SET brand = 'Fizzy Lanka' WHERE brand = 'DEMO Fizz';
UPDATE products SET brand = 'Home Kitchen' WHERE brand = 'DEMO Kitchen';
UPDATE products SET brand = 'Golden Grain Mills' WHERE brand = 'DEMO Mills';
UPDATE products SET brand = 'FreshPick Pantry' WHERE brand = 'DEMO Pantry';
UPDATE products SET brand = 'Hill Country Roasters' WHERE brand = 'DEMO Roasters';
UPDATE products SET brand = 'Crunchy Bites' WHERE brand = 'DEMO Snacks';
UPDATE products SET brand = 'SoftTouch' WHERE brand = 'DEMO Soft';
UPDATE products SET brand = 'Island Spice Co.' WHERE brand = 'DEMO Spice Co';
UPDATE products SET brand = 'Crystal Springs' WHERE brand = 'DEMO Springs';
UPDATE product_promotions SET name = TRIM(SUBSTRING(name, 6)) WHERE name LIKE 'DEMO:%';
UPDATE stock_movements SET reason = 'Opening stock', reference_type = 'OPENING_STOCK' WHERE reference_type = 'DEMO_SEED';
UPDATE tax_rates SET name = 'VAT standard rate' WHERE code = 'VAT_STD' AND name LIKE '%example%';
