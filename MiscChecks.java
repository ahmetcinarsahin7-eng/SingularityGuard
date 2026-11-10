package dev.singularityguard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.Locale;

/** ChatSpam, CommandSpam, Sprint, InvSpeed */
public class MiscChecks implements Listener {
    private final SingularityGuard pl;

    public MiscChecks(SingularityGuard pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("ChatSpam") || p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        String msg = e.getMessage().toLowerCase(Locale.ROOT).trim();
        String why = null;

        synchronized (d.chatTimes) {
            PlayerData.trim(d.chatTimes, now, (long) (pl.num("ChatSpam", "window-seconds", 5) * 1000));
            if (d.chatTimes.size() >= (int) pl.num("ChatSpam", "max-messages", 4)) why = "rate";
            else if (!d.chatTimes.isEmpty() && now - d.chatTimes.peekLast() < pl.num("ChatSpam", "min-delay-ms", 350)) why = "too fast";
            else if (msg.equals(d.lastMessage) && now - d.lastMessageTime < 10_000) why = "repeat";
            if (why == null) {
                d.chatTimes.addLast(now);
                d.lastMessage = msg;
                d.lastMessageTime = now;
            }
        }
        if (why != null) {
            e.setCancelled(true);
            pl.violations().flag(p, "ChatSpam", why, 1);
            p.sendMessage(pl.color(pl.getConfig().getString("prefix", "") + "&cYavaş ol, spam yapma!"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("CommandSpam") || p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        PlayerData.trim(d.cmdTimes, now, (long) (pl.num("CommandSpam", "window-seconds", 3) * 1000));
        if (d.cmdTimes.size() >= (int) pl.num("CommandSpam", "max-commands", 6)) {
            e.setCancelled(true);
            pl.violations().flag(p, "CommandSpam", "commands=" + d.cmdTimes.size(), 1);
            return;
        }
        d.cmdTimes.addLast(now);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("Sprint") || p.hasPermission("singularityguard.bypass") || !e.isSprinting()) return;
        boolean blind = Util.effect(p, "BLINDNESS") >= 0;
        if (p.getFoodLevel() <= 6 || blind) {
            e.setCancelled(true);
            pl.violations().flag(p, "Sprint", blind ? "sprint while blind" : "sprint with food=" + p.getFoodLevel(), 1.5);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!pl.enabled("InvSpeed") || p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        d.invClicks.addLast(now);
        PlayerData.trim(d.invClicks, now, 1000);
        if (d.invClicks.size() > pl.num("InvSpeed", "max-clicks-per-second", 30)) {
            e.setCancelled(true);
            pl.violations().flag(p, "InvSpeed", "clicks/s=" + d.invClicks.size(), 1.5);
        }
    }
}
