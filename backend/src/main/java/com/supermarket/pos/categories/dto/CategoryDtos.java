package com.supermarket.pos.categories.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** Flat representation; {@code path} is the breadcrumb, e.g. "Grocery / Rice & Grains". */
    public record CategoryDto(Long id, Long parentId, String code, String name, String path, String description,
                              int sortOrder, boolean active, long productCount, long version) {
    }

    public record CategoryNodeDto(Long id, String code, String name, boolean active, long productCount,
                                  List<CategoryNodeDto> children) {
    }

    public record CategoryRequest(
            Long parentId,
            @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,30}$", message = "2-30 upper-case letters, digits, _ or -")
            String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @Min(0) int sortOrder,
            Long version) {
    }

    public record CategoryStatusRequest(@NotNull Boolean active, @NotNull Long version, boolean force) {
    }
}
