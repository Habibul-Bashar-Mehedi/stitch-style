package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.BannerRequest;
import com.stitchstyle.app.dto.response.BannerResponse;
import com.stitchstyle.app.service.BannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/banners")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminBannerController {

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public List<BannerResponse> list() {
        return bannerService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BannerResponse create(@Valid @RequestBody BannerRequest request) {
        return bannerService.create(request);
    }

    @PutMapping("/{id}")
    public BannerResponse update(@PathVariable Long id, @Valid @RequestBody BannerRequest request) {
        return bannerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        bannerService.delete(id);
    }
}
