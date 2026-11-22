package org.albi.core.modules.Duels.AutomatedCombat;

import org.bukkit.entity.Player;

public interface AutomatedCombatCallbacks {

    default void onMenuOpened(Player player, BotSettings settings) {}

    default void onSessionCreated(BotSession session) {}

    default void onNpcSpawned(BotSession session, FakePlayerNPC npc) {}

    default void onCountdownStarted(BotSession session) {}

    default void onFightStarted(BotSession session) {}

    default void onTick(BotSession session) {}

    default void onBotDamaged(BotSession session, Player attacker, double finalDamage) {}

    default void onBotAttack(BotSession session, double baseDamage) {}

    default void onPlayerWin(BotSession session) {}

    default void onPlayerLoss(BotSession session) {}

    default void onSessionClosed(BotSession session) {}
}
