package com.stitchstyle.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CartMergeRequest(@NotEmpty List<@Valid CartLineRequest> items) {}
