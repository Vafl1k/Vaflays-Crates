package cz.vaflays.crates.manager;

import cz.vaflays.crates.VaflaysCratesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class CrateManager {

    private final VaflaysCratesPlugin plugin;
    private final NamespacedKey keyIdentifier;
    private final Map<String, ActiveCrate> activeCrates = new HashMap<>();
    private final Set<String> busyCrates = new HashSet<>();

    public static class ActiveCrate {
        public final String id;
        public final String crateType;
        public final Location centerLoc;
        public ItemDisplay baseDisplay;
        public ItemDisplay lidDisplay;
        public TextDisplay hologramDisplay;

        public ActiveCrate(String id, String crateType, Location centerLoc) {
            this.id = id;
            this.crateType = crateType;
            this.centerLoc = centerLoc;
        }
    }

    public static class RewardEntry {
        public final ItemStack item;
        public final double chance;

        public RewardEntry(ItemStack item, double chance) {
            this.item = item;
            this.chance = chance;
        }
    }

    public CrateManager(VaflaysCratesPlugin plugin) {
        this.plugin = plugin;
        this.keyIdentifier = new NamespacedKey(plugin, "vaflays_crate_key");
    }

    public String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public void placeCrate(Block block, String crateType, float playerYaw) {
        String id = blockKey(block);
        removeCrate(block);

        // Otočí bednu čelem (zámkem) směrem k hráči, který ji pokládá, zarovnaně na 90°
        float yaw = Math.round((playerYaw + 180f) / 90f) * 90f;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        center.setYaw(yaw);
        center.setPitch(0f);

        // Neviditelná bariéra jako pevný hitbox bloku
        block.setType(Material.BARRIER);

        plugin.getConfig().set("locations." + id + ".type", crateType);
        plugin.getConfig().set("locations." + id + ".world", block.getWorld().getName());
        plugin.getConfig().set("locations." + id + ".x", block.getX());
        plugin.getConfig().set("locations." + id + ".y", block.getY());
        plugin.getConfig().set("locations." + id + ".z", block.getZ());
        plugin.getConfig().set("locations." + id + ".yaw", yaw);
        plugin.saveConfig();

        spawnCrateVisuals(id, crateType, center);
    }

    public void spawnCrateVisuals(String id, String crateType, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        ActiveCrate active = new ActiveCrate(id, crateType, center);

        // 1. Spodek 3D bedny
        active.baseDisplay = world.spawn(center, ItemDisplay.class, e -> {
            e.setItemStack(createModelItem("vaflayscrates", "crate_base"));
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(),
                    new Vector3f(1.15f, 1.15f, 1.15f), new AxisAngle4f()
            ));
        });

        // 2. Víko 3D bedny (samostatná entita, aby se mohlo otevírat)
        active.lidDisplay = world.spawn(center, ItemDisplay.class, e -> {
            e.setItemStack(createModelItem("vaflayscrates", "crate_lid"));
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(),
                    new Vector3f(1.15f, 1.15f, 1.15f), new AxisAngle4f()
            ));
        });

        // 3. Hologram nad bednou
        String titleRaw = plugin.getConfig().getString("crates." + crateType + ".display-name", "<gold>Crate</gold>");
        active.hologramDisplay = world.spawn(center.clone().add(0, 1.25, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.text(plugin.mm().deserialize(titleRaw + "\n<gray>Klikni <yellow>LEVÝM</yellow> s klíčem\n<gray>Klikni <aqua>PRAVÝM</aqua> pro náhled"));
            e.setBackgroundColor(Color.fromARGB(130, 10, 10, 15));
        });

        activeCrates.put(id, active);
    }

    public void loadCratesFromConfig() {
        despawnAllEntities();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("locations");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            String type = sec.getString(id + ".type", "legendary");
            String worldName = sec.getString(id + ".world");
            if (worldName == null) continue;
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;

            double x = sec.getDouble(id + ".x") + 0.5;
            double y = sec.getDouble(id + ".y") + 0.5;
            double z = sec.getDouble(id + ".z") + 0.5;
            float yaw = (float) sec.getDouble(id + ".yaw", 0.0);

            Location loc = new Location(w, x, y, z, yaw, 0f);
            spawnCrateVisuals(id, type, loc);
        }
    }

    public void removeCrate(Block block) {
        String id = blockKey(block);
        ActiveCrate active = activeCrates.remove(id);
        if (active != null) {
            if (active.baseDisplay != null) active.baseDisplay.remove();
            if (active.lidDisplay != null) active.lidDisplay.remove();
            if (active.hologramDisplay != null) active.hologramDisplay.remove();
        }
        if (block.getType() == Material.BARRIER) {
            block.setType(Material.AIR);
        }
        plugin.getConfig().set("locations." + id, null);
        plugin.saveConfig();
    }

    public void despawnAllEntities() {
        for (ActiveCrate active : activeCrates.values()) {
            if (active.baseDisplay != null) active.baseDisplay.remove();
            if (active.lidDisplay != null) active.lidDisplay.remove();
            if (active.hologramDisplay != null) active.hologramDisplay.remove();
        }
        activeCrates.clear();
        busyCrates.clear();
    }

    public ActiveCrate getCrateAt(Block block) {
        return activeCrates.get(blockKey(block));
    }

    public boolean isBusy(String id) {
        return busyCrates.contains(id);
    }

    public void setBusy(String id, boolean busy) {
        if (busy) busyCrates.add(id);
        else busyCrates.remove(id);
    }

    public ItemStack createCrateKey(String crateType, int amount) {
        ItemStack key = createModelItem("vaflayscrates", "crate_key");
        key.setAmount(amount);
        ItemMeta meta = key.getItemMeta();

        String nameRaw = plugin.getConfig().getString("crates." + crateType + ".key-name", "<gold>Crate Key</gold>");
        meta.displayName(plugin.mm().deserialize(nameRaw));

        List<String> loreRaw = plugin.getConfig().getStringList("crates." + crateType + ".key-lore");
        List<Component> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(plugin.mm().deserialize(line));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(keyIdentifier, PersistentDataType.STRING, crateType);
        key.setItemMeta(meta);
        return key;
    }

    public boolean isValidKey(ItemStack item, String expectedCrateType) {
        if (item == null || !item.hasItemMeta()) return false;
        String stored = item.getItemMeta().getPersistentDataContainer().get(keyIdentifier, PersistentDataType.STRING);
        return expectedCrateType.equalsIgnoreCase(stored);
    }

    public List<RewardEntry> getRewards(String crateType) {
        List<RewardEntry> list = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crates." + crateType + ".rewards");
        if (sec == null) return list;

        for (String key : sec.getKeys(false)) {
            Material mat = Material.matchMaterial(sec.getString(key + ".material", "DIAMOND"));
            if (mat == null) mat = Material.DIAMOND;
            int amt = sec.getInt(key + ".amount", 1);
            double chance = sec.getDouble(key + ".chance", 10.0);
            String nameRaw = sec.getString(key + ".name", "<white>" + mat.name());

            ItemStack item = new ItemStack(mat, amt);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(plugin.mm().deserialize(nameRaw));
            item.setItemMeta(meta);

            list.add(new RewardEntry(item, chance));
        }
        return list;
    }

    public ItemStack rollWeightedReward(List<RewardEntry> entries) {
        double totalWeight = entries.stream().mapToDouble(r -> r.chance).sum();
        double rand = Math.random() * totalWeight;
        double current = 0;
        for (RewardEntry entry : entries) {
            current += entry.chance;
            if (rand <= current) {
                return entry.item.clone();
            }
        }
        return entries.get(0).item.clone();
    }

    public ItemStack createModelItem(String namespace, String modelKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey(namespace, modelKey));
        item.setItemMeta(meta);
        return item;
    }
}
