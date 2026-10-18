package com.supermarket.pos.categories.service;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryDto;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryNodeDto;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryRequest;
import com.supermarket.pos.categories.entity.Category;
import com.supermarket.pos.categories.repository.CategoryRepository;
import com.supermarket.pos.common.error.ApiException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryService {

    private static final int MAX_DEPTH = 5;

    private final CategoryRepository categories;
    private final CategoryUsage usage;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, CategoryUsage usage, AuditService audit) {
        this.categories = categories;
        this.usage = usage;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public List<CategoryDto> list(Boolean activeOnly) {
        List<Category> all = categories.findAllWithParent();
        Map<Long, Long> counts = usage.activeProductCounts();
        return all.stream()
                .filter(c -> activeOnly == null || !activeOnly || c.isActive())
                .map(c -> toDto(c, counts))
                .sorted(Comparator.comparing(CategoryDto::path, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Public (online shop): the tree of active categories only. */
    @Transactional(readOnly = true)
    public List<CategoryNodeDto> activeTree() {
        List<Category> all = categories.findAllWithParent().stream().filter(Category::isActive).toList();
        Map<Long, Long> counts = usage.activeProductCounts();
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParent() == null ? 0L : c.getParent().getId(), k -> new ArrayList<>()).add(c);
        }
        return children(0L, byParent, counts);
    }

    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Transactional(readOnly = true)
    public List<CategoryNodeDto> tree() {
        List<Category> all = categories.findAllWithParent();
        Map<Long, Long> counts = usage.activeProductCounts();
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParent() == null ? 0L : c.getParent().getId(), k -> new ArrayList<>()).add(c);
        }
        return children(0L, byParent, counts);
    }

    /** The category and all of its sub-categories (product filtering by a parent category). */
    @Transactional(readOnly = true)
    public Set<Long> withDescendants(Long id) {
        List<Category> all = categories.findAllWithParent();
        Map<Long, List<Long>> children = new HashMap<>();
        for (Category c : all) {
            if (c.getParent() != null) {
                children.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    /** For other modules (product validation). */
    @Transactional(readOnly = true)
    public Category requireActive(Long id) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
        if (!category.isActive()) {
            throw ApiException.rule("Category " + category.getName() + " is inactive.");
        }
        return category;
    }

    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String code = request.code().trim().toUpperCase();
        if (categories.existsByCodeIgnoreCase(code)) {
            throw ApiException.duplicate("code", "Category code " + code + " already exists.");
        }
        Category category = new Category();
        category.setCode(code);
        apply(category, request);
        Category saved = categories.saveAndFlush(category);
        audit.record(AuditService.Entry.success("CATEGORY_CREATED", "Category", saved.getId(),
                "Category " + saved.getName() + " created"));
        return toDto(saved, usage.activeProductCounts());
    }

    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
        checkVersion(category, request.version());
        String code = request.code().trim().toUpperCase();
        if (!category.getCode().equalsIgnoreCase(code) && categories.existsByCodeIgnoreCase(code)) {
            throw ApiException.duplicate("code", "Category code " + code + " already exists.");
        }
        category.setCode(code);
        apply(category, request);
        Category saved = categories.saveAndFlush(category);
        audit.record(AuditService.Entry.success("CATEGORY_UPDATED", "Category", id,
                "Category " + saved.getName() + " updated"));
        return toDto(saved, usage.activeProductCounts());
    }

    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Transactional
    public CategoryDto setActive(Long id, boolean active, Long version, boolean force) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
        checkVersion(category, version);
        if (!active) {
            if (categories.countActiveChildren(id) > 0) {
                throw ApiException.rule("Deactivate or move the sub-categories first.");
            }
            long products = usage.activeProductCount(id);
            if (products > 0 && !force) {
                throw ApiException.rule(products + " active products use this category. Move them first "
                        + "or confirm to deactivate anyway (they remain sellable).");
            }
        } else if (category.getParent() != null && !category.getParent().isActive()) {
            throw ApiException.rule("Activate the parent category first.");
        }
        category.setActive(active);
        Category saved = categories.saveAndFlush(category);
        audit.record(AuditService.Entry.success(active ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED",
                "Category", id, category.getName()));
        return toDto(saved, usage.activeProductCounts());
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        category.setSortOrder(request.sortOrder());
        if (request.parentId() == null) {
            category.setParent(null);
            return;
        }
        Category parent = categories.findById(request.parentId())
                .orElseThrow(() -> ApiException.notFound("Category", request.parentId()));
        // Reject cycles (a category cannot become its own ancestor) and very deep trees.
        Set<Long> seen = new HashSet<>();
        int depth = 1;
        for (Category p = parent; p != null; p = p.getParent()) {
            if (category.getId() != null && p.getId().equals(category.getId())) {
                throw ApiException.rule("A category cannot be moved under itself or one of its sub-categories.");
            }
            if (!seen.add(p.getId()) || ++depth > MAX_DEPTH) {
                throw ApiException.rule("Categories can be nested at most " + MAX_DEPTH + " levels deep.");
            }
        }
        category.setParent(parent);
    }

    private List<CategoryNodeDto> children(Long parentId, Map<Long, List<Category>> byParent, Map<Long, Long> counts) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName))
                .map(c -> new CategoryNodeDto(c.getId(), c.getCode(), c.getName(), c.isActive(),
                        counts.getOrDefault(c.getId(), 0L), children(c.getId(), byParent, counts)))
                .toList();
    }

    private static CategoryDto toDto(Category c, Map<Long, Long> counts) {
        return new CategoryDto(c.getId(), c.getParent() == null ? null : c.getParent().getId(), c.getCode(),
                c.getName(), path(c), c.getDescription(), c.getSortOrder(), c.isActive(),
                counts.getOrDefault(c.getId(), 0L), c.getVersion());
    }

    public static String path(Category c) {
        List<String> parts = new ArrayList<>();
        int guard = 0;
        for (Category p = c; p != null && guard++ < MAX_DEPTH + 1; p = p.getParent()) {
            parts.add(0, p.getName());
        }
        return String.join(" / ", parts);
    }

    private static void checkVersion(Category c, Long expected) {
        if (expected == null || c.getVersion() != expected) {
            throw new OptimisticLockingFailureException("Category version mismatch");
        }
    }
}
