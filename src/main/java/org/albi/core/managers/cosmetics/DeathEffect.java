package org.albi.core.managers.cosmetics;

import org.albi.core.Core;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

public enum DeathEffect {
    NONE("None", Material.BARRIER, 1, null, 1.0f),

    SUPERNOVA("Supernova", Material.NETHER_STAR, 5, Sound.ENTITY_GENERIC_EXPLODE, 0.5f),
    BLACK_HOLE("Black Hole", Material.OBSIDIAN, 6, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.1f),
    DNA_HELIX("DNA Helix", Material.CHORUS_FLOWER, 7, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f),
    LOTUS_BLOOM("Lotus Bloom", Material.PINK_TULIP, 8, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f),
    FERMATS_SPIRAL("Sacred Geometry", Material.SUNFLOWER, 9, Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, 1.0f),

    LORENTZ_CHAOS("Lorentz Chaos", Material.DRAGON_EGG, 10, Sound.ENTITY_WITHER_AMBIENT, 0.5f),
    ATOMIC_ORBITAL("Atomic Orbital", Material.RECOVERY_COMPASS, 11, Sound.BLOCK_BEACON_ACTIVATE, 2.0f),
    FROST_CRYSTAL("Frost Crystal", Material.BLUE_ICE, 12, Sound.BLOCK_GLASS_BREAK, 1.8f),
    HELLFIRE_GEYSER("Hellfire Geyser", Material.MAGMA_BLOCK, 13, Sound.ENTITY_GHAST_SHOOT, 0.8f),
    SKULL_OF_DOOM("Skull of Doom", Material.WITHER_SKELETON_SKULL, 14, Sound.ENTITY_WITHER_SPAWN, 0.5f),

    VOID_TENTACLES("Void Tentacles", Material.SCULK_SHRIEKER, 15, Sound.ENTITY_WARDEN_DIG, 0.5f),
    ANGEL_WINGS("Angel Wings", Material.FEATHER, 16, Sound.ENTITY_PHANTOM_FLAP, 1.5f),
    METEOR_STRIKE("Meteor Strike", Material.FIRE_CHARGE, 17, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.7f),
    CHRONOS_WARP("Chronos Warp", Material.CLOCK, 18, Sound.BLOCK_BELL_RESONATE, 1.5f),
    PRISMATIC_BEAM("Prismatic Beam", Material.BEACON, 19, Sound.BLOCK_BEACON_AMBIENT, 1.0f),

    SHATTERED_GLYPHS("Shattered Glyphs", Material.ENCHANTING_TABLE, 20, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f),
    SONIC_BOOM("Sonic Boom", Material.DISC_FRAGMENT_5, 21, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f),
    GALAXY_SWIRL("Galaxy Swirl", Material.END_CRYSTAL, 22, Sound.AMBIENT_BASALT_DELTAS_ADDITIONS, 1.2f),
    MANDELBROT_PULSE("Fractal Pulse", Material.AMETHYST_CLUSTER, 23, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.5f),
    PHARAOH_CURSE("Pharaoh's Curse", Material.SANDSTONE, 24, Sound.ENTITY_HUSK_AMBIENT, 0.8f),

    NEBULA_CLOUD("Nebula Cloud", Material.PURPUR_BLOCK, 25, Sound.BLOCK_PORTAL_AMBIENT, 0.5f),
    HYPERSPACE_JUMP("Hyperspace Jump", Material.FIREWORK_ROCKET, 26, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f),
    RINGS_OF_SATURN("Rings of Saturn", Material.DIORITE, 27, Sound.BLOCK_GRAVEL_STEP, 1.0f),
    MOLTEN_CORE("Molten Core", Material.LAVA_BUCKET, 28, Sound.BLOCK_LAVA_POP, 1.0f),
    ABYSSAL_MAW("Abyssal Maw", Material.SOUL_LANTERN, 29, Sound.ENTITY_GHAST_WARN, 0.5f),

