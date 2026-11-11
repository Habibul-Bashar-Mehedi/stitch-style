package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.SizeType;
import java.util.List;

public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        Long imageMediaId,
        SizeType sizeType,
        int displayOrder,
        int depth,
        boolean active,
        boolean showOnHome,
        boolean showInNavbar,
        List<CategoryResponse> children
) implements java.io.Serializable {}
