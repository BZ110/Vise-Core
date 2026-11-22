package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;

import java.util.ArrayList;
import java.util.List;

public class BotSettings {

    private final List<String> allowedKitNames;

    private Kit kit;
    private Arena arena;
    private int frequentSprintHitsPercent = 60;
    private int frequentWTapsPercent = 30;
    private double frequentRange = 3.00D;
    private boolean invulnerability = true;
    private int strafePercent = 45;
    private int reactionTicks = 2;
    private int attackDelayTicks = 10;
    private int rounds = 1;
    private double moveSpeed = 0.31D;
    private double retreatSpeed = 0.26D;
    private double closeRetreatSpeed = 0.16D;
    private double maxChaseDistance = 500.0D;
    private int shieldUsePercent = 40;
    private boolean shieldBreakEnabled = true;
    private int shieldBreakCooldownTicks = 100;
    private double desyncTeleportDistance = 3.5D;
    private double aimSpeedDegrees = 18.0D;
    private double reachDistance = 3.10D;
    private boolean debugLogging;
    private BotPreset preset = null;

    public BotSettings(List<String> allowedKitNames, int rounds, boolean debugLogging) {
        this(allowedKitNames);
        setRounds(rounds);
        this.debugLogging = debugLogging;

        var kits = getAllowedPublicKits();
        if (!kits.isEmpty()) kit = kits.getFirst();
        if (DuelManager.stor != null) {
            for (Arena candidate : DuelManager.stor.arenas) {
                if (!candidate.deactivated) arena = candidate;
            }
        }
    }

    private BotSettings(List<String> allowedKitNames) {
        this.allowedKitNames = List.copyOf(allowedKitNames);
        resetToDefaults();
    }

    public BotSettings copy() {
        BotSettings clone = new BotSettings(allowedKitNames);
        clone.kit = this.kit;
        clone.arena = this.arena;
        clone.frequentSprintHitsPercent = this.frequentSprintHitsPercent;
        clone.frequentWTapsPercent = this.frequentWTapsPercent;
        clone.frequentRange = this.frequentRange;
        clone.invulnerability = this.invulnerability;
        clone.strafePercent = this.strafePercent;
        clone.reactionTicks = this.reactionTicks;
        clone.attackDelayTicks = this.attackDelayTicks;
        clone.rounds = this.rounds;
        clone.moveSpeed = this.moveSpeed;
        clone.retreatSpeed = this.retreatSpeed;
        clone.closeRetreatSpeed = this.closeRetreatSpeed;
        clone.maxChaseDistance = this.maxChaseDistance;
        clone.shieldUsePercent = this.shieldUsePercent;
        clone.shieldBreakEnabled = this.shieldBreakEnabled;
        clone.shieldBreakCooldownTicks = this.shieldBreakCooldownTicks;
        clone.desyncTeleportDistance = this.desyncTeleportDistance;
        clone.aimSpeedDegrees = this.aimSpeedDegrees;
        clone.reachDistance = this.reachDistance;
        clone.debugLogging = this.debugLogging;
        clone.preset = this.preset;
        return clone;
    }

