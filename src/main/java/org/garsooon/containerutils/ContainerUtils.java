package org.garsooon.containerutils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerUtils extends JavaPlugin {

    public final Map<String, ItemStack[]> containerTemplates = new ConcurrentHashMap<>();
    public final Map<String, Integer> containerTimers = new ConcurrentHashMap<>();
    public final Set<UUID> createModePlayers = new HashSet<>();

    public int defaultRestockTime = 300; // default 5 min
    public boolean announceRestock = false;

    private RestockCommand commandHandler;
    private ContainerUtilsListener eventListener;

    private File configFile;
    private Yaml yaml = new Yaml();
    private Map<String, Object> config = new HashMap<>();

    @Override
    public void onEnable() {
        System.out.println(ChatColor.GREEN + "[ContainerUtils] Plugin enabled.");
        loadConfig();

        eventListener = new ContainerUtilsListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);

        commandHandler = new RestockCommand(this);
        if (getCommand("restock") != null) {
            getCommand("restock").setExecutor(commandHandler);
        } else {
            System.out.println(ChatColor.RED + "[ContainerUtils] Command 'restock' not found in plugin.yml!");
        }

        startRestockTimer();
    }

    @Override
    public void onDisable() {
        System.out.println(ChatColor.YELLOW + "[ContainerUtils] Plugin disabled.");
        containerTemplates.clear();
        containerTimers.clear();
        createModePlayers.clear();
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            getDataFolder().mkdirs();

            // Default config values
            config.put("default-restock-time", 300);
            config.put("announce-restock", false);
            config.put("allow-player-registration", true);

            saveConfigFile();
        }

        try (InputStream input = new FileInputStream(configFile)) {
            Object loaded = yaml.load(input);

            if (loaded instanceof Map) {
                config = (Map<String, Object>) loaded;
            } else {
                System.out.println(ChatColor.RED + "[ContainerUtils] Config file is not a valid map. Using defaults.");
            }
        } catch (Exception e) {
            System.out.println(ChatColor.RED + "[ContainerUtils] Failed to load config: " + e.getMessage());
        }

        defaultRestockTime = getInt("default-restock-time", 300);
        announceRestock = getBoolean("announce-restock", false);
    }

    public void saveConfigFile() {
        try (Writer writer = new FileWriter(configFile)) {
            yaml.dump(config, writer);
        } catch (IOException e) {
            System.out.println(ChatColor.RED + "[ContainerUtils] Failed to save config: " + e.getMessage());
        }
    }

    public void reloadConfigFile() {
        loadConfig();
    }

    public boolean isContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.DISPENSER;
    }

    public void registerContainer(Block block, Player player) {
        BlockState state = block.getState();
        Inventory inv = getInventoryFromState(state);
        if (inv == null) {
            player.sendMessage(ChatColor.RED + "Cannot register container: Inventory not accessible.");
            System.out.println(ChatColor.RED + "[ContainerUtils] Failed to get inventory for container at " + getLocationKey(block));
            return;
        }

        String key = getLocationKey(block);
        ItemStack[] template = new ItemStack[inv.getSize()];
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                template[i] = item.clone();
            }
        }

        containerTemplates.put(key, template);
        containerTimers.put(key, defaultRestockTime);

        player.sendMessage(ChatColor.GREEN + "Container registered for restocking! Punch without sneaking to restock.");
        System.out.println(ChatColor.GREEN + "[ContainerUtils] Registered container at " + key + " with " + inv.getSize() + " slots.");
    }

    public void restockContainer(Block block, Player player) {
        String key = getLocationKey(block);
        ItemStack[] template = containerTemplates.get(key);
        if (template == null) {
            player.sendMessage(ChatColor.RED + "This container is not registered for restocking.");
            return;
        }

        Inventory inv = getInventoryFromState(block.getState());
        if (inv == null) {
            player.sendMessage(ChatColor.RED + "Cannot restock container: Inventory not accessible.");
            return;
        }

        inv.clear();
        for (int i = 0; i < template.length && i < inv.getSize(); i++) {
            if (template[i] != null) {
                inv.setItem(i, template[i].clone());
            }
        }

        containerTimers.put(key, defaultRestockTime);
        if (player != null) player.sendMessage(ChatColor.GREEN + "Container restocked!");
    }

    public String getLocationKey(Block block) {
        return block.getWorld().getName() + ":" +
                block.getX() + ":" +
                block.getY() + ":" +
                block.getZ();
    }

    private void startRestockTimer() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Map.Entry<String, Integer> entry : containerTimers.entrySet()) {
                String key = entry.getKey();
                int timeLeft = entry.getValue();

                if (timeLeft <= 0) {
                    autoRestockContainer(key);
                    containerTimers.put(key, defaultRestockTime);
                } else {
                    containerTimers.put(key, timeLeft - 1);
                }
            }
        }, 20L, 20L);
    }

    private void autoRestockContainer(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return;

        try {
            String world = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            Block block = getServer().getWorld(world).getBlockAt(x, y, z);
            if (!isContainer(block)) {
                containerTemplates.remove(key);
                containerTimers.remove(key);
                return;
            }

            ItemStack[] template = containerTemplates.get(key);
            if (template == null) return;

            Inventory inv = getInventoryFromState(block.getState());
            if (inv == null) return;

            inv.clear();
            for (int i = 0; i < template.length && i < inv.getSize(); i++) {
                if (template[i] != null) {
                    inv.setItem(i, template[i].clone());
                }
            }

            if (announceRestock) {
                for (Player p : getServer().getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(world) &&
                            p.getLocation().distance(block.getLocation()) <= 10) {
                        p.sendMessage(ChatColor.GRAY + "Container auto-restocked nearby.");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(ChatColor.RED + "[ContainerUtils] Error auto-restocking container: " + e.getMessage());
        }
    }


    private Inventory getInventoryFromState(BlockState state) {
        if (state instanceof Chest) return ((Chest) state).getInventory();
        if (state instanceof Dispenser) return ((Dispenser) state).getInventory();
        return null;
    }

    private int getInt(String key, int def) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object value = config.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }

    public Map<String, ItemStack[]> getContainerTemplates() {
        return containerTemplates;
    }

    public Map<String, Integer> getContainerTimers() {
        return containerTimers;
    }

    public int getDefaultRestockTime() {
        return defaultRestockTime;
    }

    public void setDefaultRestockTime(int time) {
        this.defaultRestockTime = time;
        config.put("default-restock-time", time);
        saveConfigFile();
    }

    // I dont think im going to finish this
    public boolean isAnnounceRestock() {
        return announceRestock;
    }
}
