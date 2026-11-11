package com.stitchstyle.app.dto.response;

/**
 * Outcome of an admin product delete. {@code deleted} means the row is gone;
 * {@code deactivated} means it was kept because order history references it.
 */
public record ProductDeletionResponse(boolean deleted, boolean deactivated) {
}
