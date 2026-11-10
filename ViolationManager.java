package dev.singularityguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ViolationManager {
    private final SingularityGuard pl;
    private final File logFile;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ViolationManager(SingularityGuard pl) {
        this.pl = pl;
        pl.getDataFolder().mkdirs();
        this.logFile = new File(pl.getDataFolder(), "violations.log");
    }

    public void flag(Player p, String check, String info, double add) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(pl, () -> flag(p, check, info, add));
            return;
        }
        if (!p.isOnline() || !pl.enabled(check)) return;

        FileConfiguration cfg = pl.getConfig();
        PlayerData d = pl.data(p);
        double vl = d.vl.merge(check, add, Double::sum);

        String msg = pl.color(cfg.getString("alerts.format", "%prefix%&f%player% &7failed &c%check% &8(&7%info%&8) &7VL: &c%vl%")
                .replace("%prefix%", cfg.getString("prefix", ""))
                .replace("%player%", p.getName())
                .replace("%check%", check)
                .replace("%info%", info)
                .replace("%vl%", String.format("%.1f", vl))
                .replace("%ping%", String.valueOf(p.getPing())));

        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("singularityguard.alerts") && !pl.alertsDisabled().contains(s.getUniqueId())) s.sendMessage(msg);
        }
        if (cfg.getBoolean("alerts.console", true)) Bukkit.getConsoleSender().sendMessage(msg);

        String plain = ChatColor.stripColor(msg);
        if (cfg.getBoolean("logging", true)) log(plain);
        webhook(plain);

        double max = pl.num(check, "max-vl", cfg.getDouble("default-max-vl", 15));
        if (vl >= max) {
            d.vl.put(check, 0.0);
            List<String> cmds = cfg.isList("checks." + check + ".commands")
                    ? cfg.getStringList("checks." + check + ".commands")
                    : cfg.getStringList("punishments.default-commands");
            for (String c : cmds) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c
                        .replace("%player%", p.getName())
                        .replace("%check%", check)
                        .replace("%vl%", String.format("%.1f", vl))
                        .replace("%ping%", String.valueOf(p.getPing())));
            }
        }
    }

    private void log(String line) {
        String out = "[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator();
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try {
                synchronized (logFile) {
                    Files.write(logFile.toPath(), out.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (Exception ignored) { }
        });
    }

    private void webhook(String content) {
        FileConfiguration cfg = pl.getConfig();
        if (!cfg.getBoolean("discord.enabled", false)) return;
        String url = cfg.getString("discord.webhook-url", "");
        if (url == null || url.isEmpty()) return;
        String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"}";
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "SingularityGuard-AntiCheat");
                c.setDoOutput(true);
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                try (OutputStream os = c.getOutputStream()) { os.write(json.getBytes(StandardCharsets.UTF_8)); }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) { }
        });
    }

    public void startDecay() {
        long ticks = Math.max(1, pl.getConfig().getLong("vl-decay.interval-seconds", 30)) * 20L;
        double amt = pl.getConfig().getDouble("vl-decay.amount", 1.0);
        Bukkit.getScheduler().runTaskTimer(pl, () ->
                pl.allData().forEach(d -> d.vl.replaceAll((k, v) -> Math.max(0, v - amt))), ticks, ticks);
    }
}
