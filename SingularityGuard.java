package dev.singularityguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SingularityGuard extends JavaPlugin {

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();
    private ViolationManager violations;
    private volatile long lastTick = System.currentTimeMillis();
    private volatile long lastLagSpike = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        violations = new ViolationManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DataListener(this), this);
        pm.registerEvents(new MovementChecks(this), this);
        pm.registerEvents(new CombatChecks(this), this);
        pm.registerEvents(new WorldChecks(this), this);
        pm.registerEvents(new MiscChecks(this), this);
        pm.registerEvents(new ExtraChecks(this), this);

        SingularityGuardCommand cmd = new SingularityGuardCommand(this);
        getCommand("singularityguard").setExecutor(cmd);
        getCommand("singularityguard").setTabCompleter(cmd);

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            d.lastSafe = p.getLocation().clone();
        }

        // Sunucu lag spike izleyici (Timer vb. false-positive'leri önler)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long n = System.currentTimeMillis();
            if (n - lastTick > 200) lastLagSpike = n;
            lastTick = n;
        }, 1L, 1L);

        violations.startDecay();
        getLogger().info("SingularityGuard AntiCheat aktif.");
    }

    public PlayerData data(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), PlayerData::new);
    }

    public void removeData(UUID id) { players.remove(id); }
    public Collection<PlayerData> allData() { return players.values(); }
    public ViolationManager violations() { return violations; }
    public Set<UUID> alertsDisabled() { return alertsDisabled; }

    public boolean enabled(String check) {
        return getConfig().getBoolean("checks." + check + ".enabled", true);
    }

    public double num(String check, String key, double def) {
        return getConfig().getDouble("checks." + check + "." + key, def);
    }

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public boolean isLagging(Player p) {
        if (System.currentTimeMillis() - lastLagSpike < 2500) return true;
        try {
            if (Bukkit.getTPS()[0] < getConfig().getDouble("lag-compensation.min-tps", 17.0)) return true;
        } catch (Throwable ignored) { }
        return p.getPing() > getConfig().getDouble("lag-compensation.max-ping", 500);
    }
}
