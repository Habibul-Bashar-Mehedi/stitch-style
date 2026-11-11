package com.stitchstyle.app.dto.response;

public record ProductImageResponse(Long id, Long mediaId, String url, int sortOrder, boolean primary) implements java.io.Serializable {}
