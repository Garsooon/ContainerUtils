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
    public final Map<String, Integer> containerRestockTimes = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> pendingRestockTimes = new HashMap<>();
    public final Set<UUID> createModePlayers = new HashSet<>();

    public int defaultRestockTime = 300;
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
        loadRestockData();

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
        saveRestockData();
        containerTemplates.clear();
        containerTimers.clear();
        containerRestockTimes.clear();
        createModePlayers.clear();
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            getDataFolder().mkdirs();
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

    public void loadRestockData() {
        File file = new File(getDataFolder(), "restocks.yml");
        if (!file.exists()) {
            System.out.println("[ContainerUtils] No restocks.yml file found.");
            return;
        }

        try (InputStream input = new FileInputStream(file)) {
            Object raw = yaml.load(input);
            if (!(raw instanceof Map)) return;

            Map<String, Object> data = (Map<String, Object>) raw;

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String loc = entry.getKey();
                Map<String, Object> containerData = (Map<String, Object>) entry.getValue();

                List<Map<String, Object>> itemData = (List<Map<String, Object>>) containerData.get("items");
                int timer = (int) containerData.get("timer");
                int customTime = containerData.containsKey("restock_time") ? (int) containerData.get("restock_time") : defaultRestockTime;

                ItemStack[] contents = new ItemStack[itemData.size()];
                for (int i = 0; i < itemData.size(); i++) {
                    Map<String, Object> itemMap = itemData.get(i);
                    contents[i] = itemMap != null ? deserializeItemStack(itemMap) : null;
                }

                containerTemplates.put(loc, contents);
                containerTimers.put(loc, timer);
                containerRestockTimes.put(loc, customTime);
            }

            System.out.println(ChatColor.GREEN + "[ContainerUtils] Loaded restock data from restocks.yml");
        } catch (Exception e) {
            System.out.println(ChatColor.RED + "[ContainerUtils] Failed to load restock data: " + e.getMessage());
        }
    }

    public void saveRestockData() {
        File file = new File(getDataFolder(), "restocks.yml");

        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, ItemStack[]> entry : containerTemplates.entrySet()) {
            String loc = entry.getKey();
            List<Map<String, Object>> items = new ArrayList<>();

            for (ItemStack item : entry.getValue()) {
                items.add(item != null ? serializeItemStack(item) : null);
            }

            Map<String, Object> containerData = new HashMap<>();
            containerData.put("items", items);
            containerData.put("timer", containerTimers.getOrDefault(loc, defaultRestockTime));
            containerData.put("restock_time", containerRestockTimes.getOrDefault(loc, defaultRestockTime));
            data.put(loc, containerData);
        }

        try (Writer writer = new FileWriter(file)) {
            yaml.dump(data, writer);
            System.out.println(ChatColor.GREEN + "[ContainerUtils] Saved restock data to restocks.yml");
        } catch (IOException e) {
            System.out.println(ChatColor.RED + "[ContainerUtils] Failed to save restock data: " + e.getMessage());
        }
    }

    // Doesn't serialize nbt data at all, reads as null. Im not going to fix it, someone else can if they want
    public static Map<String, Object> serializeItemStack(ItemStack item) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", item.getType().name());
        map.put("amount", item.getAmount());
        map.put("durability", item.getDurability());
        return map;
    }

    public static ItemStack deserializeItemStack(Map<String, Object> map) {
        Material type = Material.matchMaterial((String) map.get("type"));
        int amount = (int) map.get("amount");
        short durability = map.containsKey("durability") ? ((Integer) map.get("durability")).shortValue() : 0;
        return new ItemStack(type, amount, durability);
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
        containerRestockTimes.put(key, defaultRestockTime);

        player.sendMessage(ChatColor.GREEN + "Container registered for restocking! Punch without sneaking to restock.");
        System.out.println(ChatColor.GREEN + "[ContainerUtils] Registered container at " + key + " with " + inv.getSize() + " slots.");

        saveRestockData();
    }

    public void clearRegisteredContainers() {
        containerTemplates.clear();
        containerTimers.clear();
        containerRestockTimes.clear();
        saveRestockData();
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

        containerTimers.put(key, containerRestockTimes.getOrDefault(key, defaultRestockTime));
        if (player != null) player.sendMessage(ChatColor.GREEN + "Container restocked!");
    }

    private void startRestockTimer() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Map.Entry<String, Integer> entry : containerTimers.entrySet()) {
                String key = entry.getKey();
                int timeLeft = entry.getValue();

                if (timeLeft <= 0) {
                    autoRestockContainer(key);
                    containerTimers.put(key, containerRestockTimes.getOrDefault(key, defaultRestockTime));
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
                containerRestockTimes.remove(key);
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

    public String getLocationKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
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

    public boolean isAnnounceRestock() {
        return announceRestock;
    }
}
