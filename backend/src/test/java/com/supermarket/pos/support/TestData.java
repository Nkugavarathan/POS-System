package com.supermarket.pos.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Creates stores and users with roles directly in the test database. */
@Component
public class TestData {

    public static final String PASSWORD = "Passw0rd!";
    public static final String PIN = "4321";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private String passwordHash;
    private String pinHash;

    public TestData(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record TestUser(Long id, String username, Long storeId) {
    }

    public Long store(String code) {
        jdbc.update("""
                INSERT IGNORE INTO stores (code, name, currency, timezone, prices_include_tax, active, created_at, updated_at)
                VALUES (?, ?, 'LKR', 'Asia/Colombo', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, code, "Test store " + code);
        return jdbc.queryForObject("SELECT id FROM stores WHERE code = ?", Long.class, code);
    }

    public Long register(Long storeId, String code) {
        jdbc.update("""
                INSERT IGNORE INTO registers (store_id, code, name, active, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, storeId, code, "Register " + code);
        return jdbc.queryForObject("SELECT id FROM registers WHERE store_id = ? AND code = ?", Long.class, storeId, code);
    }

    /** A new user with a unique username, the given role, access to the store and the standard password/PIN. */
    public TestUser user(String role, Long storeId) {
        String username = role.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO users (username, full_name, password_hash, pin_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, username, "Test " + role, passwordHash(), pinHash());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, role);
        jdbc.update("INSERT INTO user_store_access (user_id, store_id, is_default) VALUES (?, ?, TRUE)", id, storeId);
        return new TestUser(id, username, storeId);
    }

    /** A category (created once per code). */
    public Long category(String code) {
        jdbc.update("""
                INSERT IGNORE INTO categories (code, name, sort_order, active, created_at, updated_at, version)
                VALUES (?, ?, 0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, code, "Category " + code);
        return jdbc.queryForObject("SELECT id FROM categories WHERE code = ?", Long.class, code);
    }

    /** An active product with a unique SKU. Returns its id. */
    public Long product(String uom, String price, boolean trackBatches) {
        Long categoryId = category("TESTCAT");
        String sku = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO products (sku, name, category_id, unit_of_measure, cost_price, selling_price, tax_rate_id,
                                      reorder_level, track_batches, track_expiry, allow_price_override, active,
                                      created_at, updated_at, version)
                SELECT ?, ?, ?, ?, 0, ?, id, 2, ?, ?, FALSE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
                FROM tax_rates WHERE code = 'VAT_STD'
                """, sku, "Product " + sku, categoryId, uom, price, trackBatches, trackBatches);
        return jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku);
    }

    /** A role with exactly the given permissions (for permission-combination tests). */
    public void role(String code, String... permissions) {
        jdbc.update("INSERT IGNORE INTO roles (code, name, max_discount_percent, system_role) VALUES (?, ?, 0, FALSE)",
                code, code);
        for (String p : permissions) {
            jdbc.update("""
                    INSERT IGNORE INTO role_permissions (role_id, permission_id)
                    SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?
                    """, code, p);
        }
    }

    private synchronized String passwordHash() {
        if (passwordHash == null) {
            passwordHash = encoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    private synchronized String pinHash() {
        if (pinHash == null) {
            pinHash = encoder.encode(PIN);
        }
        return pinHash;
    }
}
