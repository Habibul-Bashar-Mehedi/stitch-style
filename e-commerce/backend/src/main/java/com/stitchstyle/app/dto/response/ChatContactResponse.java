package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.Role;

public record ChatContactResponse(Long id, String fullName, Role role, String pictureUrl) {
}
