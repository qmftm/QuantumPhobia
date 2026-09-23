package me.qmftm.quantumPhobia.phobia;

public record ActivePhobia(PhobiaType type, long expiresAt) {

    /** The expiry of a phobia that never wears off. */
    public static final long PERMANENT = Long.MAX_VALUE;

    public boolean isPermanent() {
        return expiresAt == PERMANENT;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long remainingMillis() {
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
