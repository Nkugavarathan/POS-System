package com.supermarket.pos.customers.entity;

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

/** A known shopper. Phone is stored in E.164 form (+94...), email lower-cased. */
@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_code", nullable = false, length = 20)
    private String customerCode;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;
}
