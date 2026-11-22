package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.albi.core.modules.Duels.polar.PolarArenaReservation;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.util.TitleFx;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public final class BotSession {

    private final Core plugin;
    private final Manager manager;
    private final Player player;
    private final BotSettings settings;
    private final AutomatedCombatCallbacks callbacks;
    private final Random random = new Random();

    private Arena arena;
    private Kit kit;
    private Location motherLocation;
    private Location playerSpawn;
    private Location botSpawn;
    private PolarArenaReservation polarReservation;
    private PolarArenaReservation nextPolarReservation;
    private BotBackedDuelInstance duelAdapter;

    private FakePlayerNPC npc;
    private BukkitRunnable countdownTask;
    private BukkitRunnable brainTask;

    private boolean fightStarted;
    private boolean closing;
    private boolean controllerEnabled = true;
    private boolean betweenRounds;
    private long worldGuardGraceUntilMillis;
    private int knockbackFreezeTicks;
    private int attackWindupTicks;
    private boolean queuedSprintAttack;

    private int attackCooldown;
    private int reactionWait;
    private int retreatTicks;
    private int strafeDirection = 1;
    private int strafeFlipTicks = 0;
    private int shieldTicks = 0;
    private int shieldBreakCooldownTicksRemaining = 0;
    private int potionUseCooldownTicks = 0;
    private int emergencyHealSplashesRemaining = 0;
    private PotionUseSequence potionUseSequence;

    private int currentRound = 1;
    private int playerRoundsWon = 0;
    private int botRoundsWon = 0;

    private final TreeMap<Integer, ItemStack> botInventory = new TreeMap<>();
    private final Map<PotionEffectType, ManualBotEffect> manualBotEffects = new HashMap<>();
    private final Map<UUID, PendingSplashPotion> pendingSplashPotions = new HashMap<>();

    public BotSession(Core plugin, Manager manager, Player player, BotSettings settings,
            AutomatedCombatCallbacks callbacks) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.settings = settings;
        this.callbacks = callbacks;
    }

    public void start() {
        if (!player.isOnline()) {
            manager.remove(this);
            return;
        }

        this.arena = settings.getArena();
        this.kit = settings.getKit();
        if (arena == null || kit == null) {
            Messages.send(player, "messages.bot-session.you-need-both-a-map-and-a-kit");
            manager.remove(this);
            return;
        }
        if (!settings.isKitAllowed(kit) && !isCustomKit(kit)) {
            Messages.send(player, "messages.bot-session.that-kit-is-not-whitelisted-for-bot-duels");
            manager.remove(this);
            return;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null) {
            Messages.send(player, "messages.bot-session.could-not-load-your-profile");
            manager.remove(this);
            return;
        }

        if (ep.getPlayerDuelInstance() != null && ep.getPlayerDuelInstance().getStatus() != null) {
            Messages.send(player, "messages.bot-session.you-are-already-in-a-duel");
            manager.remove(this);
            return;
        }

        if (usingPolarArena()) {
            polarReservation = DuelManager.polarArenaManager.reserveArena(arena);
            if (polarReservation == null) {
                Messages.send(player, "messages.bot-session.no-duel-instances-for-that-map-are-currently");
                manager.remove(this);
                return;
            }
            motherLocation = polarReservation.getMotherLocation();
        } else {
            motherLocation = reserveArena(arena);
            if (motherLocation == null) {
                Messages.send(player, "messages.bot-session.no-duel-instances-for-that-map-are-currently");
                manager.remove(this);
                return;
            }
            DuelManager.schemManager.enqueueSchematic(motherLocation, getSchematicFile(arena), true);
        }

        playerSpawn = absolute(arena, motherLocation, arena.getSpawn1());
        botSpawn = absolute(arena, motherLocation, arena.getSpawn2());
        duelAdapter = new BotBackedDuelInstance(plugin, this, arena, motherLocation);

        plugin.resetPlayer(player);
        armWorldGuardGrace("start-duel-teleport");
        boolean teleported = player.teleport(playerSpawn);
        if (!teleported) {
            Messages.send(player, "messages.bot-session.failed-to-move-you-into-the-duel-arena");
            forceClose(false, "teleport-failed");
            return;
        }

        giveKit(player, kit);
        applyEffects(player, kit);
        refreshPlayerHealthVisual();
        rebuildBotInventory();

        PlayerDuelInstance pdi = new PlayerDuelInstance();
        pdi.setPlayer(player);
        pdi.setStatus("Countdown");
        pdi.setDuelInstance(duelAdapter);
        pdi.setKit(kit);
        pdi.setTeam(1);
        pdi.setTeammates(new Player[] { player });
        pdi.setOpponents(new Player[0]);
        ep.setPlayerDuelInstance(pdi);

        if (!spawnNpc()) {
            Messages.send(player, "messages.bot-session.the-bot-npc-could-not-be-created");
            forceClose(false, "npc-spawn-failed");
            return;
        }

        player.sendTitle(Messages.text(player, "messages.bot-session.bot-duel"), Messages.text(player, "messages.bot-session.1"), 0, 20, 0);
        if (callbacks != null)
            callbacks.onSessionCreated(this);
        startCountdown();
        return;
    }

    private boolean isCustomKit(Kit kit) {
        if (kit == null || kit.getName() == null)
            return false;
        return kit.getName().toLowerCase(Locale.ROOT).contains("custom kit");
    }

    private void startCountdown() {
        betweenRounds = false;
        fightStarted = false;
        if (callbacks != null)
            callbacks.onCountdownStarted(this);

        cancelTask(countdownTask);
        countdownTask = new BukkitRunnable() {
            int ticks = 60;
            boolean firstToggleSoundPlayed = false;
            java.util.Set<UUID> readyToggles = new java.util.HashSet<>();
            java.util.Map<UUID, Boolean> lastSneak = new java.util.HashMap<>();

            @Override
            public void run() {
                if (closing || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (!validateRuntimeState()) {
                    cancel();
                    return;
                }

                boolean sneaks = player.isSneaking();
                boolean was = lastSneak.getOrDefault(player.getUniqueId(), false);
                if (sneaks && !was) {
                    boolean wasReady = readyToggles.contains(player.getUniqueId());
                    if (wasReady)
                        readyToggles.remove(player.getUniqueId());
                    else {
                        readyToggles.add(player.getUniqueId());
                        if (!firstToggleSoundPlayed) {
                            firstToggleSoundPlayed = true;
                            player.playSound(player, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
                        }
                    }
                }
                lastSneak.put(player.getUniqueId(), sneaks);

                player.sendActionBar(Messages.text(player, "messages.match-hud.skip-countdown", readyToggles.size(), 1));

                if (readyToggles.size() >= 1) {
                    ticks = 0;
                    player.sendTitle(Messages.text(player, "messages.bot-session.skipped"), Messages.text(player, "messages.bot-session.readied-up-starting-now"), 0, 20, 10);
                }

                if (ticks % 20 == 0) {
                    int seconds = Math.max(1, ticks / 20);
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    player.sendTitle(Messages.text(player, "messages.bot-session.round", currentRound), Messages.text(player, "messages.match-hud.countdown-seconds", seconds), 0, 20, 0);
                    if (!player.getWorld().equals(playerSpawn.getWorld())
                            || player.getLocation().distanceSquared(playerSpawn) > 4.0D) {
                        player.teleport(playerSpawn);
                    }
                    if (npc != null) {
                        npc.resetMotion();
                        npc.lookAt(player);
                    }
                }
                if (--ticks <= 0) {
                    cancel();
                    beginFight();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void beginFight() {
        fightStarted = true;
        betweenRounds = false;
        attackCooldown = settings.getAttackDelayTicks();
        reactionWait = settings.getReactionTicks();
        retreatTicks = 0;
        shieldTicks = 0;
        strafeFlipTicks = 0;
        strafeDirection = random.nextBoolean() ? 1 : -1;
        knockbackFreezeTicks = 0;
        attackWindupTicks = 0;
        queuedSprintAttack = false;
        potionUseCooldownTicks = 0;
        emergencyHealSplashesRemaining = 0;
        potionUseSequence = null;
        manualBotEffects.clear();
        pendingSplashPotions.clear();

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep != null && ep.getPlayerDuelInstance() != null) {
            ep.getPlayerDuelInstance().setStatus("InGame");
        }

        if (npc != null) {
            npc.resetKitEquipment();
            npc.setHealth(maxHealth(npc.getBukkitPlayer()));
            npc.resetMotion();
        }

        TitleFx.playFight(plugin, player,
                Messages.text(player, "messages.match-hud.bot-fight-subtitle", currentRound, playerRoundsWon, botRoundsWon));
        player.playSound(player, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        if (callbacks != null)
            callbacks.onFightStarted(this);

        cancelTask(brainTask);
        brainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (closing || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (!fightStarted || npc == null || !npc.isAlive()) {
                    cancel();
                    return;
                }
                if (!validateRuntimeState()) {
                    cancel();
                    return;
                }
                tickBot();
            }
        };
        brainTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickBot() {
        if (callbacks != null)
            callbacks.onTick(this);
        if (!controllerEnabled || npc == null) {
            if (npc != null)
                npc.lookAt(player);
            return;
        }

        if (attackCooldown > 0)
            attackCooldown--;
        if (reactionWait > 0)
            reactionWait--;
        if (retreatTicks > 0)
            retreatTicks--;
        if (shieldTicks > 0)
            shieldTicks--;
        if (shieldBreakCooldownTicksRemaining > 0)
            shieldBreakCooldownTicksRemaining--;
        if (potionUseCooldownTicks > 0)
            potionUseCooldownTicks--;
        if (knockbackFreezeTicks > 0)
            knockbackFreezeTicks--;
        if (attackWindupTicks > 0)
            attackWindupTicks--;
        if (strafeFlipTicks-- <= 0) {
            strafeFlipTicks = 8 + random.nextInt(10);
            strafeDirection = random.nextBoolean() ? 1 : -1;
        }

        Player botPlayer = npc.getBukkitPlayer();
        if (botPlayer == null || !botPlayer.isValid())
            return;
        clearActualBotPotionEffects(botPlayer);
        tickManualBotEffects(botPlayer);

        Location botLoc = botPlayer.getLocation();
        Location targetLoc = player.getLocation();
        Location targetEye = player.getEyeLocation();
        double distance = botLoc.distance(targetLoc);
        if (distance > settings.getMaxChaseDistance()) {
            finishRound(true, "too-far");
            return;
        }

        float targetYaw = BotMath.yawTo(botPlayer.getEyeLocation(), targetEye);
        float targetPitch = BotMath.pitchTo(botPlayer.getEyeLocation(), targetEye);
        float smoothYaw = BotMath.approachAngle(botLoc.getYaw(), targetYaw, (float) settings.getAimSpeedDegrees());
        float smoothPitch = BotMath.approachAngle(botLoc.getPitch(), targetPitch,
                (float) settings.getAimSpeedDegrees());

        if (tickPotionUseSequence(botPlayer, smoothYaw)) {
            return;
        }

        if (maybeStartPotionUseSequence(botPlayer, distance, smoothYaw)) {
            return;
        }

        boolean shouldShield = shouldRaiseShield(distance);
        npc.setBlocking(shouldShield);

        boolean steeringLocked = knockbackFreezeTicks > 0 || npc.isSteeringLocked();
        if (steeringLocked) {
            npc.setSprinting(false);
            npc.lookAt(player);
        }
        // Commit sprint one tick before the actual hit so sprint knockback lands
        // properly.
        if (attackWindupTicks > 0) {
            npc.setSprinting(queuedSprintAttack);
            npc.lookAt(player);
            if (attackWindupTicks <= 1 && reactionWait <= 0 && canAttack(distance)) {
                attackPlayer(queuedSprintAttack);
                return;
            }
        }

        if (reactionWait <= 0 && attackWindupTicks <= 0 && canAttack(distance)) {
            if (shouldQueueSprintHit(botPlayer, distance)) {
                queuedSprintAttack = true;
                attackWindupTicks = 2;
                npc.setSprinting(true);
                npc.lookAt(player);
                return;
            } else {
                attackPlayer(false);
                return;
            }
        }

        Vector toPlayer = targetLoc.toVector().subtract(botLoc.toVector());
        Vector flat = toPlayer.clone().setY(0);
        Vector move = new Vector();

        double moveSpeed = effectiveMoveSpeed(botPlayer);
        double retreatSpeed = settings.getRetreatSpeed();
        double closeRetreatSpeed = settings.getCloseRetreatSpeed();

        if (flat.lengthSquared() > 0.0001D) {
            Vector forward = flat.clone().normalize();
            Vector sideways = new Vector(-forward.getZ(), 0.0D, forward.getX());

            double desiredRange = settings.getFrequentRange();
            double forwardSpeed = 0.0D;

            // Reduced buffer from 0.20 to 0.10 for more precise aiming of frequent range
            if (distance > desiredRange + 0.10D) {
                forwardSpeed = moveSpeed;
            } else if (distance < desiredRange - 0.10D || retreatTicks > 0) {
                // Use closeRetreatSpeed if very close, otherwise standard retreatSpeed
                forwardSpeed = -(distance < 1.0D ? closeRetreatSpeed : retreatSpeed);
            }

            double sideSpeed = 0.0D;
            if (roll(settings.getStrafePercent())) {
                sideSpeed = moveSpeed * 0.45D * strafeDirection;
            }

            move = forward.multiply(forwardSpeed).add(sideways.multiply(sideSpeed));
            double max = Math.max(0.08D, moveSpeed);
            if (move.lengthSquared() > max * max) {
                move.normalize().multiply(max);
            }
        }

        Location next = botLoc.clone().add(move);
        next.setYaw(smoothYaw);
        next.setPitch(smoothPitch);
        npc.move(player, next, Math.max(0.08D, moveSpeed));
        npc.setSprinting(!steeringLocked
                && (distance > settings.getFrequentRange() || attackWindupTicks > 0 || queuedSprintAttack));
    }

    private boolean shouldRaiseShield(double distance) {
        if (npc == null || !npc.hasShieldOffHand())
            return false;
        if (attackCooldown <= 2)
            return false;
        if (distance > settings.getFrequentRange() + 1.15D)
            return false;
        if (!roll(settings.getShieldUsePercent()))
            return false;
        shieldTicks = Math.max(shieldTicks, 5 + random.nextInt(5));
        return shieldTicks > 0;
    }

    private boolean canAttack(double distance) {
        Player botPlayer = npc == null ? null : npc.getBukkitPlayer();
        // Since the Fake Player has no real network ticker, its attack cooldown is
        // perpetually 0.
        // We rely entirely on our synthetic attackCooldown field instead.
        return distance <= settings.getReachDistance()
                && botPlayer != null
                && botPlayer.hasLineOfSight(player)
                && attackCooldown <= 0;
    }

    private void attackPlayer(boolean sprintHit) {
        if (npc == null)
            return;

        Player botPlayer = npc.getBukkitPlayer();
        boolean targetBlocking = isPlayerActivelyShielding(player);
        ItemStack weapon = chooseAttackWeapon(targetBlocking);

        npc.setMainHand(weapon);
        npc.setBlocking(false);
        npc.setSprinting(sprintHit);

        // NMS mock hitting triggered anti-cheat and desyncs on intercepted
        // environments.
        // We bypass everything and manually deduct health, play animations, and invoke
        // exact KB vectors.
        npc.swing(player);

        if (botPlayer != null) {
            double baseDamage = resolveBaseDamage(weapon) + manualStrengthBonus();
            boolean shieldBroken = maybeBreakTargetShield(player, botPlayer, weapon, targetBlocking);

            if (settings.isInvulnerability()) {
                try {
                    player.playHurtAnimation(0.0F);
                } catch (Throwable ignored) {
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
                applyManualVanillaKnockback(player, botPlayer, sprintHit);
            } else {
                Vector beforeVelocity = player.getVelocity().clone();
                player.damage(baseDamage, botPlayer);
                supplementPlayerKnockbackIfNeeded(player, botPlayer, beforeVelocity, sprintHit, shieldBroken);
            }

            if (sprintHit) {
                player.playSound(botPlayer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
            } else if (weapon != null && weapon.getType().name().contains("SWORD")) {
                player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 1,
                        0, 0, 0, 0);
                player.playSound(botPlayer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
            } else {
                player.playSound(botPlayer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
            }
        }

        // Make W taps much lighter.
        if (!sprintHit && roll(settings.getFrequentWTapsPercent())) {
            retreatTicks = Math.max(retreatTicks, 1);
        }

        attackCooldown = settings.getAttackDelayTicks();
        reactionWait = settings.getReactionTicks();
        if (settings.isDebugLogging())
            plugin.getLogger().warning("[AutomatedCombat][DEBUG][" + player.getName() + "] Bot fired hit -> sprint="
                    + sprintHit + ", type=" + (weapon != null ? weapon.getType().name() : "HAND"));
        attackWindupTicks = 0;
        queuedSprintAttack = false;

        if (callbacks != null)
            callbacks.onBotAttack(this, resolveBaseDamage(weapon));
    }

    private boolean isPlayerActivelyShielding(Player target) {
        if (target == null || !target.isBlocking())
            return false;
        ItemStack main = target.getInventory().getItemInMainHand();
        ItemStack off = target.getInventory().getItemInOffHand();
        return isShield(main) || isShield(off);
    }

    private boolean isShield(ItemStack stack) {
        return stack != null && stack.getType() == Material.SHIELD;
    }

    private boolean maybeBreakTargetShield(Player target, Player botPlayer, ItemStack weapon, boolean targetBlocking) {
        if (target == null || botPlayer == null)
            return false;
        if (!settings.isShieldBreakEnabled())
            return false;
        if (!targetBlocking)
            return false;
        if (!isAxe(weapon))
            return false;
        if (shieldBreakCooldownTicksRemaining > 0)
            return false;

        shieldBreakCooldownTicksRemaining = settings.getShieldBreakCooldownTicks();
        target.setCooldown(Material.SHIELD, settings.getShieldBreakCooldownTicks());
        forceReleaseActiveItem(target);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
        botPlayer.playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
        debug("Shield break applied -> cooldown=" + settings.getShieldBreakCooldownTicks());
        return true;
    }

    private void forceReleaseActiveItem(Player target) {
        if (target == null)
            return;
        try {
            target.getClass().getMethod("clearActiveItem").invoke(target);
        } catch (Throwable ignored) {
        }
    }

    private void applyManualVanillaKnockback(Player target, Player attacker, boolean sprintHit) {
        if (target == null || attacker == null)
            return;

        Vector kb = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0.0D);
        if (kb.lengthSquared() <= 0.0D)
            return;

        kb.normalize().multiply(sprintHit ? 0.7D : 0.4D);
        Vector currentVel = target.getVelocity();
        double nx = (currentVel.getX() * 0.5D) + kb.getX();
        double nz = (currentVel.getZ() * 0.5D) + kb.getZ();
        double ny = target.isOnGround() ? 0.4D : currentVel.getY();
        target.setVelocity(new Vector(nx, ny, nz));
    }

    private void supplementPlayerKnockbackIfNeeded(Player target, Player attacker, Vector beforeVelocity,
            boolean sprintHit, boolean shieldBroken) {
        if (target == null || attacker == null || beforeVelocity == null)
            return;
        if (shieldBroken)
            return;

        Vector dir = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0.0D);
        if (dir.lengthSquared() <= 0.0D)
            return;
        dir.normalize();

        double desiredHorizontal = sprintHit ? 0.7D : 0.4D;
        Vector afterVelocity = target.getVelocity();
        double horizontalDeltaX = afterVelocity.getX() - beforeVelocity.getX();
        double horizontalDeltaZ = afterVelocity.getZ() - beforeVelocity.getZ();
        double projected = (horizontalDeltaX * dir.getX()) + (horizontalDeltaZ * dir.getZ());
        double missing = desiredHorizontal - projected;

        if (missing <= 0.08D)
            return;

        double correction = Math.min(missing, sprintHit ? 0.20D : 0.10D);
        double nx = afterVelocity.getX() + (dir.getX() * correction);
        double nz = afterVelocity.getZ() + (dir.getZ() * correction);
        double ny = afterVelocity.getY();
        if (sprintHit && target.isOnGround() && ny < 0.36D) {
            ny = 0.4D;
        }
        target.setVelocity(new Vector(nx, ny, nz));
    }

    private boolean shouldQueueSprintHit(Player botPlayer, double distance) {
        if (botPlayer == null)
            return false;
        if (settings.getFrequentSprintHitsPercent() <= 0)
            return false;
        return roll(settings.getFrequentSprintHitsPercent());
    }

    private boolean maybeStartPotionUseSequence(Player botPlayer, double distance, float combatYaw) {
        if (botPlayer == null || npc == null)
            return false;
        if (potionUseSequence != null)
            return false;
        if (potionUseCooldownTicks > 0 && emergencyHealSplashesRemaining <= 0)
            return false;
        if (knockbackFreezeTicks > 0 || npc.isSteeringLocked())
            return false;
        if (attackWindupTicks > 0)
            return false;

        boolean forceHealing = emergencyHealSplashesRemaining > 0;
        if (!forceHealing && isEmergencyInstantHealthThreshold() && countAvailableHealingSplashes() >= 2) {
            emergencyHealSplashesRemaining = 2;
            forceHealing = true;
        }
        PotionUsePlan plan = choosePotionUsePlan(botPlayer, distance, forceHealing);
        if (plan == null)
            return false;

        potionUseSequence = new PotionUseSequence(
                plan,
                1,
                combatYaw);

        npc.setBlocking(false);
        npc.setSprinting(false);
        npc.resetMotion();
        npc.setMainHand(plan.stack().clone());
        lookDownForPotion(combatYaw);

        shieldTicks = 0;
        queuedSprintAttack = false;
        attackWindupTicks = 0;
        return true;
    }

    private boolean tickPotionUseSequence(Player botPlayer, float combatYaw) {
        if (botPlayer == null || potionUseSequence == null || npc == null)
            return false;

        PotionUseSequence sequence = potionUseSequence;
        npc.setBlocking(false);
        npc.setSprinting(false);
        npc.resetMotion();
        npc.setMainHand(sequence.plan().stack().clone());
        lookDownForPotion(sequence.lookYaw());

        if (sequence.ticksRemaining() > 0) {
            sequence.setTicksRemaining(sequence.ticksRemaining() - 1);
            return true;
        }

        launchVisibleSplashPotion(botPlayer, sequence.plan());
        consumeBotInventoryItem(sequence.plan().slot());
        finishPotionUseSequence(sequence.plan());
        return true;
    }

    private void finishPotionUseSequence(PotionUsePlan plan) {
        if (plan == null) {
            potionUseSequence = null;
            return;
        }

        potionUseCooldownTicks = 4;
        attackCooldown = Math.max(attackCooldown, 6);
        reactionWait = Math.max(reactionWait, 2);
        retreatTicks = Math.max(retreatTicks, 4);
        potionUseSequence = null;

        if (plan.healing() && emergencyHealSplashesRemaining > 0) {
            emergencyHealSplashesRemaining--;
            if (emergencyHealSplashesRemaining > 0) {
                potionUseCooldownTicks = 0;
            }
        }

        npc.setMainHand(chooseAttackWeapon(isPlayerActivelyShielding(player)));
        debug("Bot used potion -> slot=" + plan.slot()
                + ", type=" + plan.stack().getType().name()
                + ", reason=" + plan.reason());
    }

    private PotionUsePlan choosePotionUsePlan(Player botPlayer, double distance, boolean forceHealing) {
        PotionUsePlan healPlan = null;
        PotionUsePlan buffPlan = null;

        for (Map.Entry<Integer, ItemStack> entry : botInventory.entrySet()) {
            ItemStack stack = entry.getValue();
            if (!isPotionItem(stack))
                continue;

            List<PotionEffect> effects = extractPotionEffects(stack);
            if (effects.isEmpty())
                continue;

            boolean healing = false;
            boolean beneficial = false;
            String reason = null;

            for (PotionEffect effect : effects) {
                PotionEffectType type = effect.getType();
                if (type == null)
                    continue;

                if (PotionEffectType.INSTANT_HEALTH.equals(type)) {
                    if (!isSplashPotion(stack))
                        continue;
                    if (shouldUseHealingPotion(botPlayer)) {
                        healing = true;
                        beneficial = true;
                        reason = "healing";
                    }
                    continue;
                }

                if (!isSelfBuffEffect(type))
                    continue;
                if (!shouldRefreshPotionEffect(effect))
                    continue;

                beneficial = true;
                if (reason == null) {
                    reason = type.getKey().getKey();
                }
            }

            if (!beneficial)
                continue;

            PotionUsePlan plan = new PotionUsePlan(
                    entry.getKey(),
                    stack.clone(),
                    effects,
                    healing,
                    reason == null ? "utility" : reason);

            if (healing) {
                if (healPlan == null || preferPotion(plan, healPlan)) {
                    healPlan = plan;
                }
            } else if (buffPlan == null || preferPotion(plan, buffPlan)) {
                buffPlan = plan;
            }
        }

        if (forceHealing)
            return healPlan;
        return healPlan != null ? healPlan : buffPlan;
    }

    private boolean preferPotion(PotionUsePlan candidate, PotionUsePlan existing) {
        if (candidate == null)
            return false;
        if (existing == null)
            return true;
        if (candidate.healing() != existing.healing())
            return candidate.healing();
        int candidateTypeRank = potionTypePreference(candidate.stack());
        int existingTypeRank = potionTypePreference(existing.stack());
        if (candidateTypeRank != existingTypeRank)
            return candidateTypeRank < existingTypeRank;
        return candidate.slot() < existing.slot();
    }

    private int potionTypePreference(ItemStack stack) {
        if (stack == null)
            return Integer.MAX_VALUE;
        if (isSplashPotion(stack))
            return 0;
        if (stack.getType() == Material.LINGERING_POTION)
            return 1;
        return Integer.MAX_VALUE;
    }

    private boolean shouldUseHealingPotion(Player botPlayer) {
        double deficit = maxHealth(botPlayer) - botPlayer.getHealth();
        return deficit >= 4.0D;
    }

    private boolean isEmergencyInstantHealthThreshold() {
        return npc != null && npc.getBukkitPlayer() != null && npc.getBukkitPlayer().getHealth() <= 5.0D;
    }

    private int countAvailableHealingSplashes() {
        int total = 0;
        for (ItemStack stack : botInventory.values()) {
            if (!isSplashPotion(stack))
                continue;
            for (PotionEffect effect : extractPotionEffects(stack)) {
                if (PotionEffectType.INSTANT_HEALTH.equals(effect.getType())) {
                    total += Math.max(1, stack.getAmount());
                    break;
                }
            }
        }
        return total;
    }

    private boolean shouldRefreshPotionEffect(PotionEffect desired) {
        PotionEffectType type = desired.getType();
        if (type == null)
            return false;

        if (PotionEffectType.REGENERATION.equals(type) && npc != null && npc.getBukkitPlayer() != null
                && npc.getBukkitPlayer().getHealth() >= maxHealth(npc.getBukkitPlayer()) - 2.0D) {
            return false;
        }

        ManualBotEffect active = manualBotEffects.get(type);
        if (active == null)
            return true;
        if (active.amplifier() < desired.getAmplifier())
            return true;
        if (active.amplifier() > desired.getAmplifier())
            return false;
        return active.ticksRemaining() <= 60;
    }

    private boolean isSelfBuffEffect(PotionEffectType type) {
        return PotionEffectType.SPEED.equals(type)
                || PotionEffectType.STRENGTH.equals(type)
                || PotionEffectType.REGENERATION.equals(type)
                || PotionEffectType.FIRE_RESISTANCE.equals(type)
                || PotionEffectType.RESISTANCE.equals(type)
                || PotionEffectType.JUMP_BOOST.equals(type)
                || PotionEffectType.SLOW_FALLING.equals(type);
    }

    private boolean isPotionItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            return false;
        return stack.getType() == Material.SPLASH_POTION
                || stack.getType() == Material.LINGERING_POTION;
    }

    private boolean isSplashPotion(ItemStack stack) {
        return stack != null && stack.getType() == Material.SPLASH_POTION;
    }

    private List<PotionEffect> extractPotionEffects(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return List.of();
        }

        Map<PotionEffectType, PotionEffect> effects = new LinkedHashMap<>();
        for (PotionEffect effect : potionMeta.getCustomEffects()) {
            if (effect != null
                    && effect.getType() != null
                    && (isSelfBuffEffect(effect.getType())
                            || PotionEffectType.INSTANT_HEALTH.equals(effect.getType()))) {
                effects.put(effect.getType(), effect);
            }
        }

        PotionEffect baseEffect = buildBasePotionEffect(potionMeta.getBasePotionData());
        if (baseEffect != null && baseEffect.getType() != null && !effects.containsKey(baseEffect.getType())) {
            effects.put(baseEffect.getType(), baseEffect);
        }

        return new ArrayList<>(effects.values());
    }

    private PotionEffect buildBasePotionEffect(PotionData data) {
        if (data == null || data.getType() == null)
            return null;

        PotionType type = data.getType();
        boolean extended = data.isExtended();
        boolean upgraded = data.isUpgraded();

        return switch (type) {
            case HEALING -> new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, upgraded ? 1 : 0, false, true, true);
            case SWIFTNESS -> new PotionEffect(PotionEffectType.SPEED, upgraded ? 1800 : (extended ? 9600 : 3600),
                    upgraded ? 1 : 0, false, true, true);
            case STRENGTH -> new PotionEffect(PotionEffectType.STRENGTH, upgraded ? 1800 : (extended ? 9600 : 3600),
                    upgraded ? 1 : 0, false, true, true);
            case REGENERATION -> new PotionEffect(PotionEffectType.REGENERATION,
                    upgraded ? 450 : (extended ? 1800 : 900),
                    upgraded ? 1 : 0, false, true, true);
            case FIRE_RESISTANCE -> new PotionEffect(PotionEffectType.FIRE_RESISTANCE, extended ? 9600 : 3600,
                    0, false, true, true);
            case LEAPING -> new PotionEffect(PotionEffectType.JUMP_BOOST, upgraded ? 1800 : (extended ? 9600 : 3600),
                    upgraded ? 1 : 0, false, true, true);
            case SLOW_FALLING -> new PotionEffect(PotionEffectType.SLOW_FALLING, extended ? 4800 : 1800,
                    0, false, true, true);
            default -> null;
        };
    }

    private void launchVisibleSplashPotion(Player botPlayer, PotionUsePlan plan) {
        if (botPlayer == null || plan == null)
            return;

        Location eye = botPlayer.getEyeLocation().clone();
        ItemStack visual = plan.stack().clone();

        ThrownPotion thrownPotion = botPlayer.getWorld().spawn(eye, ThrownPotion.class, thrown -> {
            thrown.setShooter(botPlayer);
            thrown.setItem(visual);
            thrown.setGravity(true);
            thrown.setVelocity(new Vector(0.0D, -1.25D, 0.0D));
        });

        pendingSplashPotions.put(thrownPotion.getUniqueId(), new PendingSplashPotion(plan.effects(), plan.healing()));
    }

    private void applyManualPotionPayload(Player botPlayer, List<PotionEffect> effects) {
        if (botPlayer == null || effects == null)
            return;

        for (PotionEffect effect : effects) {
            if (effect == null || effect.getType() == null)
                continue;

            if (!isSelfBuffEffect(effect.getType()))
                continue;
            manualBotEffects.put(effect.getType(),
                    new ManualBotEffect(effect.getAmplifier(), Math.max(1, effect.getDuration()), 0));
        }
    }

    private void tickManualBotEffects(Player botPlayer) {
        if (botPlayer == null)
            return;

        Iterator<Map.Entry<PotionEffectType, ManualBotEffect>> iterator = manualBotEffects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PotionEffectType, ManualBotEffect> entry = iterator.next();
            PotionEffectType type = entry.getKey();
            ManualBotEffect state = entry.getValue();
            int ticksRemaining = state.ticksRemaining() - 1;
            int pulseCounter = state.pulseCounter() + 1;

            if (PotionEffectType.REGENERATION.equals(type)) {
                int interval = Math.max(1, 50 >> Math.min(6, state.amplifier()));
                if (pulseCounter >= interval) {
                    botPlayer.setHealth(Math.min(maxHealth(botPlayer), botPlayer.getHealth() + 1.0D));
                    pulseCounter = 0;
                }
            }

            if (ticksRemaining <= 0) {
                iterator.remove();
                continue;
            }

            entry.setValue(new ManualBotEffect(state.amplifier(), ticksRemaining, pulseCounter));
        }
    }

    private void clearActualBotPotionEffects(Player botPlayer) {
        if (botPlayer == null)
            return;
        for (PotionEffect active : new ArrayList<>(botPlayer.getActivePotionEffects())) {
            botPlayer.removePotionEffect(active.getType());
        }
    }

    private void lookDownForPotion(float yaw) {
        if (npc == null)
            return;
        if (npc.getControl() != null) {
            npc.getControl().setRotation(yaw, 88.0F);
        }
        Player bukkit = npc.getBukkitPlayer();
        if (bukkit != null && bukkit.isValid()) {
            try {
                bukkit.setRotation(yaw, 88.0F);
            } catch (Throwable ignored) {
            }
        }
    }

    private void consumeBotInventoryItem(int slot) {
        ItemStack stack = botInventory.get(slot);
        if (stack == null)
            return;

        if (stack.getAmount() <= 1) {
            botInventory.remove(slot);
            return;
        }

        stack.setAmount(stack.getAmount() - 1);
    }

    private double effectiveMoveSpeed(Player botPlayer) {
        double speed = Math.max(0.08D, settings.getMoveSpeed());

        ManualBotEffect speedEffect = manualBotEffects.get(PotionEffectType.SPEED);
        if (speedEffect != null) {
            speed *= 1.0D + 0.20D * (speedEffect.amplifier() + 1);
        }

        return speed;
    }

    private double manualStrengthBonus() {
        ManualBotEffect effect = manualBotEffects.get(PotionEffectType.STRENGTH);
        if (effect == null)
            return 0.0D;
        return 3.0D * (effect.amplifier() + 1);
    }

    private ItemStack chooseAttackWeapon(boolean targetBlocking) {
        if (targetBlocking && settings.isShieldBreakEnabled()) {
            ItemStack axe = findAxeInBotInventory();
            if (axe != null)
                return axe;
        }
        ItemStack main = botInventory.get(0);
        return main == null ? new ItemStack(Material.AIR) : main.clone();
    }

    private ItemStack findAxeInBotInventory() {
        for (ItemStack stack : botInventory.values()) {
            if (isAxe(stack))
                return stack.clone();
        }
        return null;
    }

    private boolean isAxe(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getType().name().endsWith("_AXE");
    }

    public void handlePlayerDamageEvent(EntityDamageEvent event) {
        if (closing || event.getEntity() != player)
            return;
        if (!fightStarted || betweenRounds) {
            event.setCancelled(true);
            return;
        }
        if (settings.isInvulnerability()) {
            preserveNoDamageCombat(player, event);
        }
    }

    public void handleBotDamageEvent(EntityDamageEvent event) {
        Player botPlayer = npc == null ? null : npc.getBukkitPlayer();
        if (closing || botPlayer == null || event.getEntity() != botPlayer)
            return;
        if (!fightStarted || betweenRounds) {
            event.setCancelled(true);
            return;
        }

        Player attacker = extractAttackingPlayer(event);

        // Only the paired player can damage the bot
        if (attacker != null && !attacker.getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Cancel all non-player damage (void, fall, fire, etc.)
        if (attacker == null) {
            event.setCancelled(true);
            return;
        }

        if (callbacks != null) {
            callbacks.onBotDamaged(this, attacker, event.getFinalDamage());
        }

        // Lock steering so knockback from the hit plays out instead of being fully
        // overridden,
        // but keep the duration short enough that the bot still W-holds back into the
        // trade.
        knockbackFreezeTicks = Math.max(knockbackFreezeTicks, 6);
        if (npc != null) {
            npc.lockSteering(6);

            if (event.getFinalDamage() > 0.0D) {
                Vector repulse = botPlayer.getLocation().toVector().subtract(attacker.getLocation().toVector());
                repulse.setY(0);
                if (repulse.lengthSquared() > 0) {
                    repulse.normalize();

                    double hForce = attacker.isSprinting() ? 0.7D : 0.4D;
                    repulse.multiply(hForce);

                    double yRepulse = botPlayer.isOnGround() ? 0.4D : 0.0D;
                    if (npc.getControl() != null) {
                        npc.getControl().applyKnockback(repulse.getX(), yRepulse, repulse.getZ());
                    }
                }
            }
        }

        if (settings.isInvulnerability()) {
            // Invincibility mode: the bot must never actually lose health.
            // Cancel if it would kill, otherwise let the tiny-damage trick play the
            // hurt animation and restore health next tick.
            double currentHealth = botPlayer.getHealth();
            if (event.getFinalDamage() >= currentHealth) {
                event.setCancelled(true);
                // Still play hurt animation even though cancelled
                try {
                    botPlayer.playHurtAnimation(0.0f);
                } catch (Throwable ignored) {
                }
            } else {
                preserveNoDamageCombat(botPlayer, event);
            }
            return;
        }

        // Normal (non-invincible) mode: prevent actual death to keep the entity clean
        double currentHealth = botPlayer.getHealth();
        if (event.getFinalDamage() >= currentHealth) {
            // Cap to non-lethal so the entity doesn't actually die (prevents item drops,
            // death animation, etc.)
            event.setDamage(Math.max(0.01D, currentHealth - 0.10D));
            plugin.getServer().getScheduler().runTask(plugin, () -> finishRound(true, "killed-bot"));
        }
    }

    private void preserveNoDamageCombat(LivingEntity target, EntityDamageEvent event) {
        double healthBefore = Math.max(0.1D, target.getHealth());
        double tinyDamage = Math.max(0.01D, Math.min(event.getDamage(), healthBefore - 0.1D));
        event.setDamage(tinyDamage);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead())
                return;

            try {
                target.setHealth(Math.min(target.getMaxHealth(), healthBefore));
            } catch (IllegalArgumentException ignored) {
            }

            target.setNoDamageTicks(0);

            try {
                target.playHurtAnimation(0.0f);

                List<Player> viewers = new ArrayList<>(target.getWorld().getPlayers());
                if (target instanceof Player self) {
                    viewers.removeIf(p -> p.getUniqueId().equals(self.getUniqueId()));
                    self.sendHurtAnimation(0.0f);
                }

                target.broadcastHurtAnimation(viewers);
            } catch (Throwable ignored) {
            }
        });
    }

    private Player extractAttackingPlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity))
            return null;
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player p)
            return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
            return shooter;
        return null;
    }

    public void handlePlayerDeath() {
        if (closing || betweenRounds)
            return;
        finishRound(false, "player-death");
    }

    public void handlePlayerLeave() {
        if (closing)
            return;
        debug("handlePlayerLeave triggered. player=" + formatLocation(player.getLocation()) + ", spawn="
                + formatLocation(playerSpawn));
        forceClose(false, "left-duel-world");
    }

    private void finishRound(boolean playerWonRound, String reason) {
        if (closing || betweenRounds)
            return;
        betweenRounds = true;
        fightStarted = false;
        cancelTask(brainTask);

        if (playerWonRound)
            playerRoundsWon++;
        else
            botRoundsWon++;

        player.sendTitle(
                playerWonRound ? Messages.text(player, "messages.bot-session.round-won") : Messages.text(player, "messages.bot-session.round-lost"),
                Messages.text(player, "messages.bot-session.score", playerRoundsWon, botRoundsWon),
                0,
                25,
                8);

        if (playerRoundsWon >= settings.getRounds()) {
            if (callbacks != null)
                callbacks.onPlayerWin(this);
            player.sendTitle(Messages.text(player, "messages.bot-session.you-won"), Messages.text(player, "messages.bot-session.final-score", playerRoundsWon, botRoundsWon), 0, 30,
                    10);
            player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            forceClose(true, reason);
            return;
        }

        if (botRoundsWon >= settings.getRounds()) {
            if (callbacks != null)
                callbacks.onPlayerLoss(this);
            player.sendTitle(Messages.text(player, "messages.bot-session.you-lost"), Messages.text(player, "messages.bot-session.final-score", playerRoundsWon, botRoundsWon), 0, 30,
                    10);
            player.playSound(player, Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);
            forceClose(false, reason);
            return;
        }

        currentRound++;
        if (usingPolarArena()) {
            nextPolarReservation = DuelManager.polarArenaManager.reserveArena(arena);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::prepareNextRound, 20L);
    }

    private void prepareNextRound() {
        refreshArenaIfNeeded();
        destroyNpc();
        switchToNextPolarReservation();
        manualBotEffects.clear();
        pendingSplashPotions.clear();
        emergencyHealSplashesRemaining = 0;
        potionUseSequence = null;

        plugin.resetPlayer(player);
        armWorldGuardGrace("next-round-teleport");
        player.teleport(playerSpawn);
        giveKit(player, kit);
        applyEffects(player, kit);
        refreshPlayerHealthVisual();
        rebuildBotInventory();

        if (!spawnNpc()) {
            forceClose(false, "npc-round-respawn-failed");
            return;
        }
        startCountdown();
    }

    private void armWorldGuardGrace(String reason) {
        this.worldGuardGraceUntilMillis = System.currentTimeMillis() + 2500L;
        debug("World guard grace enabled for " + reason + " until=" + worldGuardGraceUntilMillis);
    }

    public boolean shouldIgnoreWorldGuard(Location location) {
        if (closing)
            return true;
        if (location == null || playerSpawn == null || playerSpawn.getWorld() == null)
            return false;
        if (System.currentTimeMillis() <= worldGuardGraceUntilMillis) {
            debug("Ignoring world safeguard during grace window for location=" + formatLocation(location));
            return true;
        }
        return playerSpawn.getWorld().equals(location.getWorld());
    }

    private void debug(String message) {
        if (!settings.isDebugLogging())
            return;
        plugin.getLogger().warning("[AutomatedCombat][DEBUG][" + player.getName() + "] " + message);
    }

    private String formatLocation(Location location) {
        if (location == null)
            return "null";
        return (location.getWorld() == null ? "null" : location.getWorld().getName())
                + " (" + String.format(Locale.ROOT, "%.2f", location.getX())
                + ", " + String.format(Locale.ROOT, "%.2f", location.getY())
                + ", " + String.format(Locale.ROOT, "%.2f", location.getZ()) + ")";
    }

    public void forceClose(boolean playerWon, String reason) {
        if (closing)
            return;
        debug("forceClose invoked. reason=" + reason + ", playerWon=" + playerWon);
        closing = true;
        fightStarted = false;
        betweenRounds = false;

        cancelTask(countdownTask);
        cancelTask(brainTask);
        destroyNpc();

        if (player.isOnline()) {
            plugin.resetPlayer(player);
            plugin.teleportToLobby(player);
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep != null)
            ep.setPlayerDuelInstance(null);

        refreshArenaIfNeeded();
        freeArena();
        manager.remove(this);

        if (callbacks != null)
            callbacks.onSessionClosed(this);
    }

    void stopOnDisable() {
        // Paper is stopping, so queued arena resets and delayed teleports cannot run here.
        closing = true;
        cancelTask(countdownTask);
        cancelTask(brainTask);
        destroyNpc();
    }

    private void rebuildBotInventory() {
        botInventory.clear();
        if (kit == null || kit.getInventory() == null)
            return;
        for (Map.Entry<Integer, SerializableItem> entry : kit.getInventory().entrySet()) {
            if (entry.getValue() == null)
                continue;
            ItemStack item = entry.getValue().convertToItem();
            if (item == null || item.getType().isAir())
                continue;
            botInventory.put(entry.getKey(), item.clone());
        }
    }

    private void refreshArenaIfNeeded() {
        if (motherLocation == null || arena == null)
            return;
        if (usingPolarArena())
            return;
        DuelManager.schemManager.enqueueSchematic(motherLocation, getSchematicFile(arena), true);
    }

    private void destroyNpc() {
        try {
            if (npc != null)
                npc.destroy();
        } catch (Throwable ignored) {
        }
        pendingSplashPotions.clear();
        emergencyHealSplashesRemaining = 0;
        potionUseSequence = null;
        npc = null;
    }

    private boolean spawnNpc() {
        try {
            debug("Spawning bot at " + formatLocation(botSpawn));
            npc = new FakePlayerNPC(plugin, player, settings.getBotNameFor(player.getName()), botSpawn.clone(), kit,
                    settings.isDebugLogging());
            npc.spawn(player);
            if (npc.getBukkitPlayer() != null) {
                seedManualBotEffectsFromKit(kit);
                clearActualBotPotionEffects(npc.getBukkitPlayer());
            }
            debug("Bot spawn finished. handle=" + (npc.getNpc() != null) + ", bukkit=" + (npc.getBukkitPlayer() != null)
                    + ", entityId=" + npc.getEntityId());
            if (callbacks != null)
                callbacks.onNpcSpawned(this, npc);
            return true;
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[AutomatedCombat] NPC spawn failed. Check compatibility with this Paper build.", ex);
            destroyNpc();
            return false;
        }
    }

    private boolean validateRuntimeState() {
        if (closing)
            return false;
        if (!player.isOnline() || player.isDead()) {
            forceClose(false, "player-offline-or-dead");
            return false;
        }
        if (playerSpawn == null || playerSpawn.getWorld() == null) {
            forceClose(false, "missing-player-spawn");
            return false;
        }
        if (!playerSpawn.getWorld().equals(player.getWorld())) {
            debug("Runtime validation wrong world. player=" + formatLocation(player.getLocation()) + ", spawn="
                    + formatLocation(playerSpawn));
            forceClose(false, "wrong-world");
            return false;
        }
        if (player.getLocation().distanceSquared(playerSpawn) > 144000.0D) {
            debug("Runtime validation left arena bounds. player=" + formatLocation(player.getLocation()) + ", spawn="
                    + formatLocation(playerSpawn));
            forceClose(false, "left-arena-bounds");
            return false;
        }
        if (npc == null || !npc.isSpawnedServerSide()) {
            debug("Runtime validation failed: npc missing or not spawned server-side.");
            forceClose(false, "npc-desynced");
            return false;
        }
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || ep.getPlayerDuelInstance() == null
                || ep.getPlayerDuelInstance().getDuelInstance() != duelAdapter) {
            forceClose(false, "duel-instance-detached");
            return false;
        }
        return true;
    }

    private void giveKit(Player player, Kit kit) {
        plugin.resetPlayer(player);

        if (kit != null)
            plugin.applyPossibleKit(player, kit);
    }

    private void applyEffects(Player p, Kit kit) {
        if (kit == null)
            return;
        Object raw = kit.getMetaData().get("effects");
        if (!(raw instanceof Iterable<?> iterable))
            return;
        for (Object object : iterable) {
            if (!(object instanceof org.albi.core.data.EffectMeta meta))
                continue;
            try {
                if (meta.getEffect() == null)
                    continue;
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        meta.getEffect(),
                        meta.getDuration() * 20,
                        meta.getAmplifier(),
                        meta.isAmbient(),
                        true,
                        true));
            } catch (Exception ignored) {
            }
        }
    }

    private void seedManualBotEffectsFromKit(Kit kit) {
        if (kit == null)
            return;
        Object raw = kit.getMetaData().get("effects");
        if (!(raw instanceof Iterable<?> iterable))
            return;

        for (Object object : iterable) {
            if (!(object instanceof org.albi.core.data.EffectMeta meta))
                continue;
            if (meta.getEffect() == null)
                continue;

            PotionEffect effect = new PotionEffect(
                    meta.getEffect(),
                    Math.max(1, meta.getDuration() * 20),
                    meta.getAmplifier(),
                    meta.isAmbient(),
                    true,
                    true);

            if (isSelfBuffEffect(effect.getType())) {
                manualBotEffects.put(effect.getType(),
                        new ManualBotEffect(effect.getAmplifier(), effect.getDuration(), 0));
            }
        }
    }

    public boolean ownsPendingSplashPotion(UUID uuid) {
        return uuid != null && pendingSplashPotions.containsKey(uuid);
    }

    public void handlePotionSplash(PotionSplashEvent event) {
        if (event == null || event.getPotion() == null || npc == null)
            return;

        PendingSplashPotion pending = pendingSplashPotions.remove(event.getPotion().getUniqueId());
        if (pending == null)
            return;

        Player botPlayer = npc.getBukkitPlayer();
        if (botPlayer != null && botPlayer.isValid()) {
            for (LivingEntity entity : event.getAffectedEntities()) {
                boolean isBotEntity = entity.getUniqueId().equals(botPlayer.getUniqueId());
                if (!isBotEntity || !pending.allowVanillaOnBot()) {
                    event.setIntensity(entity, 0.0D);
                }
            }

            applyManualPotionPayload(botPlayer, pending.effects());
            plugin.getServer().getScheduler().runTask(plugin, () -> clearActualBotPotionEffects(botPlayer));
        }
    }

    public void handleLingeringPotionSplash(LingeringPotionSplashEvent event) {
        if (event == null || event.getEntity() == null || npc == null)
            return;

        PendingSplashPotion pending = pendingSplashPotions.remove(event.getEntity().getUniqueId());
        if (pending == null)
            return;

        suppressLingeringCloud(event.getAreaEffectCloud(), event);

        Player botPlayer = npc.getBukkitPlayer();
        if (botPlayer != null && botPlayer.isValid()) {
            applyManualPotionPayload(botPlayer, pending.effects());
            plugin.getServer().getScheduler().runTask(plugin, () -> clearActualBotPotionEffects(botPlayer));
        }
    }

    private void suppressLingeringCloud(AreaEffectCloud cloud, LingeringPotionSplashEvent event) {
        if (cloud == null)
            return;

        try {
            cloud.clearCustomEffects();
        } catch (Throwable ignored) {
        }
        try {
            cloud.setBasePotionType(null);
        } catch (Throwable ignored) {
        }
        try {
            cloud.setDuration(0);
            cloud.setWaitTime(0);
            cloud.setRadius(0.0F);
            cloud.setRadiusOnUse(0.0F);
            cloud.setRadiusPerTick(0.0F);
        } catch (Throwable ignored) {
        }
        try {
            event.allowsEmptyCreation(false);
        } catch (Throwable ignored) {
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (cloud.isValid()) {
                cloud.remove();
            }
        });
    }

    private void refreshPlayerHealthVisual() {
        if (!player.isOnline() || player.getGameMode() == GameMode.SPECTATOR)
            return;
        try {
            player.setHealth(Math.min(maxHealth(player), Math.max(0.1D, player.getHealth())));
        } catch (Exception ignored) {
        }
    }

    private double resolveBaseDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return 1.0D;
        String name = item.getType().name();
        if (name.endsWith("_SWORD")) {
            if (name.startsWith("WOODEN") || name.startsWith("GOLDEN"))
                return 4.0D;
            if (name.startsWith("STONE"))
                return 5.0D;
            if (name.startsWith("IRON"))
                return 6.0D;
            if (name.startsWith("DIAMOND"))
                return 7.0D;
            if (name.startsWith("NETHERITE"))
                return 8.0D;
        }
        if (name.endsWith("_AXE")) {
            if (name.startsWith("WOODEN") || name.startsWith("GOLDEN"))
                return 7.0D;
            if (name.startsWith("STONE"))
                return 9.0D;
            if (name.startsWith("IRON"))
                return 9.0D;
            if (name.startsWith("DIAMOND"))
                return 9.0D;
            if (name.startsWith("NETHERITE"))
                return 10.0D;
        }
        return 1.0D;
    }

    private boolean roll(int percent) {
        if (percent <= 0)
            return false;
        if (percent >= 100)
            return true;
        return random.nextInt(100) < percent;
    }

    private double maxHealth(LivingEntity entity) {
        if (entity == null || entity.getAttribute(Attribute.MAX_HEALTH) == null)
            return 20.0D;
        return entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    public boolean ownsNpc(UUID uuid) {
        return npc != null && npc.ownsUuid(uuid);
    }

    private Location absolute(Arena arena, Location base, double[] rel) {
        World world = base == null ? null : base.getWorld();
        if (usingPolarArena()) {
            return new Location(world, rel[0], rel[1], rel[2], (float) rel[3], (float) rel[4]);
        }
        return new Location(
                world,
                base.getX() + rel[0],
                base.getY() + rel[1],
                base.getZ() + rel[2],
                (float) (base.getYaw() + rel[3]),
                (float) (base.getPitch() + rel[4]));
    }

    private File getSchematicFile(Arena arena) {
        return new File(plugin.getDataFolder(),
                "schems" + File.separator + "duels" + File.separator + arena.getSchematic());
    }

    private Location reserveArena(Arena arena) {
        World world = plugin.getServer().getWorld(arena.getName() + "-duel");
        if (world == null) {
            plugin.getLogger().warning("[AutomatedCombat] Missing duel world for arena " + arena.getName());
            return null;
        }

        if (!org.albi.core.modules.Duels.helpers.GlobalDuelCache.availableArenas.containsKey(arena)) {
            org.albi.core.modules.Duels.helpers.GlobalDuelCache.availableArenas.put(arena, new ArrayList<>());
            org.albi.core.modules.Duels.helpers.GlobalDuelCache.usedArenas.put(arena, new ArrayList<>());
        }

        ArrayList<Location> available = org.albi.core.modules.Duels.helpers.GlobalDuelCache.availableArenas.get(arena);
        ArrayList<Location> used = org.albi.core.modules.Duels.helpers.GlobalDuelCache.usedArenas.get(arena);

        if (available.isEmpty()) {
            int maxArenas = arena.getMaxArenas();
            int zOffset = arena.getOffsetZAxis();
            int xOffset = arena.getOffsetXAxis();
            int maxPerLine = arena.getMaxPerLine();
            Location base = new Location(world, 0, 100, 0);
            for (int i = 0; i < maxArenas; i++) {
                int row = i / maxPerLine;
                int col = i % maxPerLine;
                available.add(base.clone().add(row * xOffset, 0, col * zOffset));
            }
        }

        for (Location candidate : available) {
            if (!used.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
        return null;
    }

    private void freeArena() {
        if (arena == null || motherLocation == null)
            return;
        if (polarReservation != null || nextPolarReservation != null || arena.isPolarArena()) {
            if (polarReservation != null) {
                DuelManager.polarArenaManager.releaseArena(polarReservation, true);
                polarReservation = null;
            }
            if (nextPolarReservation != null) {
                DuelManager.polarArenaManager.releaseArena(nextPolarReservation, true);
                nextPolarReservation = null;
            }
            return;
        }
        List<Location> used = org.albi.core.modules.Duels.helpers.GlobalDuelCache.usedArenas.get(arena);
        List<Location> available = org.albi.core.modules.Duels.helpers.GlobalDuelCache.availableArenas.get(arena);
        if (used != null)
            used.remove(motherLocation);
        if (available != null && !available.contains(motherLocation))
            available.add(motherLocation);
    }

    private boolean usingPolarArena() {
        return DuelManager.polarArenaManager != null && DuelManager.polarArenaManager.isPolarArena(arena);
    }

    private void switchToNextPolarReservation() {
        if (!usingPolarArena() || nextPolarReservation == null)
            return;
        PolarArenaReservation old = polarReservation;
        polarReservation = nextPolarReservation;
        nextPolarReservation = null;
        motherLocation = polarReservation.getMotherLocation();
        playerSpawn = absolute(arena, motherLocation, arena.getSpawn1());
        botSpawn = absolute(arena, motherLocation, arena.getSpawn2());
        if (old != null) {
            DuelManager.polarArenaManager.releaseArena(old, true);
        }
    }

    private void cancelTask(BukkitRunnable task) {
        if (task != null)
            task.cancel();
    }

    public Player getPlayer() {
        return player;
    }

    public BotSettings getSettings() {
        return settings;
    }

    public Arena getArena() {
        return arena;
    }

    public Kit getKit() {
        return kit;
    }

    public Location getBotLocation() {
        return npc == null || npc.getLocation() == null ? botSpawn : npc.getLocation();
    }

    public Location getPlayerSpawn() {
        return playerSpawn;
    }

    public Location getBotSpawn() {
        return botSpawn;
    }

    public double getBotHealth() {
        return npc == null || npc.getBukkitPlayer() == null ? 0.0D : npc.getBukkitPlayer().getHealth();
    }

    public double getPlayerHealth() {
        return player.getHealth();
    }

    public int getPlayerRoundsWon() {
        return playerRoundsWon;
    }

    public int getBotRoundsWon() {
        return botRoundsWon;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setPlayerHealth(double playerHealth) {
        try {
            player.setHealth(Math.max(0.1D, Math.min(maxHealth(player), playerHealth)));
        } catch (Exception ignored) {
        }
    }

    public FakePlayerNPC getNpc() {
        return npc;
    }

    public boolean isFightStarted() {
        return fightStarted;
    }

    public boolean isControllerEnabled() {
        return controllerEnabled;
    }

    public void setControllerEnabled(boolean controllerEnabled) {
        this.controllerEnabled = controllerEnabled;
    }

    public void moveBot(Vector delta) {
        if (delta == null || npc == null)
            return;
        Location next = getBotLocation().clone().add(delta);
        next.setYaw(getBotLocation().getYaw());
        next.setPitch(getBotLocation().getPitch());
        npc.move(player, next, Math.max(0.08D, settings.getMoveSpeed()));
    }

    public void teleportBot(Location location) {
        if (location == null || npc == null)
            return;
        npc.teleport(player, location);
    }

    public void faceTarget() {
        if (npc != null)
            npc.lookAt(player);
    }

    public void setRetreatTicks(int retreatTicks) {
        this.retreatTicks = Math.max(0, retreatTicks);
    }

    public void setAttackCooldownTicks(int attackCooldown) {
        this.attackCooldown = Math.max(0, attackCooldown);
    }

    public void setReactionWaitTicks(int reactionWait) {
        this.reactionWait = Math.max(0, reactionWait);
    }

    private record PotionUsePlan(
            int slot,
            ItemStack stack,
            List<PotionEffect> effects,
            boolean healing,
            String reason) {
    }

    private record PendingSplashPotion(List<PotionEffect> effects, boolean allowVanillaOnBot) {
    }

    private record ManualBotEffect(int amplifier, int ticksRemaining, int pulseCounter) {
    }

    private static final class PotionUseSequence {
        private final PotionUsePlan plan;
        private int ticksRemaining;
        private final float lookYaw;

        private PotionUseSequence(PotionUsePlan plan, int ticksRemaining, float lookYaw) {
            this.plan = plan;
            this.ticksRemaining = ticksRemaining;
            this.lookYaw = lookYaw;
        }

        private PotionUsePlan plan() {
            return plan;
        }

        private int ticksRemaining() {
            return ticksRemaining;
        }

        private void setTicksRemaining(int ticksRemaining) {
            this.ticksRemaining = ticksRemaining;
        }

        private float lookYaw() {
            return lookYaw;
        }
    }
}
