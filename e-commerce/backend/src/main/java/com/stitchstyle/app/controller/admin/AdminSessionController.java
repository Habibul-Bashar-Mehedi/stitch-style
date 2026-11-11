package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.response.AdminSessionResponse;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.service.CurrentUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminSessionController {

    private final CurrentUserService currentUserService;

    public AdminSessionController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/session")
    public AdminSessionResponse session() {
        User user = currentUserService.require();
        return new AdminSessionResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
