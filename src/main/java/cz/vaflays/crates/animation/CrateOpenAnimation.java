package cz.vaflays.crates.animation;

import cz.vaflays.crates.VaflaysCratesPlugin;
import cz.vaflays.crates.manager.CrateManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

public class CrateOpenAnimation {

    private final VaflaysCratesPlugin plugin;
    private final Player player;
    private final CrateManager.ActiveCrate crate;
    private final List<CrateManager.RewardEntry> rewardEntries;
    private final ItemStack finalReward;

    public CrateOpenAnimation(VaflaysCratesPlugin plugin, Player player, CrateManager.ActiveCrate crate, List<CrateManager.RewardEntry> rewardEntries) {
        this.plugin = plugin;
        this.player = player;
        this.crate = crate;
        this.rewardEntries = rewardEntries;
        this.finalReward = plugin.getCrateManager().rollWeightedReward(rewardEntries);
    }

    public void start() {
        plugin.getCrateManager().setBusy(crate.id, true);
        Location crateLoc = crate.centerLoc.clone();
        World world = crateLoc.getWorld();
        if (world == null) return;

        // Skryjeme dočasně hologram, aby nepřekážel ruletě
        if (crate.hologramDisplay != null) {
            crate.hologramDisplay.setViewRange(0f);
        }

        Vector forward = crateLoc.getDirection().setY(0).normalize();

        // 1. Spawn 3D klíče 1.5 bloku před zámkem
        Location keyStartLoc = crateLoc.clone().add(0, 0.1, 0).add(forward.clone().multiply(1.5));
        keyStartLoc.setDirection(forward.clone().multiply(-1));

        ItemDisplay keyDisplay = world.spawn(keyStartLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(plugin.getCrateManager().createModelItem("vaflayscrates", "crate_key"));
            entity.setInterpolationDuration(15);
        });

        world.playSound(crateLoc, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);

        new BukkitRunnable() {
            int tick = 0;
            ItemDisplay rouletteItem;
            TextDisplay rouletteName;
            final Random random = new Random();

            @Override
            public void run() {
                // FÁZE 1: Klíč pomalu letí dopředu do zámku
                if (tick == 1) {
                    Location lockLoc = crateLoc.clone().add(0, 0.1, 0).add(forward.clone().multiply(0.55));
                    lockLoc.setDirection(forward.clone().multiply(-1));
                    keyDisplay.setInterpolationDelay(0);
                    keyDisplay.setInterpolationDuration(16);
                    keyDisplay.teleport(lockLoc);
                }

                // FÁZE 2: Otočení klíče v zámku o 90°
                if (tick == 19) {
                    world.playSound(crateLoc, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 1.5f);
                    world.spawnParticle(Particle.CRIT, keyDisplay.getLocation(), 12, 0.1, 0.1, 0.1, 0.05);

                    Transformation trans = keyDisplay.getTransformation();
                    trans.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(90), 0, 0, 1));
                    keyDisplay.setInterpolationDelay(0);
                    keyDisplay.setInterpolationDuration(8);
                    keyDisplay.setTransformation(trans);
                }

                // FÁZE 3: Otevření víka bedny dozadu + zlatý efekt
                if (tick == 29) {
                    keyDisplay.remove();
                    world.playSound(crateLoc, Sound.BLOCK_CHEST_OPEN, 1.2f, 0.9f);
                    world.playSound(crateLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.2f);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, crateLoc.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0.12);

                    if (crate.lidDisplay != null) {
                        Transformation lidTrans = crate.lidDisplay.getTransformation();
                        lidTrans.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(-105), 1, 0, 0));
                        crate.lidDisplay.setInterpolationDelay(0);
                        crate.lidDisplay.setInterpolationDuration(12);
                        crate.lidDisplay.setTransformation(lidTrans);
                    }
                }

                // FÁZE 4: Vylétnutí 3D GUI rulety nad bednu
                if (tick == 36) {
                    Location guiLoc = crateLoc.clone().add(0, 1.15, 0);
                    rouletteItem = world.spawn(guiLoc, ItemDisplay.class, e -> {
                        e.setBillboard(Display.Billboard.VERTICAL);
                        e.setTransformation(new Transformation(
                                new Vector3f(0, 0, 0), new AxisAngle4f(),
                                new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()
                        ));
                    });

                    rouletteName = world.spawn(guiLoc.clone().add(0, 0.55, 0), TextDisplay.class, e -> {
                        e.setBillboard(Display.Billboard.VERTICAL);
                        e.setBackgroundColor(Color.fromARGB(170, 15, 15, 25));
                    });
                }

                // Točení odměn nad bednou
                if (tick >= 36 && tick < 96 && rouletteItem != null) {
                    int speed = (tick < 65) ? 2 : (tick < 82) ? 4 : 7;
                    if (tick % speed == 0) {
                        ItemStack preview = rewardEntries.get(random.nextInt(rewardEntries.size())).item;
                        rouletteItem.setItemStack(preview);
                        Component name = preview.getItemMeta().displayName();
                        rouletteName.text(name != null ? name : Component.text(preview.getType().name()));
                        world.playSound(crateLoc, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                    }
                }

                // FÁZE 5: Zastavení na finální výhře
                if (tick == 96 && rouletteItem != null) {
                    rouletteItem.setItemStack(finalReward);
                    Component winName = finalReward.getItemMeta().displayName();
                    rouletteName.text(plugin.mm().deserialize("<gold><bold>★ VÝHRA: </bold></gold>")
                            .append(winName != null ? winName : Component.text(finalReward.getType().name())));

                    Transformation winTrans = rouletteItem.getTransformation();
                    winTrans.getScale().set(1.15f, 1.15f, 1.15f);
                    rouletteItem.setInterpolationDelay(0);
                    rouletteItem.setInterpolationDuration(6);
                    rouletteItem.setTransformation(winTrans);

                    world.playSound(crateLoc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, rouletteItem.getLocation(), 20, 0.4, 0.4, 0.4);
                }

                // FÁZE 6: Výhra letí přímo k hráči
                if (tick == 112 && rouletteItem != null) {
                    rouletteName.remove();
                    Location target = player.isOnline() ? player.getLocation().add(0, 1.1, 0) : crateLoc.clone().add(0, 0.5, 0);
                    rouletteItem.setInterpolationDelay(0);
                    rouletteItem.setInterpolationDuration(12);
                    rouletteItem.teleport(target);
                }

                // FÁZE 7: Předání výhry, zavření víka a úklid
                if (tick == 124) {
                    if (rouletteItem != null) rouletteItem.remove();
                    if (player.isOnline()) {
                        player.getInventory().addItem(finalReward).values()
                                .forEach(drop -> world.dropItemNaturally(player.getLocation(), drop));
                        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    }

                    if (crate.lidDisplay != null) {
                        Transformation lidTrans = crate.lidDisplay.getTransformation();
                        lidTrans.getLeftRotation().set(new AxisAngle4f(0, 1, 0, 0));
                        crate.lidDisplay.setInterpolationDelay(0);
                        crate.lidDisplay.setInterpolationDuration(10);
                        crate.lidDisplay.setTransformation(lidTrans);
                        world.playSound(crateLoc, Sound.BLOCK_CHEST_CLOSE, 1f, 1f);
                    }

                    if (crate.hologramDisplay != null) {
                        crate.hologramDisplay.setViewRange(1.0f);
                    }

                    plugin.getCrateManager().setBusy(crate.id, false);
                    this.cancel();
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
