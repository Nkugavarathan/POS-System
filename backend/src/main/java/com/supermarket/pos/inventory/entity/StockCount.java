package com.supermarket.pos.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Physical stock count (reconciliation). Posting turns variances into COUNT_CORRECTION movements. */
@Getter
@Setter
@Entity
@Table(name = "stock_counts")
public class StockCount {

    public enum Status { OPEN, SUBMITTED, POSTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_number", nullable = false, length = 40)
    private String countNumber;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "stockCount", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<StockCountItem> items = new ArrayList<>();
}
