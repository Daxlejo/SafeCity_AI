package com.safecityai.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhotoUrlHelper {

    private final String baseUrl;

    public PhotoUrlHelper(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Builds the absolute photo URL to return to the client.
     * - If stored value is already a full URL (starts with http:// or https://), returns it unchanged
     *   for backward-compatibility with legacy DB rows.
     * - Otherwise, prepends baseUrl + "/api/v1/uploads/" to the raw filename.
     * - Returns null if the stored value is null or blank.
     */
    public String getFullPhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
            return photoUrl;
        }
        return baseUrl + "/api/v1/uploads/" + photoUrl;
    }

    /**
     * Extracts the raw filename from a photo URL string.
     * If the client sends a full URL (e.g. "http://localhost:8080/api/v1/uploads/uuid.jpg"),
     * only the last path segment is kept for DB storage.
     * If the value is already a bare filename, it is returned as-is.
     * Returns null if input is null or blank.
     */
    public String extractFilename(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (photoUrl.contains("/")) {
            return photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
        }
        return photoUrl;
    }
}
