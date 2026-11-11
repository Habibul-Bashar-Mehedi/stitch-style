package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.ReturnStatus;
import jakarta.validation.constraints.NotNull;

public record ReturnStatusRequest(@NotNull ReturnStatus status, String note) {}
