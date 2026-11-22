package org.albi.core.util;

import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

public enum TrimCombo {
    SENTRY_IRON("Sentry Iron", TrimPattern.SENTRY, TrimMaterial.IRON, 5),
    DUNE_GOLD("Dune Gold", TrimPattern.DUNE, TrimMaterial.GOLD, 6),
    COAST_COPPER("Coast Copper", TrimPattern.COAST, TrimMaterial.COPPER, 7),
    WILD_AMETHYST("Wild Amethyst", TrimPattern.WILD, TrimMaterial.AMETHYST, 8),
    VEX_EMERALD("Vex Emerald", TrimPattern.VEX, TrimMaterial.EMERALD, 9),
    WARD_LAPIS("Ward Lapis", TrimPattern.WARD, TrimMaterial.LAPIS, 10),
    SNOUT_REDSTONE("Snout Redstone", TrimPattern.SNOUT, TrimMaterial.REDSTONE, 11),
    RIB_QUARTZ("Rib Quartz", TrimPattern.RIB, TrimMaterial.QUARTZ, 12),
    EYE_DIAMOND("Eye Diamond", TrimPattern.EYE, TrimMaterial.DIAMOND, 13),
    SPIRE_NETHERITE("Spire Netherite", TrimPattern.SPIRE, TrimMaterial.NETHERITE, 14),
    SILENCE_AMETHYST("Silence Amethyst", TrimPattern.SILENCE, TrimMaterial.AMETHYST, 15),
    WAYFINDER_GOLD("Wayfinder Gold", TrimPattern.WAYFINDER, TrimMaterial.GOLD, 16),
    RAISER_DIAMOND("Raiser Diamond", TrimPattern.RAISER, TrimMaterial.DIAMOND, 17),
    SHAPER_EMERALD("Shaper Emerald", TrimPattern.SHAPER, TrimMaterial.EMERALD, 18),
    HOST_REDSTONE("Host Redstone", TrimPattern.HOST, TrimMaterial.REDSTONE, 19),
    TIDE_COPPER("Tide Copper", TrimPattern.TIDE, TrimMaterial.COPPER, 20),
    FLOW_LAPIS("Flow Lapis", TrimPattern.FLOW, TrimMaterial.LAPIS, 21),
    BOLT_IRON("Bolt Iron", TrimPattern.BOLT, TrimMaterial.IRON, 22);

    private final String displayName;
    private final TrimPattern pattern;
    private final TrimMaterial material;
    private final int levelRequired;

    TrimCombo(String displayName, TrimPattern pattern, TrimMaterial material, int levelRequired) {
        this.displayName = displayName;
        this.pattern = pattern;
        this.material = material;
        this.levelRequired = levelRequired;
    }

    public String getDisplayName() { return displayName; }
    public TrimPattern getPattern() { return pattern; }
    public TrimMaterial getMaterial() { return material; }
    public int getLevelRequired() { return levelRequired; }
    
    public String getPermission() { 
        return "core.armortrim." + name().toLowerCase(); 
    }
}