    TSUNAMI_WAVE("Tsunami Wave", Material.WATER_BUCKET, 30, Sound.ITEM_BUCKET_EMPTY, 1.0f),
    BLOOD_RITUAL("Blood Ritual", Material.REDSTONE_BLOCK, 31, Sound.ENTITY_PLAYER_HURT, 0.5f),
    ELECTRIC_STORM("Electric Storm", Material.LIGHTNING_ROD, 32, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f),
    EMERALD_CITY("Emerald City", Material.EMERALD_BLOCK, 33, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f),
    DIVINE_LIGHT("Divine Light", Material.GLOWSTONE, 34, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f);

    private final String displayName;
    private final Material icon;
    private final int requiredLevel;
    private final Sound sound;
    private final float soundPitch;
    private static final Random random = new Random();

    DeathEffect(String displayName, Material icon, int requiredLevel, Sound sound, float soundPitch) {
        this.displayName = displayName;
        this.icon = icon;
        this.requiredLevel = requiredLevel;
        this.sound = sound;
        this.soundPitch = soundPitch;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public Sound getSound() {
        return sound;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public void play(Location loc, Collection<Player> viewers, Core plugin) {
        World world = loc.getWorld();
        if (world == null || viewers == null || viewers.isEmpty())
            return;
        Location center = loc.clone().add(0, 1, 0);

        switch (this) {
            case SUPERNOVA:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 20) { this.cancel(); return; }
                        double r = t * 0.25;
                        for (int i = 0; i < 60; i++) {
                            double[] coords = getFibonacciSphere(i, 60, r);
                            spawn(viewers, Particle.FIREWORK, center.clone().add(coords[0], coords[1], coords[2]), 1, 0, 0, 0, 0.02, null);
                            if (t % 5 == 0) spawn(viewers, Particle.FLASH, center.clone().add(coords[0], coords[1], coords[2]), 1, 0, 0, 0, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case BLACK_HOLE:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 40) { this.cancel(); return; }
                        double r = 3.5 - (t * 0.08);
                        for (int i = 0; i < 25; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double y = (random.nextDouble() - 0.5) * 2.5;
                            double x = Math.cos(angle) * r;
                            double z = Math.sin(angle) * r;
                            spawn(viewers, Particle.SQUID_INK, center.clone().add(x, y, z), 1, -x/8, -y/8, -z/8, 0.15, null);
                            spawn(viewers, Particle.SMOKE, center.clone().add(x, y, z), 1, 0, 0, 0, 0.02, null);
                        }
                        spawn(viewers, Particle.LARGE_SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case DNA_HELIX:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 45) { this.cancel(); return; }
                        double y = t * 0.12;
                        double angle = t * 0.45;
                        double r = 0.85;
                        Location p1 = center.clone().add(r * Math.cos(angle), y - 1.5, r * Math.sin(angle));
                        Location p2 = center.clone().add(r * Math.cos(angle + Math.PI), y - 1.5, r * Math.sin(angle + Math.PI));
                        spawn(viewers, Particle.DUST, p1, 6, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 50, 255), 1.1f));
                        spawn(viewers, Particle.DUST, p2, 6, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(50, 255, 255), 1.1f));
                        if (t % 4 == 0) {
                            for (double d = -r; d <= r; d += 0.25) {
                                spawn(viewers, Particle.END_ROD, center.clone().add(d * Math.cos(angle), y - 1.5, d * Math.sin(angle)), 1, 0, 0, 0, 0, null);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case LOTUS_BLOOM:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 35) { this.cancel(); return; }
                        double size = Math.sin(t * 0.12) * 2.5;
                        for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 40) {
                            double r = size * Math.abs(Math.cos(5 * theta));
                            double x = r * Math.cos(theta);
                            double z = r * Math.sin(theta);
                            spawn(viewers, Particle.DUST, center.clone().add(x, t * 0.02, z), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 150, 200), 1.3f));
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case FERMATS_SPIRAL:
                new BukkitRunnable() {
                    int n = 0;
                    @Override
                    public void run() {
                        if (n++ > 180) { this.cancel(); return; }
                        double c = 0.22;
                        double theta = n * 137.508 * (Math.PI / 180);
                        double r = c * Math.sqrt(n);
                        double x = r * Math.cos(theta);
                        double z = r * Math.sin(theta);
                        spawn(viewers, Particle.GLOW, center.clone().add(x, -0.9, z), 3, 0.06, 0, 0.06, 0.01, null);
                        if (n % 12 == 0) spawn(viewers, Particle.HAPPY_VILLAGER, center.clone().add(x, -0.9, z), 1, 0, 1.2, 0, 0.12, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case LORENTZ_CHAOS:
                new BukkitRunnable() {
                    double x = 0.12, y = 0, z = 0;
                    double sigma = 10, rho = 28, beta = 8/3.0;
                    double dt = 0.01;
                    int count = 0;
                    @Override
                    public void run() {
                        count++; // Advance at top
                        if (count > 220) { this.cancel(); return; }
                        for (int i = 0; i < 6; i++) {
                            double dx = sigma * (y - x) * dt;
                            double dy = (x * (rho - z) - y) * dt;
                            double dz = (x * y - beta * z) * dt;
                            x += dx; y += dy; z += dz;
                            spawn(viewers, Particle.DRAGON_BREATH, center.clone().add(x * 0.11, z * 0.11 - 1, y * 0.11), 1, 0, 0, 0, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case ATOMIC_ORBITAL:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 70) { this.cancel(); return; }
                        spawn(viewers, Particle.END_ROD, center, 3, 0.06, 0.06, 0.06, 0.01, null);
                        drawOrbit(viewers, center, t * 0.22, 0, 0);
                        drawOrbit(viewers, center, t * 0.22, Math.PI / 2.8, Math.PI / 4.5);
                        drawOrbit(viewers, center, t * 0.22, -Math.PI / 2.8, -Math.PI / 4.5);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case FROST_CRYSTAL:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 5) { this.cancel(); return; }
                        for (int i = 0; i < 6; i++) {
                            double angle = i * Math.PI / 3;
                            for (double d = 0; d < 3.0; d += 0.25) {
                                Location l = center.clone().add(d * Math.cos(angle), 0, d * Math.sin(angle));
                                spawn(viewers, Particle.SNOWFLAKE, l, 1, 0, 0, 0, 0, null);
                                if (d > 1.2) {
                                    double subAngle = angle + Math.PI / 6;
                                    spawn(viewers, Particle.SNOWFLAKE, l.clone().add((d-1.2) * Math.cos(subAngle), 0, (d-1.2) * Math.sin(subAngle)), 1, 0, 0, 0, 0, null);
                                    spawn(viewers, Particle.SNOWFLAKE, l.clone().add((d-1.2) * Math.cos(angle - Math.PI / 6), 0, (d-1.2) * Math.sin(angle - Math.PI / 6)), 1, 0, 0, 0, 0, null);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 4);
                break;

            case HELLFIRE_GEYSER:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 35) { this.cancel(); return; }
                        spawn(viewers, Particle.FLAME, center.clone().add(0, -1, 0), 25, 0.35, 2.5, 0.35, 0.25, null);
                        spawn(viewers, Particle.LAVA, center.clone().add(0, -1, 0), 7, 0.6, 0.1, 0.6, 0.1, null);
                        if (t % 4 == 0) {
                            double r = t * 0.12;
                            for (double a = 0; a < 2 * Math.PI; a += Math.PI / 10) {
                                spawn(viewers, Particle.LARGE_SMOKE, center.clone().add(r * Math.cos(a), t * 0.12, r * Math.sin(a)), 1, 0, 0.12, 0, 0.03, null);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case SKULL_OF_DOOM:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 45) { this.cancel(); return; }
                        for (int i = 0; i < 35; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double vertical = Math.pow(random.nextDouble(), 0.4) * 2.2 - 1.1;
                            double radius = Math.sqrt(1 - (vertical/1.1) * (vertical/1.1)) * 0.9;
                            if (vertical < -0.5) radius *= 0.65;
                            spawn(viewers, Particle.SOUL, center.clone().add(radius * Math.cos(angle), vertical + 1.2, radius * Math.sin(angle)), 1, 0, 0, 0, 0.03, null);
                        }
                        spawn(viewers, Particle.FLAME, center.clone().add(0.35, 1.4, 0.65), 3, 0.05, 0.05, 0.05, 0.06, null);
                        spawn(viewers, Particle.FLAME, center.clone().add(-0.35, 1.4, 0.65), 3, 0.05, 0.05, 0.05, 0.06, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case VOID_TENTACLES:
                for (int i = 0; i < 6; i++) {
                    double startAngle = i * 2 * Math.PI / 6;
                    new BukkitRunnable() {
                        int t = 0;
                        @Override
                        public void run() {
                            if (t++ > 30) { this.cancel(); return; }
                            double offset = Math.sin(t * 0.55) * 0.6;
                            double x = Math.cos(startAngle) * (t * 0.18) + offset * Math.cos(startAngle + Math.PI/2);
                            double z = Math.sin(startAngle) * (t * 0.18) + offset * Math.sin(startAngle + Math.PI/2);
                            double y = t * 0.12;
                            spawn(viewers, Particle.SCULK_SOUL, center.clone().add(x, y - 1, z), 2, 0, 0, 0, 0.03, null);
                            spawn(viewers, Particle.SQUID_INK, center.clone().add(x, y - 1, z), 6, 0.12, 0.12, 0.12, 0, null);
                        }
                    }.runTaskTimer(plugin, 0, 1);
                }
                break;

            case ANGEL_WINGS:
                for (int i = -1; i <= 1; i += 2) {
                    final int side = i;
                    new BukkitRunnable() {
                        double t = 0;
                        @Override
                        public void run() {
                            t += 0.18; // Advance at top
                            if (t > Math.PI + 0.18) { this.cancel(); return; }
                            for (double u = 0; u < 1; u += 0.08) {
                                double x = side * (Math.sin(t) * (Math.exp(Math.cos(t)) - 2*Math.cos(4*t) - Math.pow(Math.sin(t/12), 5)));
                                double y = Math.cos(t) * (Math.exp(Math.cos(t)) - 2*Math.cos(4*t) - Math.pow(Math.sin(t/12), 5));
                                spawn(viewers, Particle.CLOUD, center.clone().add(x * 0.55, y * 0.55 + 1.8, -0.2), 1, 0, 0, 0, 0, null);
                                if (random.nextDouble() < 0.1) spawn(viewers, Particle.END_ROD, center.clone().add(x * 0.55, y * 0.55 + 1.8, -0.2), 1, 0, 0, 0, 0, null);
                            }
                        }
                    }.runTaskTimer(plugin, 0, 1);
                }
                break;

            case METEOR_STRIKE:
                new BukkitRunnable() {
                    double y_level = 18;
                    @Override
                    public void run() {
                        y_level -= 1.25; // Advance at top
                        if (y_level <= -1.25) { // Finished
                            spawn(viewers, Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0, null);
                            spawn(viewers, Particle.LAVA, center, 60, 2.5, 0.6, 2.5, 0.6, null);
                            spawn(viewers, Particle.FLASH, center, 1, 0, 0, 0, 0, null);
                            this.cancel();
                            return;
                        }
                        Location trail = center.clone().add(0, y_level + 1.25, 0);
                        spawn(viewers, Particle.FLAME, trail, 25, 0.6, 0.6, 0.6, 0.12, null);
                        spawn(viewers, Particle.LARGE_SMOKE, trail, 12, 0.4, 0.4, 0.4, 0.06, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case CHRONOS_WARP:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 50) { this.cancel(); return; }
                        for (double a = 0; a < 2 * Math.PI; a += Math.PI / 12) {
                            spawn(viewers, Particle.WITCH, center.clone().add(2.2 * Math.cos(a), -0.9, 2.2 * Math.sin(a)), 1, 0, 0, 0, 0, null);
                        }
                        double hourAngle = t * 0.12;
                        double minAngle = t * 0.6;
                        for (double d = 0; d < 1.2; d += 0.22) spawn(viewers, Particle.PORTAL, center.clone().add(d * Math.cos(hourAngle), -0.9, d * Math.sin(hourAngle)), 1, 0, 0, 0, 0, null);
                        for (double d = 0; d < 2.0; d += 0.22) spawn(viewers, Particle.PORTAL, center.clone().add(d * Math.cos(minAngle), -0.9, d * Math.sin(minAngle)), 1, 0, 0, 0, 0, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case PRISMATIC_BEAM:
                new BukkitRunnable() {
                    int t = 0;
                    Color[] colors = { Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE };
                    @Override
                    public void run() {
                        if (t++ > 50) { this.cancel(); return; }
                        for (int h = 0; h < 25; h++) {
                            Color color = colors[(t + h) % colors.length];
                            spawn(viewers, Particle.DUST, center.clone().add(0, h * 0.4 - 5, 0), 6, 0.25, 0, 0.25, 0, new Particle.DustOptions(color, 2.2f));
                        }
                        spawn(viewers, Particle.GUST, center, 2, 0, 0, 0, 0, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case SHATTERED_GLYPHS:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 40) { this.cancel(); return; }
                        for (int i = 0; i < 15; i++) {
                            double x = (random.nextDouble() - 0.5) * 4;
                            double y = (random.nextDouble() - 0.5) * 4;
                            double z = (random.nextDouble() - 0.5) * 4;
                            spawn(viewers, Particle.ENCHANT, center.clone().add(x, y, z), 2, -x/10, -y/10, -z/10, 0.2, null);
                            if (random.nextDouble() < 0.1) spawn(viewers, Particle.WITCH, center.clone().add(x,y,z), 1, 0,0,0,0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case SONIC_BOOM:
                for (int t = 0; t < 4; t++) {
                    final int step = t;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            spawn(viewers, Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0, null);
                            for (double a = 0; a < 2 * Math.PI; a += Math.PI / 20) {
                                double r = 1.2 + step * 1.8;
                                spawn(viewers, Particle.GUST, center.clone().add(r * Math.cos(a), 0, r * Math.sin(a)), 1, 0, 0, 0, 0, null);
                            }
                        }
                    }.runTaskLater(plugin, t * 5);
                }
                break;

            case GALAXY_SWIRL:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 120) { this.cancel(); return; }
                        for (int i = 0; i < 4; i++) {
                            double angle = t * 0.12 + (i * Math.PI / 2);
                            double r = t * 0.045;
                            double x = r * Math.cos(angle);
                            double z = r * Math.sin(angle);
                            spawn(viewers, Particle.DUST, center.clone().add(x, Math.sin(t*0.2)*0.3, z), 4, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(100, 0, 200), 1.2f));
                            if (random.nextDouble() < 0.25) spawn(viewers, Particle.END_ROD, center.clone().add(x + (random.nextDouble()-0.5), random.nextDouble()*0.6, z + (random.nextDouble()-0.5)), 1, 0, 0, 0, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case MANDELBROT_PULSE:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 30) { this.cancel(); return; }
                        double scale = 1.0 + Math.sin(t * 0.3) * 0.5;
                        for (int i = 0; i < 100; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double r = (0.8 + 0.4 * Math.cos(angle * 3)) * scale;
                            double x = r * Math.cos(angle);
                            double z = r * Math.sin(angle);
                            spawn(viewers, Particle.BLOCK, center.clone().add(x, (random.nextDouble()-0.5)*2, z), 1, 0, 0, 0, 0, Material.AMETHYST_CLUSTER.createBlockData());
                            spawn(viewers, Particle.CHERRY_LEAVES, center.clone().add(x, (random.nextDouble()-0.5)*2, z), 1, 0, 0, 0, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case PHARAOH_CURSE:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 60) { this.cancel(); return; }
                        for (int i = 0; i < 25; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double r = 1.6;
                            spawn(viewers, Particle.BLOCK, center.clone().add(r * Math.cos(angle), 3.5 - t * 0.07, r * Math.sin(angle)), 1, 0, 0, 0, 0, Material.SAND.createBlockData());
                        }
                        spawn(viewers, Particle.DUST, center, 12, 1.2, 0, 1.2, 0, new Particle.DustOptions(Color.YELLOW, 1.2f));
                        if (t == 30) spawn(viewers, Particle.ENTITY_EFFECT, center, 50, 1, 2, 1, 0, Color.YELLOW);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case NEBULA_CLOUD:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 45) { this.cancel(); return; }
                        for (int i = 0; i < 30; i++) {
                            double x = (random.nextDouble() - 0.5) * 5;
                            double y = (random.nextDouble() - 0.5) * 5;
                            double z = (random.nextDouble() - 0.5) * 5;
                            Color color = (i % 2 == 0) ? Color.PURPLE : Color.BLUE;
                            spawn(viewers, Particle.DUST, center.clone().add(x, y, z), 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(color, 1.5f));
                            if (random.nextDouble() < 0.05) spawn(viewers, Particle.PORTAL, center.clone().add(x, y, z), 1, 0, 0, 0, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case HYPERSPACE_JUMP:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 15) { this.cancel(); return; }
                        for (int i = 0; i < 40; i++) {
                            Vector v = new Vector(random.nextDouble()-0.5, random.nextDouble()-0.5, random.nextDouble()-0.5).normalize().multiply(t * 0.8);
                            spawn(viewers, Particle.FIREWORK, center.clone().add(v), 0, v.getX(), v.getY(), v.getZ(), 0.5, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case RINGS_OF_SATURN:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 70) { this.cancel(); return; }
                        double angle = t * 0.12;
                        for (double r = 2.2; r < 3.8; r += 0.35) {
                            for (int i = 0; i < 4; i++) {
                                double sa = angle + (i * Math.PI / 2);
                                Vector v = new Vector(r * Math.cos(sa), 0, r * Math.sin(sa));
                                double tilt = Math.toRadians(35);
                                double y = v.getY() * Math.cos(tilt) - v.getZ() * Math.sin(tilt);
                                double z = v.getY() * Math.sin(tilt) + v.getZ() * Math.cos(tilt);
                                spawn(viewers, Particle.DUST, center.clone().add(v.getX(), y, z), 2, 0, 0, 0, 0, new Particle.DustOptions(Color.GRAY, 0.9f));
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case MOLTEN_CORE:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 40) { this.cancel(); return; }
                        spawn(viewers, Particle.LAVA, center, 10, 0.8, 0.2, 0.8, 0.1, null);
                        spawn(viewers, Particle.DRIPPING_LAVA, center.clone().add(0, 2, 0), 5, 0.5, 0, 0.5, 0, null);
                        spawn(viewers, Particle.FLAME, center, 15, 0.6, 0.6, 0.6, 0.05, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case ABYSSAL_MAW:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 25) { this.cancel(); return; }
                        double open = Math.sin(t * Math.PI / 25) * 1.8;
                        for (double x = -2.2; x <= 2.2; x += 0.25) {
                            spawn(viewers, Particle.SOUL, center.clone().add(x, -0.9, open), 1, 0, 0, 0, 0, null);
                            spawn(viewers, Particle.SOUL, center.clone().add(x, -0.9, -open), 1, 0, 0, 0, 0, null);
                        }
                        if (t == 12) spawn(viewers, Particle.LARGE_SMOKE, center, 40, 1, 0.5, 1, 0.1, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case TSUNAMI_WAVE:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 35) { this.cancel(); return; }
                        double x = t * 0.25;
                        for (double z = -3.5; z <= 3.5; z += 0.6) {
                            double y = Math.sin(x + z) * 1.8;
                            spawn(viewers, Particle.SPLASH, center.clone().add(x, y, z), 12, 0.1, 0.1, 0.1, 0.12, null);
                            spawn(viewers, Particle.BUBBLE, center.clone().add(x, y - 0.6, z), 6, 0.25, 0.25, 0.25, 0, null);
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case BLOOD_RITUAL:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 40) { this.cancel(); return; }
                        for (double a = 0; a < 2 * Math.PI; a += Math.PI / 20) {
                            spawn(viewers, Particle.DUST, center.clone().add(2.0 * Math.cos(a), -0.9, 2.0 * Math.sin(a)), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.2f));
                        }
                        if (t % 5 == 0) {
                            for (int i = 0; i < 5; i++) {
                                double angle = i * 2 * Math.PI / 5;
                                spawn(viewers, Particle.DAMAGE_INDICATOR, center.clone().add(1.8 * Math.cos(angle), -0.5, 1.8 * Math.sin(angle)), 5, 0.1, 0.5, 0.1, 0.1, null);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case ELECTRIC_STORM:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 18) { this.cancel(); return; }
                        Location strike = center.clone().add(random.nextInt(8) - 4, 0, random.nextInt(8) - 4);
                        world.strikeLightningEffect(strike);
                        spawn(viewers, Particle.ELECTRIC_SPARK, strike.clone().add(0, 1.2, 0), 60, 0.6, 1.2, 0.6, 0.15, null);
                    }
                }.runTaskTimer(plugin, 0, 3);
                break;

            case EMERALD_CITY:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 30) { this.cancel(); return; }
                        double s = t * 0.12;
                        for (int x = -1; x <= 1; x += 2) {
                            for (int y = -1; y <= 1; y += 2) {
                                for (int z = -1; z <= 1; z += 2) {
                                    spawn(viewers, Particle.HAPPY_VILLAGER, center.clone().add(x * s, y * s, z * s), 5, 0, 0, 0, 0.1, null);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            case DIVINE_LIGHT:
                new BukkitRunnable() {
                    int t = 0;
                    @Override
                    public void run() {
                        if (t++ > 40) { this.cancel(); return; }
                        spawn(viewers, Particle.END_ROD, center.clone().add(0, t * 0.15, 0), 10, 0.5, 0.5, 0.5, 0.05, null);
                        spawn(viewers, Particle.GLOW, center, 20, 1, 2, 1, 0.02, null);
                        if (t == 1) spawn(viewers, Particle.FLASH, center, 5, 0, 0, 0, 0, null);
                    }
                }.runTaskTimer(plugin, 0, 1);
                break;

            default:
                spawn(viewers, Particle.EXPLOSION, center, 30, 0.6, 0.6, 0.6, 0.12, null);
                break;
        }
    }

    private void spawn(Collection<Player> viewers, Particle particle, Location loc, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        if (loc == null || loc.getWorld() == null || viewers == null || viewers.isEmpty()) return;
        
        for (Player p : viewers) {
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != loc.getWorld()) continue; // Same world only
            
            try {
                // Special handling for particles that might require Color but were passed null
                Object finalData = data;
                if (finalData == null) {
                    if (particle == Particle.ENTITY_EFFECT || particle == Particle.FLASH) {
                        finalData = Color.WHITE;
                    }
                }
                
                p.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, finalData);
            } catch (Exception ignored) {
                // Fail silently to prevent console spam and breaking the animation loop
            }
        }
    }

    private double[] getFibonacciSphere(int i, int n, double r) {
        double offset = 2.0 / n;
        double increment = Math.PI * (3.0 - Math.sqrt(5.0));
        double y = ((i * offset) - 1) + (offset / 2);
        double radiusAtY = Math.sqrt(1 - y * y) * r;
        double phi = i * increment;
        return new double[] { Math.cos(phi) * radiusAtY, y * r, Math.sin(phi) * radiusAtY };
    }

    private void drawOrbit(Collection<Player> viewers, Location center, double angle, double tiltX, double tiltZ) {
        double r = 1.35;
        Vector v = new Vector(r * Math.cos(angle), r * Math.sin(angle), 0);
        if (tiltX != 0) {
            double y = v.getY() * Math.cos(tiltX) - v.getZ() * Math.sin(tiltX);
            double z = v.getY() * Math.sin(tiltX) + v.getZ() * Math.cos(tiltX);
            v.setY(y).setZ(z);
        }
        if (tiltZ != 0) {
            double x = v.getX() * Math.cos(tiltZ) - v.getY() * Math.sin(tiltZ);
            double y = v.getX() * Math.sin(tiltZ) + v.getY() * Math.cos(tiltZ);
            v.setX(x).setY(y);
        }
        spawn(viewers, Particle.END_ROD, center.clone().add(v), 1, 0, 0, 0, 0, null);
    }
}
