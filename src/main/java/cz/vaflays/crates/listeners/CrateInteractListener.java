package cz.vaflays.crates.listeners;

import cz.vaflays.crates.VaflaysCratesPlugin;
import cz.vaflays.crates.animation.CrateOpenAnimation;
import cz.vaflays.crates.manager.CrateManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CrateInteractListener implements Listener {

    private final VaflaysCratesPlugin plugin;
    private static final String PREVIEW_TITLE = "Náhled odměn bedny";

    public CrateInteractListener(VaflaysCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCrateInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        CrateManager manager = plugin.getCrateManager();
        CrateManager.ActiveCrate crate = manager.getCrateAt(clicked);
        if (crate == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // LEVÝ KLIK -> Otevření bedny pomocí 3D klíče
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (manager.isBusy(crate.id)) {
                player.sendMessage(plugin.formatMsg("already-opening"));
                return;
            }

            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (!manager.isValidKey(inHand, crate.crateType)) {
                player.sendMessage(plugin.formatMsg("need-key"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            List<CrateManager.RewardEntry> rewards = manager.getRewards(crate.crateType);
            if (rewards.isEmpty()) return;

            // Odebrání 1 klíče
            inHand.setAmount(inHand.getAmount() - 1);

            // Spuštění filmové animace
            new CrateOpenAnimation(plugin, player, crate, rewards).start();
        }
        // PRAVÝ KLIK -> Náhled všech možných odměn a šancí
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            openPreviewGui(player, crate.crateType);
        }
    }

    private void openPreviewGui(Player player, String crateType) {
        List<CrateManager.RewardEntry> rewards = plugin.getCrateManager().getRewards(crateType);
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(PREVIEW_TITLE));

        int slot = 10;
        for (CrateManager.RewardEntry entry : rewards) {
            if (slot > 16) break;
            ItemStack display = entry.item.clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(plugin.mm().deserialize("<gray>Šance na výhru: <gold><bold>" + entry.chance + "%</bold></gold>"));
            meta.lore(lore);
            display.setItemMeta(meta);

            inv.setItem(slot++, display);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        if (event.getView().title().equals(Component.text(PREVIEW_TITLE))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCrateBreak(BlockBreakEvent event) {
        if (plugin.getCrateManager().getCrateAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
