package org.albi.core;

import com.samjakob.spigui.SpiGUI;
import org.albi.core.data.*;
import org.albi.core.gui.GuiService;
import org.albi.core.gui.LobbyHotbar;
import org.albi.core.gui.InterfaceFiles;
import org.albi.core.gui.Messages;
import org.albi.core.managers.ChatFilter;
import org.albi.core.modules.CustomKit.GUIs.CustomKitGUIMain;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.DuelSettings;
import org.albi.core.modules.Duels.ranked.RankedRules;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.ModuleManager;
import org.albi.core.managers.CommandManager;
import org.albi.core.managers.EventManager;
import org.albi.core.managers.PlaceholderManager;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.serializable.SerializableLocation;
import org.albi.core.util.AnnouncementService;
import org.albi.core.util.CombatSettings;
import org.albi.core.util.FeatherBackground;
import org.albi.core.modules.Duels.AutomatedCombat.Manager;
import org.albi.core.util.KitSafety;
import org.albi.core.util.LobbySettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.*;

public final class Core extends JavaPlugin {

    public static Core instance;
    private static Cache cache = new Cache();
    private final InterfaceFiles interfaceFiles = new InterfaceFiles(this);
    private final LangAPI messageAPI = new LangAPI(this, interfaceFiles);
    private static Data data = new Data();
    private SpiGUI spiGUI;
    private GuiService menus;
    private LobbyHotbar lobbyHotbar;
    private LobbySettings lobbySettings;
    private DuelSettings duelSettings;
    private CombatSettings combatSettings;
    private CustomKitGUIMain guiKit;
    public static final Map<UUID, TeleportRequest> teleportRequests = new HashMap<>();
    private PartyManager partyManager;
    private ChatFilter chatFilter;
    private org.albi.core.commands.DeathEffectsGUI deathEffectsGUI;
    private org.albi.core.managers.tiers.TierManager tierManager;
    private org.albi.core.util.AutoRestartService autoRestartService;
    private org.albi.core.events.GlobalChatRedisListener globalChatRedisListener;

