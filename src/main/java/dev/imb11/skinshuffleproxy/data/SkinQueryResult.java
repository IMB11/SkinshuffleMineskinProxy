

package dev.imb11.skinshuffleproxy.data;

import org.jetbrains.annotations.Nullable;

public record SkinQueryResult(boolean usesDefaultSkin, @Nullable String skinURL, @Nullable String modelType,
                              @Nullable String textureSignature, @Nullable String textureValue) {
}
