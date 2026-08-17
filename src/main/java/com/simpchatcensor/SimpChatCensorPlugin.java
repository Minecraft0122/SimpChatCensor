package com.simpchatcensor;

import com.simpchatcensor.command.BwfCommand;
import com.simpchatcensor.config.ConfigManager;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.listener.ChatListener;
import com.simpchatcensor.listener.CommandListener;
import com.simpchatcensor.listener.ConnectionListener;
import com.simpchatcensor.util.ViolationLogger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SimpChatCensorPlugin extends JavaPlugin {
    private static final String USAGE_GUIDE_RESOURCE = "USAGE.md";

    private ConfigManager configManager;
    private BannedWordManager bannedWordManager;
    private ViolationLogger violationLogger;
    private ActionExecutor actionExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveUsageGuide();

        this.configManager = new ConfigManager(this);
        this.bannedWordManager = new BannedWordManager(getLogger());
        this.violationLogger = new ViolationLogger(getLogger());
        this.actionExecutor = new ActionExecutor(
                this,
                () -> configManager.getConfig(),
                bannedWordManager,
                violationLogger
        );

        reloadPlugin();
        registerListeners();
        registerCommands();
        getLogger().info("SimpChatCensor enabled.");
    }

    @Override
    public void onDisable() {
        if (bannedWordManager != null) {
            bannedWordManager.close();
        }
    }

    public void reloadPlugin() {
        configManager.reload();
        bannedWordManager.reload(configManager.getConfig());
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public BannedWordManager bannedWordManager() {
        return bannedWordManager;
    }

    private void saveUsageGuide() {
        File usageGuide = new File(getDataFolder(), USAGE_GUIDE_RESOURCE);
        if (!usageGuide.isFile()) {
            saveResource(USAGE_GUIDE_RESOURCE, false);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new ChatListener(bannedWordManager, actionExecutor),
                this
        );
        getServer().getPluginManager().registerEvents(
                new CommandListener(() -> configManager.getConfig(), bannedWordManager, actionExecutor),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ConnectionListener(bannedWordManager, actionExecutor),
                this
        );
    }

    private void registerCommands() {
        PluginCommand command = getCommand("bwf");
        if (command == null) {
            getLogger().warning("Command /bwf is not defined in plugin.yml.");
            return;
        }
        BwfCommand executor = new BwfCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