    public List<String> toLore() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Preset: §f" + (preset == null ? "Custom" : preset.getDisplayName()));
        lore.add("§7Kit: §f" + (kit == null ? "None" : kit.getName()));
        lore.add("§7Map: §f" + (arena == null ? "None" : arena.getName()));
        lore.add("§7Rounds to win: §f" + rounds);
        lore.add("§7Sprint hits: §f" + frequentSprintHitsPercent + "%");
        lore.add("§7W taps: §f" + frequentWTapsPercent + "%");
        lore.add("§7Range: §f" + String.format("%.2f", frequentRange));
        lore.add("§7Invulnerability: " + (invulnerability ? "§aOn" : "§cOff"));
        lore.add("§7Strafe chance: §f" + strafePercent + "%");
        lore.add("§7Reaction: §f" + reactionTicks + "t");
        lore.add("§7Attack delay: §f" + attackDelayTicks + "t");
        lore.add("§7Move speed: §f" + String.format("%.2f", moveSpeed));
        lore.add("§7Retreat speed: §f" + String.format("%.2f", retreatSpeed));
        lore.add("§7Shield use: §f" + shieldUsePercent + "%");
        lore.add("§7Shield break: " + (shieldBreakEnabled ? "§aOn" : "§cOff"));
        lore.add("§7Shield cooldown: §f" + shieldBreakCooldownTicks + "t");
        lore.add("§7Max chase: §f" + String.format("%.1f", maxChaseDistance));
        lore.add("§7Aim speed: §f" + String.format("%.1f", aimSpeedDegrees));
        lore.add("§7Reach: §f" + String.format("%.2f", reachDistance));
        lore.add("§7Desync snap: §f" + String.format("%.1f", desyncTeleportDistance));
        lore.add("§7Debug logs: " + (debugLogging ? "§aOn" : "§cOff"));
        return lore;
    }

    public void resetToDefaults() {
        frequentSprintHitsPercent = 60;
        frequentWTapsPercent = 30;
        frequentRange = 3.00D;
        strafePercent = 45;
        reactionTicks = 2;
        attackDelayTicks = 10;
        moveSpeed = 0.31D;
        retreatSpeed = 0.26D;
        closeRetreatSpeed = 0.16D;
        maxChaseDistance = 80.0D;
        shieldUsePercent = 40;
        shieldBreakEnabled = true;
        shieldBreakCooldownTicks = 100;
        desyncTeleportDistance = 3.5D;
        aimSpeedDegrees = 18.0D;
        reachDistance = 3.10D;
    }

    public void applyPreset(BotPreset preset) {
        if (preset == null)
            return;
        this.preset = preset;

        setFrequentSprintHitsPercent(preset.getSprintHits());
        setFrequentWTapsPercent(preset.getWTaps());
        setFrequentRange(preset.getFreqRange());
        setInvulnerability(preset.isInvuln());
        setStrafePercent(preset.getStrafe());
        setReactionTicks(preset.getReaction());
        setAttackDelayTicks(preset.getAttackDelay());
        setMoveSpeed(preset.getMoveSpeed());
        setRetreatSpeed(preset.getRetreatSpeed());
        setCloseRetreatSpeed(preset.getCloseRetreatSpeed());
        setMaxChaseDistance(preset.getMaxChase());
        setShieldUsePercent(preset.getShieldUse());
        setShieldBreakEnabled(preset.isShieldBreak());
        setShieldBreakCooldownTicks(preset.getShieldCooldown());
        setDesyncTeleportDistance(preset.getDesync());
        setAimSpeedDegrees(preset.getAimSpeed());
        setReachDistance(preset.getReach());
    }

    public boolean isKitAllowed(Kit kit) {
        if (kit == null || kit.getName() == null)
            return false;
        for (String allowed : allowedKitNames) {
            if (allowed.equalsIgnoreCase(kit.getName()))
                return true;
        }
        return false;
    }

    public List<Kit> getAllowedPublicKits() {
        List<Kit> out = new ArrayList<>();
        if (DuelManager.stor == null || DuelManager.stor.kits == null)
            return out;
        for (Kit kit : DuelManager.stor.kits) {
            if (isKitAllowed(kit))
                out.add(kit);
        }
        return out;
    }

    public String getBotNameFor(String playerName) {
        String raw = "PvP Bot";
        return raw.length() <= 16 ? raw : raw.substring(0, 16);
    }

    public Kit getKit() {
        return kit;
    }

    public void setKit(Kit kit) {
        this.kit = kit;
    }

    public Arena getArena() {
        return arena;
    }

    public void setArena(Arena arena) {
        this.arena = arena;
    }

    public int getFrequentSprintHitsPercent() {
        return frequentSprintHitsPercent;
    }

    public void setFrequentSprintHitsPercent(int percent) {
        this.frequentSprintHitsPercent = clamp(percent, 0, 100);
        this.preset = null;
    }

    public int getFrequentWTapsPercent() {
        return frequentWTapsPercent;
    }

    public void setFrequentWTapsPercent(int percent) {
        this.frequentWTapsPercent = clamp(percent, 0, 100);
        this.preset = null;
    }

    public double getFrequentRange() {
        return frequentRange;
    }

    public void setFrequentRange(double range) {
        this.frequentRange = Math.max(0.01D, Math.min(6.0D, range));
        this.preset = null;
    }

    public boolean isInvulnerability() {
        return invulnerability;
    }

    public void setInvulnerability(boolean invulnerability) {
        this.invulnerability = invulnerability;
    }

    public int getStrafePercent() {
        return strafePercent;
    }

    public void setStrafePercent(int strafePercent) {
        this.strafePercent = clamp(strafePercent, 0, 100);
        this.preset = null;
    }

    public int getReactionTicks() {
        return reactionTicks;
    }

    public void setReactionTicks(int reactionTicks) {
        this.reactionTicks = clamp(reactionTicks, 0, 10);
        this.preset = null;
    }

    public int getAttackDelayTicks() {
        return attackDelayTicks;
    }

    public void setAttackDelayTicks(int attackDelayTicks) {
        this.attackDelayTicks = clamp(attackDelayTicks, 4, 20);
        this.preset = null;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = clamp(rounds, 1, 10);
    }

    public double getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(double moveSpeed) {
        this.moveSpeed = clamp(moveSpeed, 0.10D, 0.60D);
        this.preset = null;
    }

    public double getRetreatSpeed() {
        return retreatSpeed;
    }

    public void setRetreatSpeed(double retreatSpeed) {
        this.retreatSpeed = clamp(retreatSpeed, 0.05D, 0.50D);
        this.preset = null;
    }

    public double getCloseRetreatSpeed() {
        return closeRetreatSpeed;
    }

    public void setCloseRetreatSpeed(double closeRetreatSpeed) {
        this.closeRetreatSpeed = clamp(closeRetreatSpeed, 0.05D, 0.45D);
        this.preset = null;
    }

    public double getMaxChaseDistance() {
        return maxChaseDistance;
    }

    public void setMaxChaseDistance(double maxChaseDistance) {
        this.maxChaseDistance = clamp(maxChaseDistance, 0.0D, 2000.0D);
        this.preset = null;
    }

    public int getShieldUsePercent() {
        return shieldUsePercent;
    }

    public void setShieldUsePercent(int shieldUsePercent) {
        this.shieldUsePercent = clamp(shieldUsePercent, 0, 100);
        this.preset = null;
    }

    public boolean isShieldBreakEnabled() {
        return shieldBreakEnabled;
    }

    public void setShieldBreakEnabled(boolean shieldBreakEnabled) {
        this.shieldBreakEnabled = shieldBreakEnabled;
        this.preset = null;
    }

    public int getShieldBreakCooldownTicks() {
        return shieldBreakCooldownTicks;
    }

    public void setShieldBreakCooldownTicks(int shieldBreakCooldownTicks) {
        this.shieldBreakCooldownTicks = clamp(shieldBreakCooldownTicks, 20, 200);
        this.preset = null;
    }

    public double getDesyncTeleportDistance() {
        return desyncTeleportDistance;
    }

    public void setDesyncTeleportDistance(double desyncTeleportDistance) {
        this.desyncTeleportDistance = clamp(desyncTeleportDistance, 0.75D, 10.0D);
        this.preset = null;
    }

    public double getAimSpeedDegrees() {
        return aimSpeedDegrees;
    }

    public void setAimSpeedDegrees(double aimSpeedDegrees) {
        this.aimSpeedDegrees = clamp(aimSpeedDegrees, 4.0D, 40.0D);
        this.preset = null;
    }

    public double getReachDistance() {
        return reachDistance;
    }

    public void setReachDistance(double reachDistance) {
        this.reachDistance = clamp(reachDistance, 0.01D, 6.00D);
        this.preset = null;
    }

    public BotPreset getPreset() {
        return preset;
    }

    public void setPreset(BotPreset preset) {
        this.preset = preset;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}
