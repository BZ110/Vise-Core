package org.albi.core.modules.Duels.helpers;

public final class DuelPreference {

    private Kit   kit;
    private Arena arena;
    private int   rounds = 1;   // sensible default

    private boolean telemetry = false;

    public Kit   getKit()             { return kit; }
    public Arena getArena()           { return arena; }
    public int   getRounds()          { return rounds; }
    public boolean isTelemetry()      { return telemetry; }

    public DuelPreference setKit(Kit k)       { this.kit = k;       return this; }
    public DuelPreference setArena(Arena a)   { this.arena = a;     return this; }
    public DuelPreference setRounds(int r)    { this.rounds = r;    return this; }
    public DuelPreference setTelemetry(boolean t) { this.telemetry = t; return this; }
}
