package com.supermarket.pos.store.entity;

import com.supermarket.pos.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "stores")
public class Store extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "address_line1", length = 150)
    private String addressLine1;

    @Column(name = "address_line2", length = 150)
    private String addressLine2;

    @Column(length = 80)
    private String city;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(name = "tax_registration_no", length = 40)
    private String taxRegistrationNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Column(name = "receipt_header", length = 500)
    private String receiptHeader;

    @Column(name = "receipt_footer", length = 500)
    private String receiptFooter;

    @Column(nullable = false)
    private boolean active;

    @Version
    private long version;
}
