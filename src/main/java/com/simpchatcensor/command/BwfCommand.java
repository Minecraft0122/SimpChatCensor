package com.simpchatcensor.command;

import com.simpchatcensor.SimpChatCensorPlugin;
import com.simpchatcensor.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BwfCommand implements CommandExecutor, TabCompleter {
    private final SimpChatCensorPlugin plugin;

    public BwfCommand(SimpChatCensorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simpchatcensor.admin")) {
            send(sender, "&c你没有权限使用这个命令。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                plugin.reloadPlugin();
                send(sender, "&aSimpChatCensor 配置已重载。");
                return true;
            }
            case "add" -> {
                String word = joinTail(args, 1);
                if (word.isBlank()) {
                    send(sender, "&c用法: /" + label + " add <词/IP/域名>");
                    return true;
                }
                if (plugin.configManager().addBannedWord(word)) {
                    plugin.reloadPlugin();
                    send(sender, "&a已添加: &f" + word);
                } else {
                    send(sender, "&e该条目已存在或为空。");
                }
                return true;
            }
            case "remove" -> {
                String word = joinTail(args, 1);
                if (word.isBlank()) {
                    send(sender, "&c用法: /" + label + " remove <词/IP/域名>");
                    return true;
                }
                if (plugin.configManager().removeBannedWord(word)) {
                    plugin.reloadPlugin();
                    send(sender, "&a已删除: &f" + word);
                } else {
                    send(sender, "&e没有找到该条目。");
                }
                return true;
            }
            case "list" -> {
                sendList(sender);
                return true;
            }
            case "geoip" -> {
                return handleGeoIp(sender, label, args);
            }
            case "whitelist" -> {
                return handleWhitelist(sender, label, args);
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simpchatcensor.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("reload", "add", "remove", "list", "geoip", "whitelist"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("geoip")) {
            return filter(List.of("reload", "update"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return filter(List.of("add"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return filter(plugin.bannedWordManager().bannedWords(), args[1]);
        }
        return List.of();
    }

    private boolean handleGeoIp(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("reload") && !args[1].equalsIgnoreCase("update"))) {
            send(sender, "&c用法: /" + label + " geoip <reload|update>");
            return true;
        }

        plugin.reloadPlugin();
        if (args[1].equalsIgnoreCase("update")) {
            send(sender, "&eGeoIP 在线更新需要 MaxMind 授权密钥；请替换数据库文件后已为你执行重载。");
        } else {
            send(sender, "&aGeoIP 数据库已重新加载。");
        }
        return true;
    }

    private boolean handleWhitelist(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("add")) {
            send(sender, "&c用法: /" + label + " whitelist add <IP/域名>");
            return true;
        }

        String entry = joinTail(args, 2);
        if (plugin.configManager().addWhitelistEntry(entry)) {
            plugin.reloadPlugin();
            send(sender, "&a已加入白名单: &f" + entry);
        } else {
            send(sender, "&e该白名单条目已存在或为空。");
        }
        return true;
    }

    private void sendList(CommandSender sender) {
        List<String> words = plugin.bannedWordManager().bannedWords();
        send(sender, "&a当前违禁词数量: &f" + words.size());
        if (words.isEmpty()) {
            return;
        }

        int pageSize = 12;
        for (int start = 0; start < words.size(); start += pageSize) {
            int end = Math.min(start + pageSize, words.size());
            send(sender, "&7- &f" + String.join("&7, &f", words.subList(start, end)));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        send(sender, "&eSimpChatCensor 命令:");
        send(sender, "&7/" + label + " reload &f- 重载配置");
        send(sender, "&7/" + label + " add <词> &f- 添加违禁词/IP/域名");
        send(sender, "&7/" + label + " remove <词> &f- 删除违禁词");
        send(sender, "&7/" + label + " list &f- 显示词库");
        send(sender, "&7/" + label + " geoip reload &f- 重载 GeoIP 数据库");
        send(sender, "&7/" + label + " whitelist add <IP/域名> &f- 添加白名单");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtil.component(message, Map.of()));
    }

    private static String joinTail(String[] args, int start) {
        if (start >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
    }

    private static List<String> filter(List<String> values, String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                output.add(value);
            }
        }
        return output;
    }
}
