package org.albi.core.data;

import java.util.UUID;

public class TeleportRequest {
    public enum Type { TPA, TPA_HERE }

    public final UUID requesterId;
    public final long expiresAt;
    public final Type type;

    public TeleportRequest(UUID requesterId, long expiresAfterMillis, Type type) {
        this.requesterId = requesterId;
        this.expiresAt = System.currentTimeMillis() + expiresAfterMillis;
        this.type = type;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
