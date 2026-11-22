package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.gui.GuiService;

import java.util.List;

public enum BotPreset {

        BEAST_EASY("Beast Easy", "beast", "bot-preset.easy-difficulty-for-beast-kit",
                        85, // Sprint Hits %
                        10, // W Taps %
                        2.95, // Frequent Range
                        true, // Invulnerability
                        0, // Strafe %
                        0, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.0// Reach Distance
        ),
        BEAST_HARD("Beast Hard", "beast", "bot-preset.hard-difficulty-for-beast-kit",
                        100, // Sprint Hits %
                        0, // W Taps %
                        3.1, // Frequent Range
                        true, // Invulnerability
                        0, // Strafe %
                        0, // Reaction Ticks
                        11, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.25// Reach Distance
        ),
        BEAST_INSANE("Beast Insane", "beast", "bot-preset.insane-difficulty-for-beast-kit",
                        100, // Sprint Hits %
                        0, // W Taps %
                        3.8, // Frequent Range
                        true, // Invulnerability
                        0, // Strafe %
                        0, // Reaction Ticks
                        11, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.85// Reach Distance
        ),
        SMP_EASY("SMP Easy", "smp", "bot-preset.easy-difficulty-for-smp-kit",
                        75, // Sprint Hits %
                        20, // W Taps %
                        2.95, // Frequent Range
                        true, // Invulnerability
                        50, // Strafe %
                        1, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        50, // Shield Use %
                        true, // Shield Break Enabled
                        100, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.0// Reach Distance
        ),
        SMP_HARD("SMP Hard", "smp", "bot-preset.hard-difficulty-for-smp-kit",
                        100, // Sprint Hits %
                        100, // W Taps %
                        3.60, // Frequent Range
                        true, // Invulnerability
                        100, // Strafe %
                        0, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        25, // Shield Use %
                        true, // Shield Break Enabled
                        100, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.65// Reach Distance
        ),
        NETHERITE_OP_EASY("Netherite OP Easy", "nethop", "bot-preset.easy-difficulty-for-netherite-op-kit",
                        75, // Sprint Hits %
                        20, // W Taps %
                        2.95, // Frequent Range
                        true, // Invulnerability
                        50, // Strafe %
                        1, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.0// Reach Distance
        ),
        NETHERITE_OP_HARD("Netherite OP Hard", "nethop", "bot-preset.hard-difficulty-for-netherite-op-kit",
                        100, // Sprint Hits %
                        100, // W Taps %
                        3.60, // Frequent Range
                        true, // Invulnerability
                        100, // Strafe %
                        0, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.65// Reach Distance
        ),
        POT_EASY("Pot Easy", "pot", "bot-preset.easy-difficulty-for-pot-kit",
                        75, // Sprint Hits %
                        20, // W Taps %
                        2.95, // Frequent Range
                        true, // Invulnerability
                        50, // Strafe %
                        1, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.0// Reach Distance
        ),
        POT_HARD("Pot Hard", "pot", "bot-preset.hard-difficulty-for-pot-kit",
                        100, // Sprint Hits %
                        100, // W Taps %
                        3.20, // Frequent Range
                        true, // Invulnerability
                        100, // Strafe %
                        0, // Reaction Ticks
                        12, // Attack Delay Ticks
                        0.31, // Move Speed
                        0.26, // Retreat Speed
                        0.16, // Close Retreat Speed
                        500, // Max Chase Distance
                        0, // Shield Use %
                        false, // Shield Break Enabled
                        0, // Shield Break Cooldown Ticks
                        2.5, // Desync Teleport Distance
                        15.0, // Aim Speed Degrees
                        3.5// Reach Distance
        );

        private final String displayName;
        private final String kitCategory;
        private final String loreKey;

        // Combat Parameters
        private final int sprintHits;
        private final int wTaps;
        private final double freqRange;
        private final boolean invuln;
        private final int strafe;
        private final int reaction;
        private final int attackDelay;
        private final double moveSpeed;
        private final double retreatSpeed;
        private final double closeRetreatSpeed;
        private final double maxChase;
        private final int shieldUse;
        private final boolean shieldBreak;
        private final int shieldCooldown;
        private final double desync;
        private final double aimSpeed;
        private final double reach;

        BotPreset(String displayName, String kitCategory, String loreKey,
                        int sprintHits, int wTaps, double freqRange, boolean invuln, int strafe,
                        int reaction, int attackDelay, double moveSpeed, double retreatSpeed,
                        double closeRetreatSpeed, double maxChase, int shieldUse, boolean shieldBreak,
                        int shieldCooldown, double desync, double aimSpeed, double reach) {
                this.displayName = displayName;
                this.kitCategory = kitCategory;
                this.loreKey = loreKey;
                this.sprintHits = sprintHits;
                this.wTaps = wTaps;
                this.freqRange = freqRange;
                this.invuln = invuln;
                this.strafe = strafe;
                this.reaction = reaction;
                this.attackDelay = attackDelay;
                this.moveSpeed = moveSpeed;
                this.retreatSpeed = retreatSpeed;
                this.closeRetreatSpeed = closeRetreatSpeed;
                this.maxChase = maxChase;
                this.shieldUse = shieldUse;
                this.shieldBreak = shieldBreak;
                this.shieldCooldown = shieldCooldown;
                this.desync = desync;
                this.aimSpeed = aimSpeed;
                this.reach = reach;
        }

        public String getDisplayName() {
                return displayName;
        }

        public String getKitCategory() {
                return kitCategory;
        }

        public List<String> getLore() {
                return List.of(GuiService.text(loreKey));
        }

        public int getSprintHits() {
                return sprintHits;
        }

        public int getWTaps() {
                return wTaps;
        }

        public double getFreqRange() {
                return freqRange;
        }

        public boolean isInvuln() {
                return invuln;
        }

        public int getStrafe() {
                return strafe;
        }

        public int getReaction() {
                return reaction;
        }

        public int getAttackDelay() {
                return attackDelay;
        }

        public double getMoveSpeed() {
                return moveSpeed;
        }

        public double getRetreatSpeed() {
                return retreatSpeed;
        }

        public double getCloseRetreatSpeed() {
                return closeRetreatSpeed;
        }

        public double getMaxChase() {
                return maxChase;
        }

        public int getShieldUse() {
                return shieldUse;
        }

        public boolean isShieldBreak() {
                return shieldBreak;
        }

        public int getShieldCooldown() {
                return shieldCooldown;
        }

        public double getDesync() {
                return desync;
        }

        public double getAimSpeed() {
                return aimSpeed;
        }

        public double getReach() {
                return reach;
        }
}
