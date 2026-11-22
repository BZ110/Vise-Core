package org.albi.core.modules.Duels.AutomatedCombat;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import org.albi.core.Core;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.albi.mods.Reflect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Experimental practice opponent backed by Paper's version-specific ServerPlayer and packets.
 * Keep this opt-in. Before upgrading Paper, test spawning, combat, and session cleanup on a
 * staging server. Changes to server internals can break NPCs without any Bukkit API change.
 * All entity and connection operations here belong on the primary server thread.
 */
public final class FakePlayerNPC {

    private static final Class<?> C_SERVER_PLAYER = Reflect.resolveClass("net.minecraft.server.level.ServerPlayer");
    private static final Class<?> C_SERVER_LEVEL = Reflect.resolveClass("net.minecraft.server.level.ServerLevel");
    private static final Class<?> C_MINECRAFT_SERVER = Reflect.resolveClass("net.minecraft.server.MinecraftServer");
    private static final Class<?> C_CLIENT_INFORMATION = Reflect
            .resolveClass("net.minecraft.server.level.ClientInformation");
    private static final Class<?> C_CONNECTION = Reflect.resolveClass("net.minecraft.network.Connection");
    private static final Class<?> C_PACKET_FLOW = Reflect.resolveClass("net.minecraft.network.protocol.PacketFlow");
    private static final Class<?> C_COMMON_LISTENER_COOKIE = Reflect
            .resolveClass("net.minecraft.server.network.CommonListenerCookie");
    private static final Class<?> C_SERVER_GAME_PACKET_LISTENER = Reflect
            .resolveClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
    private static final Class<?> C_SERVER_ENTITY = Reflect.resolveClass("net.minecraft.server.level.ServerEntity");

    private static final Constructor<?> CTOR_SERVER_PLAYER = Reflect.resolveConstructor(
            C_SERVER_PLAYER,
            C_MINECRAFT_SERVER,
            C_SERVER_LEVEL,
            GameProfile.class,
            C_CLIENT_INFORMATION);
    private static final Constructor<?> CTOR_CONNECTION = Reflect.resolveConstructor(C_CONNECTION, C_PACKET_FLOW);
    private static final Constructor<?> CTOR_SERVER_GAME_PACKET_LISTENER = Reflect.resolveConstructor(
            C_SERVER_GAME_PACKET_LISTENER,
            C_MINECRAFT_SERVER,
            C_CONNECTION,
            C_SERVER_PLAYER,
            C_COMMON_LISTENER_COOKIE);
    private static final Constructor<?> CTOR_SERVER_ENTITY = resolveServerEntityConstructor();

    private static final Method M_CLIENT_INFORMATION_CREATE_DEFAULT = Reflect.resolveMethod(C_CLIENT_INFORMATION,
            new String[] { "createDefault" });
    private static final Method M_COMMON_LISTENER_COOKIE_CREATE_INITIAL = Reflect.resolveMethod(
            C_COMMON_LISTENER_COOKIE,
            new String[] { "createInitial" },
            GameProfile.class,
            boolean.class);
    private static final Method M_ADD_NEW_PLAYER = Reflect.resolveMethod(C_SERVER_LEVEL,
            new String[] { "addNewPlayer" }, C_SERVER_PLAYER);
    private static final Method M_SEND_PAIRING_DATA = resolveSendPairingData();
    private static final Field F_CONNECTION = Reflect.resolveField(C_SERVER_PLAYER, "connection", "g", "f_8906_");

    private final Core plugin;
    private final Player skinSource;
    private final String name;
    private final Kit kit;
    private final boolean debugLogging;

    private final Map<org.bukkit.inventory.EquipmentSlot, ItemStack> equipment = new HashMap<>();

    private Object handle;
    private ControlNPC control;
    private Location spawnLocation;
    private UUID uuid;

