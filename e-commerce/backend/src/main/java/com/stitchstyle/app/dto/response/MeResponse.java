package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.Role;

public record MeResponse(Long id, String fullName, String email, String pictureUrl, String phone, Role role) {}
