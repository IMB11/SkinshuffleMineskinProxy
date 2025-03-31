package dev.imb11.skinshuffleproxy.data;

import org.jetbrains.annotations.Nullable;

public record SkinUploadRequest(String type, @Nullable String url, String model) {
}
