package com.simpchatcensor.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerUtil {
    private SchedulerUtil() {
    }

    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (tryRunEntityScheduler(plugin, player, task)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (tryRunGlobalScheduler(plugin, task)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static boolean tryRunEntityScheduler(Plugin plugin, Player player, Runnable task) {
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(player);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            Consumer<Object> consumer = ignored -> task.run();
            run.invoke(scheduler, plugin, consumer, null);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean tryRunGlobalScheduler(Plugin plugin, Runnable task) {
        try {
            Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = getScheduler.invoke(null);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            Consumer<Object> consumer = ignored -> task.run();
            run.invoke(scheduler, plugin, consumer);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return false;
        }
    }
}
