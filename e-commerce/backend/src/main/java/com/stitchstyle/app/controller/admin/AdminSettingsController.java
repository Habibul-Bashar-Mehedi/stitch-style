package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.dto.response.SiteSettingResponse;
import com.stitchstyle.app.service.CurrentUserService;
import com.stitchstyle.app.service.SiteSettingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final SiteSettingService siteSettingService;
    private final CurrentUserService currentUserService;

    public AdminSettingsController(SiteSettingService siteSettingService, CurrentUserService currentUserService) {
        this.siteSettingService = siteSettingService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<SiteSettingResponse> list() {
        return siteSettingService.list();
    }

    @PutMapping
    public List<SiteSettingResponse> update(@Valid @RequestBody List<SettingRequest> requests) {
        String email = currentUserService.email();
        for (SettingRequest request : requests) siteSettingService.update(request, email);
        return siteSettingService.list();
    }
}
