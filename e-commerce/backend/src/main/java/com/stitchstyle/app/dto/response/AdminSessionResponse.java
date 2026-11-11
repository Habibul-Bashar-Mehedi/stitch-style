package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.Role;

public record AdminSessionResponse(Long id, String fullName, String email, Role role) {}
