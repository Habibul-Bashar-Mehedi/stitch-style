package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CategoryReorderRequest(@NotNull List<Long> orderedIds, Long parentId) {}
