package org.albi.core.modules.Duels.ranked;

import org.albi.core.util.ShortIDUtils;

import java.io.Serializable;
import java.util.*;

public class RankedMatchRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String matchId;
    private final UUID player1;
    private final UUID player2;
    private final String player1Name;
    private final String player2Name;
    private final String kitName;
    private final long startTime;
    private long endTime;
    private UUID winner;
    private int eloChange1 = 0;
    private int eloChange2 = 0;

    private final List<HitRecord> hits = new ArrayList<>();
    private final List<PingRecord> pings1 = new ArrayList<>();
    private final List<PingRecord> pings2 = new ArrayList<>();
    private final List<MatchSnapshot> snapshots = new ArrayList<>();
    private final List<Long> clicks1 = new ArrayList<>();
    private final List<Long> clicks2 = new ArrayList<>();
    private double damage1, damage2;
    private int maxCombo1;
    private int maxCombo2;

    private boolean suspicious = false;
    private String suspicionReason = "";

    public RankedMatchRecord(UUID player1, UUID player2, String player1Name, String player2Name, String kitName) {
        this.matchId = ShortIDUtils.generate(8);
        this.player1 = player1;
        this.player2 = player2;
        this.player1Name = player1Name;
        this.player2Name = player2Name;
        this.kitName = kitName;
        this.startTime = System.currentTimeMillis();
    }

    public void addSnapshot(double p1X, double p1Y, double p1Z, double p1H, int p1P, double p2X, double p2Y, double p2Z, double p2H, int p2P) {
        snapshots.add(new MatchSnapshot(p1X, p1Y, p1Z, p1H, p1P, p2X, p2Y, p2Z, p2H, p2P, System.currentTimeMillis()));
    }

    public void addClick(UUID uuid) {
        if (uuid.equals(player1)) clicks1.add(System.currentTimeMillis());
        else clicks2.add(System.currentTimeMillis());
    }

    public void addDamage(UUID uuid, double amount) {
        if (uuid.equals(player1)) damage1 += amount;
        else damage2 += amount;
    }

    public void addHit(UUID attacker, double distance, float yaw, float pitch, double yDiff) {
        hits.add(new HitRecord(attacker, distance, yaw, pitch, yDiff, System.currentTimeMillis()));
        if (distance > 3.6) {
            suspicious = true;
            suspicionReason = "High reach detected: " + String.format("%.2f", distance);
        }
    }

    public void addPing(UUID player, int ping) {
        if (player.equals(player1)) {
            pings1.add(new PingRecord(ping, System.currentTimeMillis()));
        } else {
            pings2.add(new PingRecord(ping, System.currentTimeMillis()));
        }
    }

    public void end() {
        this.endTime = System.currentTimeMillis();
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }

    public int getEloChange1() { return eloChange1; }
    public void setEloChange1(int eloChange1) { this.eloChange1 = eloChange1; }
    public int getEloChange2() { return eloChange2; }
    public void setEloChange2(int eloChange2) { this.eloChange2 = eloChange2; }

    // Getters
    public String getMatchId() { return matchId; }
    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
    public String getKitName() { return kitName; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public List<HitRecord> getHits() { return hits; }
    public List<PingRecord> getPings1() { return pings1; }
    public List<PingRecord> getPings2() { return pings2; }
    public boolean isSuspicious() { return suspicious; }
    public String getSuspicionReason() { return suspicionReason; }
    public UUID getWinner() { return winner; }
    public int getMaxCombo1() { return maxCombo1; }
    public void setMaxCombo1(int maxCombo1) { this.maxCombo1 = maxCombo1; }
    public int getMaxCombo2() { return maxCombo2; }
    public void setMaxCombo2(int maxCombo2) { this.maxCombo2 = maxCombo2; }
    public List<MatchSnapshot> getSnapshots() { return snapshots; }
    public List<Long> getClicks1() { return clicks1; }
    public List<Long> getClicks2() { return clicks2; }
    public double getDamage1() { return damage1; }
    public double getDamage2() { return damage2; }

    public static record HitRecord(UUID attacker, double distance, float yaw, float pitch, double yDiff, long timestamp) implements Serializable {}
    public static record PingRecord(int ping, long timestamp) implements Serializable {}
    public static record MatchSnapshot(double p1X, double p1Y, double p1Z, double p1H, int p1P, double p2X, double p2Y, double p2Z, double p2H, int p2P, long timestamp) implements Serializable {}
}