    public FakePlayerNPC(Core plugin, Player skinSource, String name, Location location, Kit kit,
            boolean debugLogging) {
        this.plugin = plugin;
        this.skinSource = skinSource;
        this.name = name == null ? "Bot" : name;
        this.spawnLocation = location == null ? null : location.clone();
        this.kit = kit;
        this.debugLogging = debugLogging;
        this.uuid = UUID.randomUUID();
        resetKitEquipment();
    }

    public void spawn(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            throw new IllegalStateException("Viewer must be online to spawn bot.");
        }
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            throw new IllegalStateException("Bot spawn location is missing.");
        }
        if (handle != null && isSpawnedServerSide()) {
            debug("Spawn called while bot already exists, skipping.");
            return;
        }

        CraftServer craftServer = (CraftServer) plugin.getServer();
        CraftWorld craftWorld = (CraftWorld) spawnLocation.getWorld();
        Object server = craftServer.getServer();
        Object level = craftWorld.getHandle();

        GameProfile profile = new GameProfile(uuid, trimName(name));
        debug("Skipping skin copy for stability.");

        Object clientInformation = Reflect.invoke(null, M_CLIENT_INFORMATION_CREATE_DEFAULT);
        handle = Reflect.newInstance(CTOR_SERVER_PLAYER, server, level, profile, clientInformation);
        control = new ControlNPC(handle, this);

        configureDummyConnection(server, profile);
        control.setPosition(spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ());
        control.setRotation(spawnLocation.getYaw(), spawnLocation.getPitch());
        control.initState();

        primeViewer(viewer);
        Reflect.invoke(level, M_ADD_NEW_PLAYER, handle);

        this.uuid = getBukkitPlayer() == null ? uuid : getBukkitPlayer().getUniqueId();

        for (Player worldViewer : getWorldViewers()) {
            primeViewer(worldViewer);
            showToViewer(worldViewer);
        }
        schedulePostSpawnViewerSync();

        Player bukkit = getBukkitPlayer();
        if (bukkit != null) {
            try {
                bukkit.setSilent(false);
                bukkit.setCollidable(true);
                bukkit.setCanPickupItems(false);
                bukkit.setPersistent(false);
                bukkit.setHealth(bukkit.getMaxHealth());
                bukkit.setFoodLevel(20);
                bukkit.setSaturation(20.0f);
                bukkit.setCustomNameVisible(false);
                bukkit.setAllowFlight(false);
                bukkit.setFlying(false);
                bukkit.setNoDamageTicks(0);
                bukkit.setFreezeTicks(0);
                bukkit.setFireTicks(0);
            } catch (Throwable t) {
                debug("Failed to post-configure Bukkit entity: " + t.getClass().getSimpleName() + ": "
                        + t.getMessage());
            }
        }

        syncEquipment();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (handle == null || !viewer.isOnline())
                return;
            lookAt(viewer);
            debug("Spawn complete. entityId=" + getEntityId() + ", uuid=" + uuid + ", viewer=" + viewer.getName());
        }, 1L);
    }

    private static Constructor<?> resolveServerEntityConstructor() {
        Class<?> entityClass = Reflect.resolveClass("net.minecraft.world.entity.Entity");
        Class<?> synchronizerClass = Reflect.resolveClass("net.minecraft.server.level.ServerEntity$Synchronizer");

        // Exact 1.21.11 match first
        Constructor<?> exact = Reflect.resolveConstructor(
                C_SERVER_ENTITY,
                C_SERVER_LEVEL,
                entityClass,
                int.class,
                boolean.class,
                synchronizerClass,
                java.util.Set.class);
        if (exact != null) {
            exact.setAccessible(true);
            return exact;
        }

        // Fallback structural match
        for (Constructor<?> constructor : C_SERVER_ENTITY.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 6)
                continue;

            boolean p0 = params[0].isAssignableFrom(C_SERVER_LEVEL) || C_SERVER_LEVEL.isAssignableFrom(params[0]);
            boolean p1 = params[1].isAssignableFrom(entityClass) || entityClass.isAssignableFrom(params[1]);
            boolean p2 = params[2] == int.class;
            boolean p3 = params[3] == boolean.class;
            boolean p4 = params[4].isInterface()
                    && params[4].getEnclosingClass() != null
                    && params[4].getEnclosingClass().equals(C_SERVER_ENTITY);
            boolean p5 = java.util.Set.class.isAssignableFrom(params[5]);

            if (p0 && p1 && p2 && p3 && p4 && p5) {
                constructor.setAccessible(true);
                return constructor;
            }
        }

        for (Constructor<?> constructor : C_SERVER_ENTITY.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 6)
                continue;

            boolean p0 = params[0].isAssignableFrom(C_SERVER_LEVEL) || C_SERVER_LEVEL.isAssignableFrom(params[0]);
            boolean p1 = params[1].isAssignableFrom(entityClass) || entityClass.isAssignableFrom(params[1]);
            boolean p2 = params[2] == int.class;
            boolean p3 = params[3] == boolean.class;
            boolean p4 = params[4].isInterface()
                    && params[4].getEnclosingClass() != null
                    && params[4].getEnclosingClass().equals(C_SERVER_ENTITY);
            boolean p5 = java.util.Set.class.isAssignableFrom(params[5]);

            if (p0 && p1 && p2 && p3 && p4 && p5) {
                constructor.setAccessible(true);
                return constructor;
            }
        }

        return null;
    }

    private static Method resolveSendPairingData() {
        for (Method method : C_SERVER_ENTITY.getMethods()) {
            if (method.getName().equals("sendPairingData") && method.getParameterCount() == 2) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : C_SERVER_ENTITY.getDeclaredMethods()) {
            if (method.getName().equals("sendPairingData") && method.getParameterCount() == 2) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private void schedulePostSpawnViewerSync() {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (handle == null)
                return;
            resendEquipmentToViewers();
            List<UUID> uuids = List.of(uuid);
            for (Player viewer : getWorldViewers()) {
                try {
                    ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundPlayerInfoRemovePacket(uuids));
                } catch (Throwable t) {
                    debug("Failed to remove bot from tab for " + viewer.getName() + ": "
                            + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }, 20L);
    }

    public void destroy() {
        try {
            hideFromWorldViewers();
        } catch (Throwable t) {
            debug("Failed to hide bot from viewers: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        Player bukkit = getBukkitPlayer();
        if (bukkit != null && bukkit.isValid()) {
            try {
                bukkit.remove();
            } catch (Throwable ignored) {
            }
        } else if (control != null) {
            try {
                control.removeDiscarded();
            } catch (Throwable ignored) {
            }
        }
        handle = null;
        control = null;
    }

    public void move(Player viewer, Location next, double walkSpeed) {
        if (control == null || next == null)
            return;

        Player bukkit = getBukkitPlayer();
        if (bukkit == null || !bukkit.isValid())
            return;

        Location current = bukkit.getLocation();
        if (current.getWorld() == null || next.getWorld() == null)
            return;
        if (current.getWorld() != next.getWorld())
            return;

        Vector delta = next.toVector().subtract(current.toVector());
        delta.setY(0.0D);

        double distance = delta.length();
        Vector desiredHorizontal = new Vector();
        if (distance > 0.0001D) {
            desiredHorizontal = delta.normalize().multiply(Math.min(distance, Math.max(0.08D, walkSpeed)));
        }

        control.setRotation(next.getYaw(), next.getPitch());
        control.steerHorizontal(
                desiredHorizontal.getX(),
                desiredHorizontal.getZ(),
                0.35D,
                bukkit.isSprinting(),
                next.getYaw());

        Location live = bukkit.getLocation().clone();
        live.setYaw(next.getYaw());
        live.setPitch(next.getPitch());

        // Safety snap if the fake player gets visually stuck.
        if (live.distanceSquared(next) > (3.5D * 3.5D)) {
            control.setPosition(next.getX(), next.getY(), next.getZ());
            control.resetMotion();
            live = next.clone();
            debug("Movement desync snap. before=" + current + ", live=" + bukkit.getLocation() + ", target=" + next);
        }

        this.spawnLocation = live;
    }

    public void teleport(Player viewer, Location next) {
        if (control == null || next == null)
            return;
        control.setPosition(next.getX(), next.getY(), next.getZ());
        control.setRotation(next.getYaw(), next.getPitch());
        this.spawnLocation = next.clone();
    }

    public void lookAt(Player viewer) {
        if (viewer == null)
            return;
        lookAt(viewer.getEyeLocation());
    }

    public void lookAt(Location target) {
        Player bukkit = getBukkitPlayer();
        if (bukkit == null || !bukkit.isValid() || target == null || control == null)
            return;

        Location from = bukkit.getEyeLocation();
        float yaw = BotMath.yawTo(from, target);
        float pitch = BotMath.pitchTo(from, target);

        try {
            bukkit.setRotation(yaw, pitch);
        } catch (Throwable ignored) {
        }

        control.setRotation(yaw, pitch);

        Location current = bukkit.getLocation().clone();
        current.setYaw(yaw);
        current.setPitch(pitch);
        this.spawnLocation = current;
    }

    public void swing(Player viewer) {
        Player bukkit = getBukkitPlayer();
        if (bukkit == null || !bukkit.isValid() || control == null)
            return;

        control.swingMainHand();
        try {
            bukkit.swingMainHand();
        } catch (Throwable ignored) {
        }
    }

    public void attack(Player target) {
        Player bukkit = getBukkitPlayer();
        if (bukkit == null || target == null || !bukkit.isValid() || !target.isValid() || control == null)
            return;

        control.swingMainHand();
        try {
            bukkit.swingMainHand();
        } catch (Throwable ignored) {
        }

        control.attack(control.getNmsEntity(target));
    }

    public void setBlocking(boolean blocking) {
        if (control == null)
            return;
        if (!hasShieldOffHand())
            return;
        control.setBlocking(blocking);
    }

    public boolean isBlocking() {
        Player bukkit = getBukkitPlayer();
        return bukkit != null && bukkit.isValid() && bukkit.isBlocking();
    }

    public void setSprinting(boolean sprinting) {
        if (control != null) {
            control.setSprinting(sprinting);
        }

        Player bukkit = getBukkitPlayer();
        if (bukkit != null && bukkit.isValid()) {
            bukkit.setSprinting(sprinting);
        }
    }

    public void lockSteering(int ticks) {
        if (control != null) {
            control.lockSteering(ticks);
        }
    }

    public boolean isSteeringLocked() {
        return control != null && control.isSteeringLocked();
    }

    public void resetMotion() {
        if (control != null) {
            control.resetMotion();
        }
    }

    public Player getBukkitPlayer() {
        return control == null ? null : control.getBukkitPlayer();
    }

    public LivingEntity getBukkitLiving() {
        Player bukkit = getBukkitPlayer();
        return bukkit == null ? null : bukkit;
    }

    public ControlNPC getControl() {
        return control;
    }

    public boolean isSpawnedServerSide() {
        return handle != null && getBukkitPlayer() != null;
    }

    public Location getLocation() {
        Player bukkit = getBukkitPlayer();
        if (bukkit != null && bukkit.isValid()) {
            return bukkit.getLocation().clone();
        }
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void sendVelocitySync(double x, double y, double z) {
        if (handle == null)
            return;
        try {
            net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket packet = new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
                    getEntityId(), new net.minecraft.world.phys.Vec3(x, y, z));
            for (Player viewer : getWorldViewers()) {
                ((CraftPlayer) viewer).getHandle().connection.send(packet);
            }
        } catch (Throwable t) {
            debug("Failed to send velocity sync: " + t.getMessage());
        }
    }

    public void applyNmsDamageTo(Player targetWrapper, double baseDamage) {
        try {
            net.minecraft.server.level.ServerPlayer nmsTarget = ((CraftPlayer) targetWrapper).getHandle();
            net.minecraft.world.damagesource.DamageSources damageSources = nmsTarget.level().damageSources();
            net.minecraft.world.damagesource.DamageSource source = damageSources
                    .playerAttack((net.minecraft.server.level.ServerPlayer) handle);
            nmsTarget.hurt(source, (float) baseDamage);
            debug("Applied exact NMS damage=" + baseDamage + " to " + targetWrapper.getName());
        } catch (Throwable t) {
            debug("Failed to apply NMS damage: " + t.getMessage());
        }
    }

    public boolean ownsUuid(UUID other) {
        return other != null && uuid != null && uuid.equals(other);
    }

    public UUID getUuid() {
        return uuid;
    }

    public Object getNpc() {
        return handle;
    }

    public int getEntityId() {
        return control == null ? -1 : control.getEntityId();
    }

    public boolean isAlive() {
        Player bukkit = getBukkitPlayer();
        return bukkit != null && bukkit.isValid() && !bukkit.isDead();
    }

    public double getHealth() {
        Player bukkit = getBukkitPlayer();
        return bukkit == null ? 0.0D : bukkit.getHealth();
    }

    public void setHealth(double health) {
        Player bukkit = getBukkitPlayer();
        if (bukkit == null || !bukkit.isValid())
            return;
        double clamped = Math.max(0.1D, Math.min(bukkit.getMaxHealth(), health));
        bukkit.setHealth(clamped);
    }

    public void setMainHand(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            equipment.remove(org.bukkit.inventory.EquipmentSlot.HAND);
        else
            equipment.put(org.bukkit.inventory.EquipmentSlot.HAND, stack.clone());
        syncEquipment();
    }

    public ItemStack getMainHand() {
        ItemStack stack = equipment.get(org.bukkit.inventory.EquipmentSlot.HAND);
        return stack == null ? null : stack.clone();
    }

    public ItemStack getOffHand() {
        ItemStack stack = equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        return stack == null ? null : stack.clone();
    }

    public boolean hasShieldOffHand() {
        ItemStack offHand = equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        return offHand != null && offHand.getType() == Material.SHIELD;
    }

    public void setOffHand(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            equipment.remove(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        else
            equipment.put(org.bukkit.inventory.EquipmentSlot.OFF_HAND, stack.clone());
        syncEquipment();
    }

    public void resetKitEquipment() {
        equipment.clear();
        ItemStack main = itemFromKit(0);
        if (main != null && !main.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.HAND, main);
        ItemStack off = itemFromSerializable(() -> kit.getOffhand());
        if (off != null && !off.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.OFF_HAND, off);
        ItemStack helmet = itemFromSerializable(() -> kit.getHelmet());
        if (helmet != null && !helmet.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.HEAD, helmet);
        ItemStack chest = itemFromSerializable(() -> kit.getChestplate());
        if (chest != null && !chest.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.CHEST, chest);
        ItemStack legs = itemFromSerializable(() -> kit.getLeggings());
        if (legs != null && !legs.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.LEGS, legs);
        ItemStack boots = itemFromSerializable(() -> kit.getBoots());
        if (boots != null && !boots.getType().isAir())
            equipment.put(org.bukkit.inventory.EquipmentSlot.FEET, boots);
        syncEquipment();
    }

    public boolean hasLineOfSight(Player target) {
        Player bukkit = getBukkitPlayer();
        return bukkit != null && target != null && bukkit.isValid() && bukkit.hasLineOfSight(target);
    }

    private void configureDummyConnection(Object server, GameProfile profile) {
        Object packetFlowServerbound = Reflect.enumValue(C_PACKET_FLOW, "SERVERBOUND");
        Object connection = Reflect.newInstance(CTOR_CONNECTION, packetFlowServerbound);
        Object cookie = Reflect.invoke(null, M_COMMON_LISTENER_COOKIE_CREATE_INITIAL, profile, false);
        Object packetListener = Reflect.newInstance(CTOR_SERVER_GAME_PACKET_LISTENER, server, connection, handle,
                cookie);
        Reflect.set(handle, F_CONNECTION, packetListener);
    }

    private void syncEquipment() {
        if (control == null)
            return;

        control.setItemSlot("MAINHAND", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HAND)));
        control.setItemSlot("OFFHAND", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND)));
        control.setItemSlot("HEAD", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD)));
        control.setItemSlot("CHEST", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.CHEST)));
        control.setItemSlot("LEGS", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.LEGS)));
        control.setItemSlot("FEET", copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.FEET)));

        Player bukkit = getBukkitPlayer();
        if (bukkit != null) {
            try {
                bukkit.getInventory()
                        .setItemInMainHand(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HAND)));
                bukkit.getInventory()
                        .setItemInOffHand(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND)));
                bukkit.getInventory().setHelmet(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD)));
                bukkit.getInventory().setChestplate(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.CHEST)));
                bukkit.getInventory().setLeggings(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.LEGS)));
                bukkit.getInventory().setBoots(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.FEET)));
            } catch (Throwable t) {
                debug("Failed to sync Bukkit equipment: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        resendEquipmentToViewers();
    }

    private void resendEquipmentToViewers() {
        if (control == null)
            return;
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> pairs = new ArrayList<>();
        pairs.add(Pair.of(EquipmentSlot.MAINHAND,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HAND)))));
        pairs.add(Pair.of(EquipmentSlot.OFFHAND,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND)))));
        pairs.add(Pair.of(EquipmentSlot.HEAD,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD)))));
        pairs.add(Pair.of(EquipmentSlot.CHEST,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.CHEST)))));
        pairs.add(Pair.of(EquipmentSlot.LEGS,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.LEGS)))));
        pairs.add(Pair.of(EquipmentSlot.FEET,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.FEET)))));

        for (Player viewer : getWorldViewers()) {
            try {
                ((CraftPlayer) viewer).getHandle().connection
                        .send(new ClientboundSetEquipmentPacket(getEntityId(), pairs));
            } catch (Throwable t) {
                debug("Failed to send equipment packet to " + viewer.getName() + ": " + t.getClass().getSimpleName()
                        + ": " + t.getMessage());
            }
        }
    }

    private void hideFromWorldViewers() {
        if (handle == null)
            return;
        List<UUID> uuids = List.of(uuid);
        for (Player viewer : getWorldViewers()) {
            try {
                ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(getEntityId()));
                ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundPlayerInfoRemovePacket(uuids));
            } catch (Throwable t) {
                debug("Failed to remove bot packets for " + viewer.getName() + ": " + t.getClass().getSimpleName()
                        + ": " + t.getMessage());
            }
        }
    }

    private void resendEquipmentToSingleViewer(Player viewer) {
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> pairs = new ArrayList<>();
        pairs.add(Pair.of(EquipmentSlot.MAINHAND,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HAND)))));
        pairs.add(Pair.of(EquipmentSlot.OFFHAND,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.OFF_HAND)))));
        pairs.add(Pair.of(EquipmentSlot.HEAD,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD)))));
        pairs.add(Pair.of(EquipmentSlot.CHEST,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.CHEST)))));
        pairs.add(Pair.of(EquipmentSlot.LEGS,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.LEGS)))));
        pairs.add(Pair.of(EquipmentSlot.FEET,
                CraftItemStack.asNMSCopy(copyOrAir(equipment.get(org.bukkit.inventory.EquipmentSlot.FEET)))));
        ((CraftPlayer) viewer).getHandle().connection.send(new ClientboundSetEquipmentPacket(getEntityId(), pairs));
    }

    private List<Player> getWorldViewers() {
        if (spawnLocation == null || spawnLocation.getWorld() == null)
            return List.of();
        List<Player> viewers = new ArrayList<>();
        for (Player player : spawnLocation.getWorld().getPlayers()) {
            if (uuid != null && uuid.equals(player.getUniqueId()))
                continue;
            viewers.add(player);
        }
        return viewers;
    }

    private String trimName(String raw) {
        String safe = raw == null ? "Bot" : raw.trim();
        if (safe.isEmpty())
            safe = "Bot";
        return safe.length() <= 16 ? safe : safe.substring(0, 16);
    }

    private ItemStack itemFromKit(int slot) {
        if (kit == null || kit.getInventory() == null || kit.getInventory().get(slot) == null)
            return null;
        return kit.getInventory().get(slot).convertToItem();
    }

    private interface SerializableSupplier {
        SerializableItem get();
    }

    private ItemStack itemFromSerializable(SerializableSupplier supplier) {
        try {
            SerializableItem item = supplier.get();
            return item == null ? null : item.convertToItem();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack copyOrAir(ItemStack stack) {
        return stack == null ? new ItemStack(Material.AIR) : stack.clone();
    }

    private void debug(String message) {
        if (!debugLogging)
            return;
        plugin.getLogger().warning("[AutomatedCombat][DEBUG][NPC] " + message);
    }

    private void primeViewer(Player viewer) {
        if (handle == null || viewer == null || !viewer.isOnline())
            return;

        try {
            ((CraftPlayer) viewer).getHandle().connection.send(
                    ClientboundPlayerInfoUpdatePacket
                            .createPlayerInitializing(List.of((net.minecraft.server.level.ServerPlayer) handle)));
            debug("Primed PlayerInfo for viewer=" + viewer.getName());
        } catch (Throwable t) {
            debug("Failed to prime PlayerInfo for " + viewer.getName() + ": " + t.getClass().getSimpleName() + ": "
                    + t.getMessage());
        }
    }

    private void showToViewer(Player viewer) {
        if (handle == null || viewer == null || !viewer.isOnline())
            return;

        try {
            Object viewerHandle = ((CraftPlayer) viewer).getHandle();
            Object level = ((CraftWorld) spawnLocation.getWorld()).getHandle();
            Object tracker = newServerEntity(level, handle);

            Consumer<Object> sender = packet -> ((CraftPlayer) viewer).getHandle().connection.send((Packet<?>) packet);

            M_SEND_PAIRING_DATA.invoke(tracker, viewerHandle, sender);

            resendEquipmentToSingleViewer(viewer);
            debug("Sent pairing data to viewer=" + viewer.getName());
        } catch (Throwable t) {
            debug("Failed to show NPC to " + viewer.getName() + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Object newServerEntity(Object level, Object entity) {
        if (CTOR_SERVER_ENTITY == null) {
            throw new IllegalStateException("Could not resolve ServerEntity constructor");
        }

        Class<?>[] params = CTOR_SERVER_ENTITY.getParameterTypes();

        Object synchronizer = java.lang.reflect.Proxy.newProxyInstance(
                params[4].getClassLoader(),
                new Class[] { params[4] },
                (proxy, method, methodArgs) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == void.class)
                        return null;
                    if (rt == boolean.class)
                        return false;
                    if (rt == byte.class)
                        return (byte) 0;
                    if (rt == short.class)
                        return (short) 0;
                    if (rt == int.class)
                        return 0;
                    if (rt == long.class)
                        return 0L;
                    if (rt == float.class)
                        return 0.0f;
                    if (rt == double.class)
                        return 0.0d;
                    if (rt == char.class)
                        return '\0';
                    return null;
                });

        java.util.Set<?> trackedSet = new java.util.HashSet<>();

        return Reflect.newInstance(
                CTOR_SERVER_ENTITY,
                level,
                entity,
                1,
                true,
                synchronizer,
                trackedSet);
    }
}
