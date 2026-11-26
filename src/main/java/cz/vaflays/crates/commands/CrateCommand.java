package cz.vaflays.crates.commands;

import cz.vaflays.crates.VaflaysCratesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private final VaflaysCratesPlugin plugin;

    public CrateCommand(VaflaysCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vaflayscrates.admin")) {
            sender.sendMessage(plugin.formatMsg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.mm().deserialize("<gold><bold>VaflaysCrates Příkazy:</bold></gold>"));
            sender.sendMessage(plugin.mm().deserialize("<yellow>/vcrate set <typ></yellow> <gray>- Položí 3D bednu na blok, na který koukáš</gray>"));
            sender.sendMessage(plugin.mm().deserialize("<yellow>/vcrate remove</yellow> <gray>- Odstraní bednu, na kterou koukáš</gray>"));
            sender.sendMessage(plugin.mm().deserialize("<yellow>/vcrate givekey <hráč> <typ> [počet]</yellow> <gray>- Dá hráči 3D klíč</gray>"));
            sender.sendMessage(plugin.mm().deserialize("<yellow>/vcrate reload</yellow> <gray>- Znovu načte config.yml</gray>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (!(sender instanceof Player player)) return true;
                String crateType = args.length >= 2 ? args[1].toLowerCase() : "legendary";
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    player.sendMessage(plugin.mm().deserialize("<red>Musíš koukat na nějaký blok (do 5 bloků)!"));
                    return true;
                }
                plugin.getCrateManager().placeCrate(target, crateType, player.getLocation().getYaw());
                String msg = plugin.getConfig().getString("messages.crate-set", "").replace("{crate}", crateType);
                player.sendMessage(plugin.mm().deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) return true;
                Block target = player.getTargetBlockExact(5);
                if (target == null || plugin.getCrateManager().getCrateAt(target) == null) {
                    player.sendMessage(plugin.mm().deserialize("<red>Nekoukáš na žádnou Vaflays bednu!"));
                    return true;
                }
                plugin.getCrateManager().removeCrate(target);
                player.sendMessage(plugin.formatMsg("crate-removed"));
            }
            case "givekey" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.mm().deserialize("<red>Použití: /vcrate givekey <hráč> <typ> [počet]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.mm().deserialize("<red>Hráč není online!"));
                    return true;
                }
                String crateType = args[2].toLowerCase();
                int amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;

                ItemStack key = plugin.getCrateManager().createCrateKey(crateType, amount);
                target.getInventory().addItem(key);

                String msg = plugin.getConfig().getString("messages.key-given", "")
                        .replace("{player}", target.getName())
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{crate}", crateType);
                sender.sendMessage(plugin.mm().deserialize(plugin.getConfig().getString("messages.prefix", "") + msg));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getCrateManager().loadCratesFromConfig();
                sender.sendMessage(plugin.formatMsg("reloaded"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.addAll(List.of("set", "remove", "givekey", "reload"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) list.add("legendary");
            if (args[0].equalsIgnoreCase("givekey")) {
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("givekey")) {
            list.add("legendary");
        }
        return list;
    }
}
