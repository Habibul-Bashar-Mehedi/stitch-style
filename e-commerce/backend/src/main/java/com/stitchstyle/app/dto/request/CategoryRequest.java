package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.SizeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String slug,
        String description,
        Long parentId,
        Long imageMediaId,
        SizeType sizeType,
        Integer displayOrder,
        Boolean active,
        Boolean showOnHome,
        Boolean showInNavbar
) {}
