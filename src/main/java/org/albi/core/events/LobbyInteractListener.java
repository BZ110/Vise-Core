package org.albi.core.events;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;



public class LobbyInteractListener implements Listener {

    private final Core plugin;
    private final String lobbyWorld;

    public LobbyInteractListener(Core plugin) {
        this.plugin = plugin;
        this.lobbyWorld = plugin.getConfig().getString("lobby_world", "world");
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // ONLY when pressing shift, not releasing
        if (!plugin.getLobbySettings().interaction().sneakTabToggle() || !event.isSneaking()) return;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null || !ep.isReady()) return;

        // Only lobby
        String lobby = plugin.getConfig().getString("lobby_world", "world");
        if (!player.getWorld().getName().equalsIgnoreCase(lobby)) return;

        int newMode = ep.getDisplayTab() == 1 ? 0 : 1;
        ep.setDisplayTab(newMode);
    }




    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;

        plugin.updateLobbyWindCharges(player, player.getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        if (ep == null) {
            event.setCancelled(true);
            return;
        }

        // Duel region protection
        if (player.getWorld().getName().toLowerCase().endsWith("-duel")) {
            PlayerDuelInstance pd = ep.getPlayerDuelInstance();
            if (pd != null && "Countdown".equalsIgnoreCase(pd.getStatus())) {
                event.setCancelled(true);
                return;
            }
        }

        // Only restrict lobby
        if (!player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;
        if (ep.isBypassRegion() || !plugin.getLobbySettings().protection().interactions()) return;
        var settings = plugin.getLobbySettings().interaction();

        // Only look at the hand that triggered THIS event
        ItemStack item = event.getItem();
        Material type = item == null ? Material.AIR : item.getType();

        // Optional: ignore physical triggers like pressure plates separately
        if (event.getAction() == Action.PHYSICAL) {
            if (settings.physicalTriggers()) return;
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }

        // Shulker editor exception
        if (settings.shulkerEditor() && type.name().endsWith("SHULKER_BOX")) {
            event.setCancelled(true);

            try {
                BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                ShulkerBox box = (ShulkerBox) meta.getBlockState();

                Inventory gui = Bukkit.createInventory(
                        null, 27,
                        plugin.getMenus().nativeTitle("lobby.shulker-editor", player)
                );
                gui.setContents(box.getInventory().getContents());

                Listener close = new Listener() {
                    @EventHandler
                    public void onClose(InventoryCloseEvent e) {
                        if (!e.getInventory().equals(gui)) return;
                        try {
                            ItemStack[] contents = gui.getContents();
                            for (int i = 0; i < contents.length; i++) {
                                ItemStack nested = contents[i];
                                if (nested != null && nested.getType().name().endsWith("SHULKER_BOX")) {
                                    contents[i] = null;
                                }
                            }

                            box.getInventory().setContents(contents);
                            meta.setBlockState(box);
                            item.setItemMeta(meta);

                            if (event.getHand() == EquipmentSlot.HAND) {
                                player.getInventory().setItemInMainHand(item);
                            } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                                player.getInventory().setItemInOffHand(item);
                            }
                        } finally {
                            HandlerList.unregisterAll(this);
                        }
                    }
                };

                Bukkit.getPluginManager().registerEvents(close, plugin);
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
            } catch (Exception ex) {
                Messages.send(player, "messages.lobby-interact-listener.this-shulker-box-is-corrupted-or-unreadable", ChatColor.RED);
            }

            return;
        }

        // Allow rod / wind charge ITEM behavior, but block interaction with blocks
        if (settings.allowedItems().contains(type)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
            return;
        }

        // Block everything else
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler
    public void onWindChargeExplode(EntityExplodeEvent event) {
        if (event.getEntity() == null) return;

        if (event.getEntity().getType() == EntityType.WIND_CHARGE ||
                event.getEntity().getType() == EntityType.BREEZE_WIND_CHARGE) {

            if (!event.getEntity().getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;

            Entity entity = event.getEntity();

            var settings = plugin.getLobbySettings().interaction();
            if (settings.protectWindChargeBlocks()) {
                event.blockList().clear();
                event.setYield(0);
            }

            if (settings.boostWindCharge() && entity instanceof WindCharge windCharge) {
                if (windCharge.getShooter() instanceof LivingEntity shooter) {

                    double distanceSquared = shooter.getLocation().distanceSquared(entity.getLocation());

                    if (distanceSquared > settings.windRadius() * settings.windRadius()) return;

                    Vector direction = shooter.getLocation().toVector()
                            .subtract(entity.getLocation().toVector());

                    if (direction.lengthSquared() == 0) return;

                    direction.setY(0);
                    direction.normalize();

                    direction.multiply(settings.windHorizontal());

                    direction.setY(settings.windVertical());

                    shooter.setVelocity(direction);
                }
            }
        }
    }


}
