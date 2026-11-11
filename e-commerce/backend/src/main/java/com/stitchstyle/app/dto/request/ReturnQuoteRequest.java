package com.stitchstyle.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReturnQuoteRequest(
        @NotEmpty List<@Valid Item> items
) {
    public record Item(@NotNull Long orderItemId, @Min(0) int quantity) {
    }
}
