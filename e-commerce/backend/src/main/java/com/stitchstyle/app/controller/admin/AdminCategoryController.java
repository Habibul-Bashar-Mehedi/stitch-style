package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.CategoryReorderRequest;
import com.stitchstyle.app.dto.request.CategoryRequest;
import com.stitchstyle.app.dto.response.CategoryResponse;
import com.stitchstyle.app.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.tree(true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/{id}/visibility")
    public void visibility(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        categoryService.setVisibility(id, Boolean.TRUE.equals(body.get("active")));
    }

    @PutMapping("/reorder")
    public void reorder(@Valid @RequestBody CategoryReorderRequest request) {
        categoryService.reorder(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
