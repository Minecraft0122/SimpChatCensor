package com.simpchatcensor;

import com.simpchatcensor.command.SccCommand;
import com.simpchatcensor.config.ConfigManager;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.SpamDetector;
import com.simpchatcensor.listener.ChatListener;
import com.simpchatcensor.listener.CommandListener;
import com.simpchatcensor.listener.ConnectionListener;
import com.simpchatcensor.listener.SpamStateListener;
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
    private SpamDetector spamDetector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveUsageGuide();

        this.configManager = new ConfigManager(this);
        this.bannedWordManager = new BannedWordManager(getLogger());
        this.spamDetector = new SpamDetector();
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
        if (spamDetector != null) {
            spamDetector.close();
        }
    }

    public void reloadPlugin() {
        configManager.reload();
        var snapshot = configManager.getConfig();
        bannedWordManager.reload(snapshot);
        spamDetector.reload(snapshot);
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
                new ChatListener(bannedWordManager, spamDetector, actionExecutor),
                this
        );
        getServer().getPluginManager().registerEvents(
                new CommandListener(() -> configManager.getConfig(), bannedWordManager, spamDetector, actionExecutor),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ConnectionListener(bannedWordManager, actionExecutor),
                this
        );
        getServer().getPluginManager().registerEvents(
                new SpamStateListener(spamDetector),
                this
        );
    }

    private void registerCommands() {
        PluginCommand command = getCommand("scc");
        if (command == null) {
            getLogger().warning("Command /scc is not defined in plugin.yml.");
            return;
        }
        SccCommand executor = new SccCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
