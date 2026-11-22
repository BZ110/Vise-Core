package org.albi.core.modules.KnockbackSynchronization;

import org.bukkit.entity.Player;

public final class KnockbackTrackedPlayerState {

    private Double ping;
    private Double previousPing;
    private Double verticalVelocity;
    private Integer lastDamageTicks;
    private double gravity = 0.08D;
    private long expiresAtMillis;

    public void samplePing(Player player) {
        double currentPing = Math.max(0, KnockbackReflectionAccess.getPing(player));
        this.previousPing = this.ping != null ? this.ping : currentPing;
        this.ping = currentPing;
    }

    public double getPing(Player player) {
        return ping != null ? ping : Math.max(0, KnockbackReflectionAccess.getPing(player));
    }

    public double getPreviousPing(Player player) {
        return previousPing != null ? previousPing : getPing(player);
    }

    public double getCompensatedPing(Player player, KnockbackSynchronizationConfig config) {
        double currentPing = getPing(player);
        double lastPing = getPreviousPing(player);
        double compensated = isSpike(player, config) ? lastPing : currentPing;
        return Math.max(1.0D, compensated - config.getPingOffset());
    }

    public int getCompensatedTicks(Player player, KnockbackSynchronizationConfig config) {
        return (int) Math.ceil(getCompensatedPing(player, config) * config.getTickRate() / 1000.0D);
    }

    public int getTicks(Player player, KnockbackSynchronizationConfig config) {
        return (int) Math.ceil(getPing(player) * config.getTickRate() / 1000.0D);
    }

    public boolean isSpike(Player player, KnockbackSynchronizationConfig config) {
        return (getPing(player) - getPreviousPing(player)) >= config.getSpikeThreshold();
    }

    public Double getCachedPing() {
        return ping;
    }

    public Double getCachedPreviousPing() {
        return previousPing;
    }

    public Double getVerticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double verticalVelocity) {
        this.verticalVelocity = verticalVelocity;
    }

    public Integer getLastDamageTicks() {
        return lastDamageTicks;
    }

    public void setLastDamageTicks(Integer lastDamageTicks) {
        this.lastDamageTicks = lastDamageTicks;
    }

    public double getGravity() {
        return gravity;
    }

    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }

    public boolean isExpired(long now) {
        return now > expiresAtMillis;
    }
}
