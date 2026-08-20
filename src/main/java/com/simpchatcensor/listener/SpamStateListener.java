package com.simpchatcensor.listener;

import com.simpchatcensor.detection.SpamDetector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SpamStateListener implements Listener {
    private final SpamDetector spamDetector;

    public SpamStateListener(SpamDetector spamDetector) {
        this.spamDetector = spamDetector;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        spamDetector.clear(event.getPlayer().getUniqueId());
    }
}
