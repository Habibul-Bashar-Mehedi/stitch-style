package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.Role;
import java.time.Instant;

public record StaffResponse(Long id, String fullName, String email, Role role, boolean active, Instant lastLoginAt) {}
