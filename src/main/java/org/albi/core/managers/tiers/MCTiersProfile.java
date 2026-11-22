package org.albi.core.managers.tiers;

public class MCTiersProfile extends SuperProfile {
    public MCTiersProfile() {
        super();
        gameModes.add(new GameMode(Mode.MCTIERS_VANILLA, "vanilla"));
        gameModes.add(new GameMode(Mode.MCTIERS_UHC, "uhc"));
        gameModes.add(new GameMode(Mode.MCTIERS_POT, "pot"));
        gameModes.add(new GameMode(Mode.MCTIERS_NETH_OP, "nethop"));
        gameModes.add(new GameMode(Mode.MCTIERS_SMP, "smp"));
        gameModes.add(new GameMode(Mode.MCTIERS_SWORD, "sword"));
        gameModes.add(new GameMode(Mode.MCTIERS_AXE, "axe"));
        gameModes.add(new GameMode(Mode.MCTIERS_MACE, "mace"));
    }
}
