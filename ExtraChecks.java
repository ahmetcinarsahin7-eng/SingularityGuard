package dev.singularityguard;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** BoatFly, ElytraSpeed, FastLadder, FastEat, FastBow, FastHeal, JoinFlood, DropSpam, EntityReach, Interact */
public class ExtraChecks implements Listener {
    private final SingularityGuard pl;
    private final Map<String, Deque<Long>> joins = new ConcurrentHashMap<>();

    public ExtraChecks(SingularityGuard pl) { this.pl = pl; }

    private boolean basicExempt(Player p, PlayerData d, long now) {
        if (p.hasPermission("singularityguard.bypass")) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.getAllowFlight() || p.isDead()) return true;
        if (now - d.lastTeleport < 1500 || now - d.lastVelocity < 1500 || now - d.lastJoin < 3000) return true;
        return pl.isLagging(p);
    }

    // ---------- BoatFly ----------
    @EventHandler(ignoreCancelled = true)
    public void onVehicle(VehicleMoveEvent e) {
        if (!(e.getVehicle() instanceof Boat boat) || !pl.enabled("BoatFly")) return;
        for (Entity en : boat.getPassengers()) {
            if (!(en instanceof Player p) || p.hasPermission("singularityguard.bypass")) continue;
            PlayerData d = pl.data(p);
            Location l = e.getTo();
            double dy = l.getY() - e.getFrom().getY();
            boolean water = l.getBlock().isLiquid() || l.clone().subtract(0, 0.3, 0).getBlock().isLiquid();
            boolean bad = dy > 0.1 && !water && !Util.onGround(l);
            if (d.buffer("BoatFly", bad, 1, 0.5, 4)) {
                pl.violations().flag(p, "BoatFly", String.format("dy=%.2f out of water", dy), 2);
                boat.eject();
            }
        }
    }

    // ---------- ElytraSpeed + FastLadder ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        if (to == null || !to.getWorld().equals(e.getFrom().getWorld())) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        if (basicExempt(p, d, now)) return;
        double dy = to.getY() - e.getFrom().getY();
        double h = Math.hypot(to.getX() - e.getFrom().getX(), to.getZ() - e.getFrom().getZ());

        if (pl.enabled("ElytraSpeed") && p.isGliding() && now - d.lastBoost > 7000
                && d.buffer("Elytra", h > pl.num("ElytraSpeed", "max-speed", 4.5), 1, 0.5, 2)) {
            pl.violations().flag(p, "ElytraSpeed", String.format("h=%.2f", h), 2);
        }

        if (pl.enabled("FastLadder") && !p.isInsideVehicle() && Util.effect(p, "LEVITATION") < 0
                && Util.onClimbable(to) && !Util.inLiquid(to)) {
            if (d.buffer("Ladder", dy > 0.26, 1, 0.5, 3)) {
                pl.violations().flag(p, "FastLadder", String.format("dy=%.2f", dy), 2);
            }
        }
    }

    @EventHandler
    public void onBoost(PlayerElytraBoostEvent e) { pl.data(e.getPlayer()).lastBoost = System.currentTimeMillis(); }

    // ---------- Kullanım başlangıcı (FastEat / FastBow) + Interact ----------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        Action a = e.getAction();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && !p.isHandRaised()) {
            d.useStart = System.currentTimeMillis();
        }
        if (!pl.enabled("Interact") || e.getClickedBlock() == null
                || (a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK)
                || p.getGameMode() == GameMode.SPECTATOR) return;

        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), 6.5, FluidCollisionMode.NEVER, false);
        boolean mismatch = r == null || r.getHitBlock() == null || !r.getHitBlock().equals(e.getClickedBlock());
        if (d.buffer("Interact", mismatch, 1, 0.5, 3)) {
            pl.violations().flag(p, "Interact", "clicked block not in line of sight", 2);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("FastEat") || p.hasPermission("singularityguard.bypass")) return;
        if (e.getItem().getType() == Material.DRIED_KELP) return;
        PlayerData d = pl.data(p);
        long elapsed = System.currentTimeMillis() - d.useStart;
        if (d.useStart > 0 && elapsed < 1000 && !pl.isLagging(p)) {
            pl.violations().flag(p, "FastEat", elapsed + "ms", 2);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !pl.enabled("FastBow") || p.hasPermission("singularityguard.bypass")) return;
        if (e.getBow() != null && e.getBow().getType() != Material.BOW) return;
        PlayerData d = pl.data(p);
        long elapsed = System.currentTimeMillis() - d.useStart;
        if (d.useStart > 0 && e.getForce() > 0.9 && elapsed < 700 && !pl.isLagging(p)) {
            pl.violations().flag(p, "FastBow", String.format("force=%.2f in %dms", e.getForce(), elapsed), 2);
            e.setCancelled(true);
        }
    }

    // ---------- FastHeal ----------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !pl.enabled("FastHeal") || p.hasPermission("singularityguard.bypass")) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r != EntityRegainHealthEvent.RegainReason.SATIATED && r != EntityRegainHealthEvent.RegainReason.REGEN) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        if (d.lastRegen > 0 && now - d.lastRegen < 400 && !pl.isLagging(p)) {
            pl.violations().flag(p, "FastHeal", (now - d.lastRegen) + "ms between regen", 2);
            e.setCancelled(true);
        }
        d.lastRegen = now;
    }

    // ---------- EntityReach ----------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("EntityReach") || p.hasPermission("singularityguard.bypass") || p.getGameMode() == GameMode.SPECTATOR) return;
        Location eye = p.getEyeLocation();
        Vector near = Util.closest(e.getRightClicked().getBoundingBox(), eye);
        double dist = eye.toVector().distance(near);
        if (dist > pl.num("EntityReach", "max-distance", 4.8)) {
            pl.violations().flag(p, "EntityReach", String.format("%.2f", dist), 2);
            e.setCancelled(true);
        }
    }

    // ---------- DropSpam ----------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!pl.enabled("DropSpam") || p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();
        d.dropTimes.addLast(now);
        PlayerData.trim(d.dropTimes, now, 1000);
        if (d.dropTimes.size() > pl.num("DropSpam", "max-per-second", 15)) {
            pl.violations().flag(p, "DropSpam", "drops/s=" + d.dropTimes.size(), 1.5);
            e.setCancelled(true);
        }
    }

    // ---------- JoinFlood (bot koruması) ----------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent e) {
        if (!pl.enabled("JoinFlood")) return;
        String ip = e.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        if (joins.size() > 2000) joins.clear();
        Deque<Long> q = joins.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            PlayerData.trim(q, now, 10_000);
            if (q.size() >= (int) pl.num("JoinFlood", "max-per-10s", 5)) {
                e.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Çok hızlı giriş denemesi. Biraz bekleyin.");
                pl.getLogger().warning("JoinFlood engellendi: " + ip);
                return;
            }
            q.addLast(now);
        }
    }
}
