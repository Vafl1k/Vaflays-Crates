package cz.vaflays.crates;

import cz.vaflays.crates.commands.CrateCommand;
import cz.vaflays.crates.listeners.CrateInteractListener;
import cz.vaflays.crates.manager.CrateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public class VaflaysCratesPlugin extends JavaPlugin {

    private CrateManager crateManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.crateManager = new CrateManager(this);
        this.crateManager.loadCratesFromConfig();

        CrateCommand cmd = new CrateCommand(this);
        if (getCommand("vcrate") != null) {
            getCommand("vcrate").setExecutor(cmd);
            getCommand("vcrate").setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new CrateInteractListener(this), this);
        getLogger().info("VaflaysCrates uspesne zapnut na Paper 1.21.11!");
    }

    @Override
    public void onDisable() {
        if (crateManager != null) {
            crateManager.despawnAllEntities();
        }
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public Component formatMsg(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String msg = getConfig().getString("messages." + path, path);
        return miniMessage.deserialize(prefix + msg);
    }

    public MiniMessage mm() {
        return miniMessage;
    }
}
