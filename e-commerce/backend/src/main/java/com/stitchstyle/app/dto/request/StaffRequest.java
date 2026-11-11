package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StaffRequest(@NotBlank @Email String email, String fullName, @NotNull Role role, Boolean active) {}
