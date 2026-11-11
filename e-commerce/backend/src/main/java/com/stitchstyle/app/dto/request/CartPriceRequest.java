package com.stitchstyle.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CartPriceRequest(@NotNull List<@Valid CartLineRequest> items) {}
