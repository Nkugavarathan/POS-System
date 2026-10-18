package com.supermarket.pos.categories.controller;

import com.supermarket.pos.categories.dto.CategoryDtos.CategoryDto;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryNodeDto;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryRequest;
import com.supermarket.pos.categories.dto.CategoryDtos.CategoryStatusRequest;
import com.supermarket.pos.categories.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categories")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryDto> list(@RequestParam(required = false) Boolean active) {
        return categories.list(active);
    }

    @GetMapping("/tree")
    public List<CategoryNodeDto> tree() {
        return categories.tree();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto create(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/{id}")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CategoryDto setStatus(@PathVariable Long id, @Valid @RequestBody CategoryStatusRequest request) {
        return categories.setActive(id, request.active(), request.version(), request.force());
    }
}
