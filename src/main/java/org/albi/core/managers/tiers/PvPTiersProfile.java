package org.albi.core.managers.tiers;

public class PvPTiersProfile extends SuperProfile {
    public PvPTiersProfile() {
        super();
        gameModes.add(new GameMode(Mode.PVPTIERS_CRYSTAL, "crystal"));
        gameModes.add(new GameMode(Mode.PVPTIERS_SWORD, "sword"));
        gameModes.add(new GameMode(Mode.PVPTIERS_UHC, "uhc"));
        gameModes.add(new GameMode(Mode.PVPTIERS_POT, "pot"));
        gameModes.add(new GameMode(Mode.PVPTIERS_NETH_POT, "neth_pot"));
        gameModes.add(new GameMode(Mode.PVPTIERS_SMP, "smp"));
        gameModes.add(new GameMode(Mode.PVPTIERS_AXE, "axe"));
        gameModes.add(new GameMode(Mode.PVPTIERS_MACE, "mace"));
    }
}
