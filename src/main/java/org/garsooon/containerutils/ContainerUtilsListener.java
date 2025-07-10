package org.garsooon.containerutils;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.ChatColor;

public class ContainerUtilsListener implements Listener {

    private final ContainerUtils plugin;

    public ContainerUtilsListener(ContainerUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !isContainer(block)) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("containerutils.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use container utilities.");
            return;
        }

        boolean allowRegistration = plugin.getBoolean("allow-player-registration", true);
        if (!allowRegistration && !player.hasPermission("containerutils.admin")) {
            player.sendMessage(ChatColor.RED + "Container registration is disabled for players.");
            return;
        }

        String locationKey = plugin.getLocationKey(block);

        if (plugin.createModePlayers.contains(player.getUniqueId())) {
            plugin.createModePlayers.remove(player.getUniqueId());
            plugin.registerContainer(block, player);
            event.setCancelled(true);
            return;
        }

        if (plugin.getContainerTemplates().containsKey(locationKey)) {
            plugin.restockContainer(block, player);
            event.setCancelled(true); // safe cancel — only on left-click
        }
    }

    private boolean isContainer(Block block) {
        Material material = block.getType();
        return material == Material.CHEST || material == Material.DISPENSER;
    }
}
