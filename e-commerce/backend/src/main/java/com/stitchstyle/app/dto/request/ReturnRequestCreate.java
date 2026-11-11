package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.ReturnCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReturnRequestCreate(
        @NotBlank String orderNumber,
        String reason,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(@NotNull Long orderItemId, @Min(1) int quantity, @NotNull ReturnCondition condition) {}
}
