package org.garsooon.containerutils;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RestockCommand implements CommandExecutor {

    private final ContainerUtils plugin;

    public RestockCommand(ContainerUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("restock")) return false;

        if (!sender.hasPermission("containerutils.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use ContainerUtils commands.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "ContainerUtils Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/restock create - Register a container by punching it");
            sender.sendMessage(ChatColor.YELLOW + "/restock list - List all registered containers");
            sender.sendMessage(ChatColor.YELLOW + "/restock clear - Clear all registered containers");
            sender.sendMessage(ChatColor.YELLOW + "/restock time <seconds> - Set default restock time");
            sender.sendMessage(ChatColor.YELLOW + "/restock ctime <seconds> - Set container restock time");
            sender.sendMessage(ChatColor.YELLOW + "/restock reload - Reload config");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (plugin.createModePlayers.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already in container registration mode. Punch a container to register it.");
                return true;
            }

            plugin.createModePlayers.add(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Punch a container to register it for restocking.");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GREEN + "Registered containers: " + plugin.containerTemplates.size());
            for (String location : plugin.containerTemplates.keySet()) {
                int timeLeft = plugin.containerTimers.getOrDefault(location, 0);
                sender.sendMessage(ChatColor.GRAY + location + ChatColor.DARK_GRAY + " (restocks in " + timeLeft + "s)");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            plugin.containerTemplates.clear();
            plugin.containerTimers.clear();
            sender.sendMessage(ChatColor.GREEN + "All registersed containers cleared!");
            plugin.clearRegisteredContainers();
            return true;
        }

        if (args[0].equalsIgnoreCase("time")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /restock time <seconds>");
                return true;
            }

            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < 1) {
                    sender.sendMessage(ChatColor.RED + "Time must be at least 1 second!");
                    return true;
                }

                plugin.setDefaultRestockTime(seconds);
                sender.sendMessage(ChatColor.GREEN + "Default restock time set to " + seconds + " seconds!");
                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number!");
                return true;
            }
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("ctime")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[ContainerUtils] Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /restock ctime <seconds>");
                return true;
            }

            int seconds;
            try {
                seconds = Integer.parseInt(args[1]);
                if (seconds < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid time. Must be a non-negative number.");
                return true;
            }

            plugin.pendingRestockTimes.put(player.getUniqueId(), seconds);
            player.sendMessage(ChatColor.YELLOW + "Punch a registered container to set its restock time to " + seconds + " seconds.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigFile();
            sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
            return true;
        }

        return false;
    }
}
