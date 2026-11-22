package org.albi.core.managers.tiers;

public enum Mode {
    // MCTiers
    MCTIERS_VANILLA("Vanilla"),
    MCTIERS_UHC("UHC"),
    MCTIERS_POT("Pot"),
    MCTIERS_NETH_OP("Neth Op"),
    MCTIERS_SMP("SMP"),
    MCTIERS_SWORD("Sword"),
    MCTIERS_AXE("Axe"),
    MCTIERS_MACE("Mace"),

    // PvPTiers
    PVPTIERS_CRYSTAL("Crystal"),
    PVPTIERS_SWORD("Sword"),
    PVPTIERS_UHC("UHC"),
    PVPTIERS_POT("Pot"),
    PVPTIERS_NETH_POT("Neth Pot"),
    PVPTIERS_SMP("SMP"),
    PVPTIERS_AXE("Axe"),
    PVPTIERS_MACE("Mace"),

    // Subtiers
    SUBTIERS_MINECART("Minecart"),
    SUBTIERS_DIAMOND_VANILLA("Diamond Vanilla"),
    SUBTIERS_DEBUFF("DeBuff"),
    SUBTIERS_ELYTRA("Elytra"),
    SUBTIERS_SPEED("Speed"),
    SUBTIERS_CREEPER("Creeper"),
    SUBTIERS_MANHUNT("Manhunt"),
    SUBTIERS_DIAMOND_SMP("Diamond SMP"),
    SUBTIERS_BOW("Bow"),
    SUBTIERS_BED("Bed"),
    SUBTIERS_OG_VANILLA("OG Vanilla"),
    SUBTIERS_TRIDENT("Trident");

    private final String label;

    Mode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
