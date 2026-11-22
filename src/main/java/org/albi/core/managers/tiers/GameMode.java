package org.albi.core.managers.tiers;

import com.google.gson.JsonObject;

public class GameMode {
    public Status status = Status.SEARCHING;

    // tier = numeric tier number (1, 2, 3...)
    // pos  = 0 means High Tier, non-0 means Low Tier  (directly from API)
    // retired = is this a retired rank
    private int tier;
    private int pos;
    private boolean retired;

    // Peak values
    private int peakTier;
    private int peakPos;

    public final Mode mode;
    public final String parsingName;

    public GameMode(Mode mode, String parsingName) {
        this.mode = mode;
        this.parsingName = parsingName;
    }

    /**
     * Parse a rankings JSON object for this specific gamemode.
     * Expected fields: tier (int), pos (int), retired (boolean), peak_tier (int), peak_pos (int)
     *
     * pos == 0  =>  High Tier  (HT)
     * pos != 0  =>  Low Tier   (LT)
     */
    public void parseTiers(JsonObject jsonObject) {
        // The API requires tier, pos, attained, and retired to be valid
        if (!jsonObject.has("tier") || !jsonObject.has("pos") ||
            !jsonObject.has("attained") || !jsonObject.has("retired")) {
            status = Status.NOT_EXISTING;
            return;
        }

        this.tier = jsonObject.get("tier").getAsInt();
        this.pos = jsonObject.get("pos").getAsInt();
        this.retired = jsonObject.get("retired").getAsBoolean();

        // Peak tier defaults to current if null
        if (jsonObject.has("peak_tier") && !jsonObject.get("peak_tier").isJsonNull()) {
            this.peakTier = jsonObject.get("peak_tier").getAsInt();
        } else {
            this.peakTier = this.tier;
        }

        if (jsonObject.has("peak_pos") && !jsonObject.get("peak_pos").isJsonNull()) {
            this.peakPos = jsonObject.get("peak_pos").getAsInt();
        } else {
            this.peakPos = this.pos;
        }

        status = Status.READY;
    }

    /**
     * Returns the full tier label, e.g. "High Tier 1", "Low Tier 2", "Retired High Tier 1"
     */
    public String getTierLabel() {
        if (status != Status.READY) return "Unknown";
        String htlt = (pos == 0) ? "High Tier" : "Low Tier";
        String prefix = retired ? "Retired " : "";
        return prefix + htlt + " " + tier;
    }

    /**
     * Returns short display form, e.g. "HT1", "LT2", "RHT1"
     */
    public String getTierShort() {
        if (status != Status.READY) return "?";
        String prefix = retired ? "R" : "";
        String htlt = (pos == 0) ? "HT" : "LT";
        return prefix + htlt + tier;
    }

    /**
     * Returns peak tier label, e.g. "High Tier 1"
     */
    public String getPeakTierLabel() {
        String htlt = (peakPos == 0) ? "High Tier" : "Low Tier";
        return htlt + " " + peakTier;
    }

    /**
     * Whether the peak differs from current tier.
     */
    public boolean hasPeak() {
        return tier != peakTier || pos != peakPos;
    }

    /**
     * Mode display name (e.g. "Crystal", "Neth Pot")
     */
    public String getDisplayName() {
        return mode.getLabel();
    }

    /**
     * Tier points for ranking "highest mode" calculation.
     * Mirrors the actual point values from the reference implementation.
     */
    public int getTierPoints() {
        if (status != Status.READY) return 0;
        String t = getTierShort().replace("R", "");
        boolean isPvP = mode.name().contains("PVPTIERS");

        switch (t.toUpperCase()) {
            case "HT1": return 60;
            case "LT1": return isPvP ? 44 : 45;
            case "HT2": return isPvP ? 28 : 30;
            case "LT2": return isPvP ? 16 : 20;
            case "HT3": return 10;
            case "LT3": return 6;
            case "HT4": return 4;
            case "LT4": return 3;
            case "HT5": return 2;
            case "LT5": return 1;
            default:    return 0;
        }
    }

    @Override
    public String toString() {
        return "GameMode{mode=" + mode + ", tier=" + tier + ", pos=" + pos +
                ", retired=" + retired + ", status=" + status + "}";
    }
}
