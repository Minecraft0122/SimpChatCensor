package com.simpchatcensor;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import com.simpchatcensor.detection.DetectionType;
import com.simpchatcensor.util.ColorUtil;
import com.simpchatcensor.util.SchedulerUtil;
import com.simpchatcensor.util.ViolationLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ActionExecutor {
    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final BannedWordManager bannedWordManager;
    private final ViolationLogger violationLogger;

    public ActionExecutor(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            BannedWordManager bannedWordManager,
            ViolationLogger violationLogger
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.bannedWordManager = bannedWordManager;
        this.violationLogger = violationLogger;
    }

    public boolean applyTextAction(
            Player player,
            String originalText,
            DetectionResult result,
            Consumer<String> replaceText,
            Runnable cancel,
            boolean allowFilter
    ) {
        return applyTextAction(player, originalText, result, replaceText, cancel, allowFilter, "CHAT");
    }

    public boolean applyTextAction(
            Player player,
            String originalText,
            DetectionResult result,
            Consumer<String> replaceText,
            Runnable cancel,
            boolean allowFilter,
            String source
    ) {
        PluginConfig config = configSupplier.get();
        record(player, result, originalText, source);

        DetectionAction action = result.action() == null ? config.defaultAction() : result.action();
        return switch (action) {
            case WARN -> {
                warn(player, config, result);
                yield false;
            }
            case FILTER -> {
                String filtered = bannedWordManager.censor(originalText, result);
                if (allowFilter && !filtered.equals("[内容已过滤]")) {
                    replaceText.accept(filtered);
                    yield false;
                }
                cancel.run();
                warn(player, config, result);
                yield true;
            }
            case KICK -> {
                cancel.run();
                kick(player, config);
                yield true;
            }
            case CANCEL -> {
                cancel.run();
                warn(player, config, result);
                yield true;
            }
        };
    }

    public void record(Player player, DetectionResult result, String originalText) {
        record(player, result, originalText, "CHAT");
    }

    public void record(Player player, DetectionResult result, String originalText, String source) {
        PluginConfig config = configSupplier.get();
        violationLogger.log(config, player, result, originalText, source);
        if (!config.notifyStaff()) {
            return;
        }

        SchedulerUtil.runGlobal(plugin, () -> {
            Component message = ColorUtil.component(config.staffMessage(), placeholders(player, result));
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("simpchatcensor.notify")) {
                    onlinePlayer.sendMessage(message);
                }
            }
        });
    }

    public void warn(Player player, PluginConfig config) {
        warn(player, config, null);
    }

    private void warn(Player player, PluginConfig config, DetectionResult result) {
        SchedulerUtil.runForPlayer(plugin, player, () ->
                player.sendMessage(ColorUtil.component(warningTemplate(config, result), Map.of()))
        );
    }

    static String warningTemplate(PluginConfig config, DetectionResult result) {
        return result != null && result.type() == DetectionType.SPAM
                ? config.spamWarning()
                : config.playerWarning();
    }

    public void kick(Player player, PluginConfig config) {
        SchedulerUtil.runForPlayer(plugin, player, () ->
                player.kick(ColorUtil.component(config.kickMessage(), Map.of()))
        );
    }

    public String loginKickMessage(Player player, DetectionResult result) {
        PluginConfig config = configSupplier.get();
        return ColorUtil.legacyString(config.kickMessage(), placeholders(player, result));
    }

    private static Map<String, String> placeholders(Player player, DetectionResult result) {
        return Map.of(
                "player", player.getName(),
                "type", result.type().key(),
                "match", result.detectedText(),
                "rule", result.rule(),
                "detail", result.detail()
        );
    }
}
