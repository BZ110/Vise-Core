package org.albi.core.managers.tiers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public abstract class SuperProfile {
    public Status status = Status.SEARCHING;

    public String region = "Unknown";
    public int points;
    public int overallPosition;

    public final List<GameMode> gameModes = new ArrayList<>();
    public GameMode highest;

    protected SuperProfile() {}

    /**
     * Parse the raw JSON string from any of the three tier APIs.
     * All three APIs (PVPTIERS, MCTIERS, SUBTIERS) use the same format:
     * {
     *   "name": "...",
     *   "region": "NA",
     *   "points": 100,
     *   "overall": 5,
     *   "rankings": {
     *     "sword": { "tier": 1, "pos": 0, "peak_tier": 1, "peak_pos": 0, "attained": ..., "retired": false },
     *     ...
     *   }
     * }
     * If any required top-level field is missing == NOT_EXISTING (player not ranked).
     */
    public void parseJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                status = Status.API_ISSUE;
                return;
            }

            var parsed = JsonParser.parseString(json);
            if (parsed.isJsonNull() || !parsed.isJsonObject()) {
                status = Status.API_ISSUE;
                return;
            }

            JsonObject root = parsed.getAsJsonObject();

            // All required fields must be present — if not, player is not ranked on this service
            if (!root.has("name") || !root.has("region") || !root.has("points") ||
                !root.has("overall") || !root.has("rankings")) {
                status = Status.NOT_EXISTING;
                return;
            }

            // Region can be null (player has no region yet)
            if (root.get("region").isJsonNull()) {
                this.region = "Unknown";
            } else {
                this.region = root.get("region").getAsString();
            }

            this.points = root.get("points").getAsInt();
            this.overallPosition = root.get("overall").getAsInt();

            parseRankings(root.getAsJsonObject("rankings"));

            this.status = Status.READY;
            this.highest = getHighestMode();

        } catch (Exception e) {
            this.status = Status.API_ISSUE;
        }
    }

    private void parseRankings(JsonObject rankings) {
        for (GameMode gameMode : gameModes) {
            if (rankings.has(gameMode.parsingName) && rankings.get(gameMode.parsingName).isJsonObject()) {
                gameMode.parseTiers(rankings.getAsJsonObject(gameMode.parsingName));
            } else {
                gameMode.status = Status.NOT_EXISTING;
            }
        }
    }

    private GameMode getHighestMode() {
        GameMode best = null;
        int bestPoints = 0;
        for (GameMode gm : gameModes) {
            if (gm.status == Status.READY && gm.getTierPoints() > bestPoints) {
                bestPoints = gm.getTierPoints();
                best = gm;
            }
        }
        return best;
    }

    /**
     * Returns the overall rank name based on points.
     * PVPTIERS master threshold is 200, others are 250.
     */
    public String getOverallRankName() {
        boolean isPvP = this instanceof PvPTiersProfile;
        if (!isPvP && points >= 400) return "§5Grandmaster";
        if (!isPvP && points >= 250) return "§6Master";
        if (isPvP  && points >= 200) return "§6Master";
        if (points >= 100) return "§bAce";
        if (points >= 50)  return "§aSpecialist";
        if (points >= 20)  return "§eCadet";
        if (points >= 10)  return "§7Novice";
        return "§fRookie";
    }

    @Override
    public String toString() {
        return "SuperProfile{status=" + status + ", region='" + region + '\'' +
                ", points=" + points + ", overall=" + overallPosition +
                ", gameModes=" + gameModes.size() + "}";
    }
}