    public volatile int playersInDuel = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobbySettings = LobbySettings.from(getConfig());
        duelSettings = DuelSettings.from(getConfig());
        combatSettings = CombatSettings.from(getConfig());
        KitSafety.configure(getConfig());
        RankedRules.configure(getConfig());
        loadInterfaceFiles(true);
        if (!loadLocalData()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Commands and background jobs depend on storage, so start it before registering either.
        try {
            StatManagement.init(this, StatManagement.Config.from(getConfig()));
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid storage settings: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (RuntimeException e) {
            getLogger().severe("Storage startup failed (" + e.getClass().getSimpleName()
                    + "). Check your MySQL connection, credentials and permissions.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        instance = this;
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        if (getServer().getPluginManager().isPluginEnabled("FeatherServerAPI")) {
            FeatherBackground.load(this);
        }

        spiGUI = new SpiGUI(this);
        menus = new GuiService(this, interfaceFiles, spiGUI);
        lobbyHotbar = new LobbyHotbar(this, menus);
        Messages.use(messageAPI);
        guiKit = new CustomKitGUIMain(this);
        chatFilter = new ChatFilter(this);
        deathEffectsGUI = new org.albi.core.commands.DeathEffectsGUI(this);
        tierManager = new org.albi.core.managers.tiers.TierManager(this);

        new CommandManager(this);
        new EventManager(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderManager(this).register();
        }
        getCommand("deatheffects").setExecutor(new org.albi.core.commands.DeathEffectsCommand(this));
        ModuleManager.initializeModules(this);

        autoRestartService = new org.albi.core.util.AutoRestartService(this);
        autoRestartService.start();
        if (StatManagement.getRedis().isEnabled() && getConfig().getBoolean("network.global_chat", true)) {
            globalChatRedisListener = new org.albi.core.events.GlobalChatRedisListener(this);
            globalChatRedisListener.start();
        }
        new AnnouncementService(this).start();
        new StatRecorder(this, StatManagement.getSharedState(), getServerName()).start();

        int flushSeconds = Math.max(1, getConfig().getInt("storage.flush_interval_seconds", 600));
        long flushTicks = flushSeconds * 20L;
        Bukkit.getScheduler().runTaskTimer(this, StatManagement::flushAllAsync, flushTicks, flushTicks);

        for (Player player : Bukkit.getOnlinePlayers()) {
            cache.removeExtendedPlayer(player);
            getExtendedPlayer(player).whenReady().thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) Messages.send(player, "messages.core.your-profile-has-now-been-rebuilt");
            }));
        }
        Bukkit.getScheduler().runTaskTimer(this, this::trackPlayerTime, 20L, 20L);
        getLogger().info("Vise Core enabled.");
    }

    private boolean loadLocalData() {
        File dataFile = new File(getDataFolder(), "data.dat");
        data = new Data();
        if (!dataFile.exists()) return true;

        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(dataFile))) {
            if (!(input.readObject() instanceof Data savedData)) {
                throw new IOException("Unexpected data type in data.dat");
            }
            data = savedData;
            return true;
        } catch (IOException | ClassNotFoundException e) {
            getLogger().log(Level.SEVERE, "Could not load data.dat. Check the file or restore a backup.", e);
            return false;
        }
    }

    private void trackPlayerTime() {
        int duelPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ExtendedPlayer profile = getExtendedPlayer(player);
            if (profile.getPlayerDuelInstance() != null) duelPlayers++;

            MainPlayer main = profile.getMainPlayer();
            if (main != null) main.addTimePlayed(1);
        }
        playersInDuel = duelPlayers;
    }

    @Override
    public void onDisable() {
        if (instance != this) return;
        try {
            DuelManager.shutdownMatchmaking();
            Manager.shutdown(this);
            if (globalChatRedisListener != null) globalChatRedisListener.stop();

            saveLocalData();
        } finally {
            StatManagement.shutdown();
            instance = null;
        }
    }

    private void saveLocalData() {
        var directory = getDataFolder().toPath();
        var target = directory.resolve("data.dat");
        try {
            Files.createDirectories(directory);
            var temporary = Files.createTempFile(directory, "data-", ".tmp");
            try {
                try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temporary))) {
                    output.writeObject(data);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save data.dat.", e);
        }
    }

    public static Core getInstance() {
        return instance;
    }

    public ExtendedPlayer getExtendedPlayer(Player p) {
        return cache.getExtendedPlayer(p);
    }

    public LangAPI getMessageAPI() {
        return messageAPI;
    }

    public Data getData() {
        return data;
    }

    public Cache getCache() {
        return cache;
    }

    public void resetPlayer(Player p, boolean addOther) {
        // Stop all motion
        if (addOther)
            p.setVelocity(new Vector(0, 0, 0));

        // Restore health and food
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(1f);
        p.setFireTicks(0);

        // Reset experience
        p.setExp(0f);
        p.setLevel(0);
        p.setTotalExperience(0);

        // Clear inventory and armor
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setExtraContents(null);

        // Remove all active potion effects
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));

        // Reset game mode if needed (optional)
        if (addOther)
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Reset walk/fly speed to default
        p.setWalkSpeed(0.2f);
        p.setFlySpeed(0.1f);

        // Disable flight
        if (addOther)
            p.setAllowFlight(false);
        if (addOther)
            p.setFlying(false);

        // Extinguish
        p.setFireTicks(0);

        // Reset fall distance
        p.setFallDistance(0);

        // Optionally close inventory and clear title
        p.closeInventory();
        p.sendTitle("", "", 0, 0, 0);
    }

    public void resetPlayer(Player p) {
        resetPlayer(p, true);
    }

    public void teleportToLobby(Player player) {
        teleportToLobbyAsync(player);
    }

    public CompletableFuture<Boolean> teleportToLobbyAsync(Player player) {
        SerializableLocation spawnSerialized = (SerializableLocation) getData().get("spawn");
        ExtendedPlayer ep = getExtendedPlayer(player);
        CompletableFuture<Boolean> teleport = CompletableFuture.completedFuture(true);
        if (spawnSerialized != null) {
            teleport = player.teleportAsync(spawnSerialized.convertToLocation());

            if (ep != null && ep.isHideLobbyPlayers()) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other != player)
                        player.hidePlayer(this, other);
                }
            }

            getMessageAPI().sendMessageLang(player, "spawn");
        }
        MainPlayer mp = ep.getMainPlayer();

        giveAppropriateLobbyItems(player);
        Bukkit.getScheduler().runTaskLater(this, () -> giveAppropriateLobbyItems(player), 1L);

        if (mp != null) {
            if (player.hasPermission("core.lobby.speed") && mp.lobbySpeed()) {
                applyLobbyEffect(player, PotionEffectType.SPEED, lobbySettings.equipment().speedLevel());
            }
            if (player.hasPermission("core.lobby.jumpboost") && mp.lobbyJumpBoost()) {
                applyLobbyEffect(player, PotionEffectType.JUMP_BOOST, lobbySettings.equipment().jumpBoostLevel());
            }
        }

        return teleport;
    }

    public CombatSettings getCombatSettings() {
        return combatSettings;
    }

    public DuelSettings getDuelSettings() {
        return duelSettings;
    }

    public LobbySettings getLobbySettings() {
        return lobbySettings;
    }

    public LobbyHotbar getLobbyHotbar() {
        return lobbyHotbar;
    }

    public boolean isLobby(Player player) {
        return player != null && player.getWorld().getName().equalsIgnoreCase(getConfig().getString("lobby_world", "world"));
    }

    public void giveAppropriateLobbyItems(Player player) {
        if (!isLobby(player)) return;
        if (partyManager != null && partyManager.isInParty(player)) {
            partyManager.giveLobbyPartyItems(player, partyManager.isLeader(player));
            return;
        }
        applyLobbyShortcutItems(player);
    }

    public void applyLobbyShortcutItems(Player player) {
        if (!isLobby(player) || (partyManager != null && partyManager.isInParty(player))) return;
        var tournaments = org.albi.core.modules.Tournaments.TournamentManager.getInstance();
        if (tournaments != null && tournaments.isInTournament(player)) {
            lobbyHotbar.render(player, LobbyHotbar.Layout.TOURNAMENT, false);
            return;
        }

        var casual = DuelManager.getDuelQueueModule();
        var ranked = DuelManager.getRankedQueueModule();
        boolean queued = (casual != null && casual.isInQueue(player)) || (ranked != null && ranked.isInQueue(player));
        lobbyHotbar.render(player, queued ? LobbyHotbar.Layout.QUEUED : LobbyHotbar.Layout.LOBBY, false);
        equipLobbyElytra(player);
        updateLobbyWindCharges(player, player.getInventory().getItemInMainHand());
    }

    public void applyLobbyEffect(Player player, PotionEffectType type, int level) {
        if (level > 0) player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, level - 1, false, false, false));
    }

    private boolean usesLobbyElytra(Player player) {
        if (!lobbySettings.equipment().elytra() || !player.hasPermission("core.elytra")) return false;
        MainPlayer profile = getExtendedPlayer(player).getMainPlayer();
        return profile != null && profile.elytra();
    }

    public void equipLobbyElytra(Player player) {
        if (!isLobby(player) || !usesLobbyElytra(player)) return;
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.setUnbreakable(lobbySettings.equipment().elytraUnbreakable());
        int level = lobbySettings.equipment().elytraUnbreakingLevel();
        if (player.hasPermission("core.elytra.enchanted") && level > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, level, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.setLore(List.of(GuiService.text("lobby-hotbar.elytra-owner", player.getName())));
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);
    }

    public void updateLobbyWindCharges(Player player, ItemStack held) {
        if (lobbySettings.equipment().windCharges() == 0 || !usesLobbyElytra(player)) return;
        boolean hasShortcut = held != null && !held.getType().isAir() && held.hasItemMeta()
                && held.getItemMeta().hasDisplayName();
        boolean hasCharge = player.getInventory().getItemInOffHand().getType() == Material.WIND_CHARGE;
        if (hasShortcut && hasCharge) {
            player.getInventory().setItemInOffHand(null);
        } else if (!hasShortcut && !hasCharge) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.WIND_CHARGE, lobbySettings.equipment().windCharges()));
        }
    }

    public SpiGUI getGUI() {
        return spiGUI;
    }

    public GuiService getMenus() {
        return menus;
    }

    public boolean loadInterfaceFiles(boolean startup) {
        try {
            interfaceFiles.load(startup);
            if (menus != null) menus.onReload();
            return true;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().warning("Could not load lang.yml and gui.yml. " + e.getMessage());
            return false;
        }
    }

    public CustomKitGUIMain getGuiKit() {
        return guiKit;
    }

    public String getServerName() {
        return getConfig().getString("region", "EU").toUpperCase(Locale.ROOT) + "-" + getConfig().getString("id_number", "1");
    }

    public void setPartyManager(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public org.albi.core.commands.DeathEffectsGUI getDeathEffectsGUI() {
        return deathEffectsGUI;
    }

    public org.albi.core.managers.tiers.TierManager getTierManager() {
        return tierManager;
    }

    public void applyPossibleKit(Player player, Kit kit) {

        // first, givekit
        kit.giveKit(player);
        var ep = getExtendedPlayer(player);
        if (ep != null && ep.isReady()) {
            MainPlayer mp = ep.getMainPlayer();
            var edit = mp.getKitEdit(kit.getName());
            if (edit != null && !edit.isEmpty()) {
                edit.applyToIncludingOffhand(player);
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    var item = player.getInventory().getItem(i);
                    if (KitSafety.isBlocked(item))
                        player.getInventory().setItem(i, null);
                }
                if (KitSafety.isBlocked(player.getInventory().getItemInOffHand())) {
                    player.getInventory().setItemInOffHand(null);
                }
                player.updateInventory();
            }
        }
    }

    public ChatFilter getChatFilter() {
        return chatFilter;
    }

    public void applyImmediateArmorTrims(Player player) {
        // Does nothing for now ... implementation soon!
    }

    public void applyTabMode(Player player, ExtendedPlayer ep) {
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null)
            return;

        boolean friendsOnly = ep.getDisplayTab() == 1;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player))
                continue;

            if (!friendsOnly) {
                player.showPlayer(this, other);
                continue;
            }

            boolean isFriend = mp.getFriends().contains(other.getUniqueId());

            if (isFriend) {
                player.showPlayer(this, other);
            } else {
                player.hidePlayer(this, other);
            }
        }
    }

    public org.albi.core.util.AutoRestartService getAutoRestartService() {
        return autoRestartService;
    }
}
