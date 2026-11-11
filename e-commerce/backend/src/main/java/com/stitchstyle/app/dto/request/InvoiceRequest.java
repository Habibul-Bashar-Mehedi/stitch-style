package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InvoiceRequest(@NotEmpty List<Long> orderIds) {}
