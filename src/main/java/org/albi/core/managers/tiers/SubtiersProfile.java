package org.albi.core.managers.tiers;

public class SubtiersProfile extends SuperProfile {
    public SubtiersProfile() {
        super();
        gameModes.add(new GameMode(Mode.SUBTIERS_MINECART, "minecart"));
        gameModes.add(new GameMode(Mode.SUBTIERS_DIAMOND_VANILLA, "dia_crystal"));
        gameModes.add(new GameMode(Mode.SUBTIERS_DEBUFF, "debuff"));
        gameModes.add(new GameMode(Mode.SUBTIERS_ELYTRA, "elytra"));
        gameModes.add(new GameMode(Mode.SUBTIERS_SPEED, "speed"));
        gameModes.add(new GameMode(Mode.SUBTIERS_CREEPER, "creeper"));
        gameModes.add(new GameMode(Mode.SUBTIERS_MANHUNT, "manhunt"));
        gameModes.add(new GameMode(Mode.SUBTIERS_DIAMOND_SMP, "dia_smp"));
        gameModes.add(new GameMode(Mode.SUBTIERS_BOW, "bow"));
        gameModes.add(new GameMode(Mode.SUBTIERS_BED, "bed"));
        gameModes.add(new GameMode(Mode.SUBTIERS_OG_VANILLA, "og_vanilla"));
        gameModes.add(new GameMode(Mode.SUBTIERS_TRIDENT, "trident"));
    }
}
