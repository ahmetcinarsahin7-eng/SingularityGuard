package dev.singularityguard;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Reach, KillAura (angle/multi/wall), AutoClicker, Criticals, Velocity */
public class CombatChecks implements Listener {
    private final SingularityGuard pl;

    public CombatChecks(SingularityGuard pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (p.hasPermission("singularityguard.bypass")) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        Entity target = e.getEntity();
        PlayerData d = pl.data(p);
        ViolationManager vm = pl.violations();
        long now = System.currentTimeMillis();
        boolean cancelCfg = pl.getConfig().getBoolean("cancel-hits", true);
        boolean cancel = false;

        Location eye = p.getEyeLocation();
        Vector near = Util.closest(target.getBoundingBox(), eye);
        double dist = eye.toVector().distance(near);

        // ---------- Reach ----------
        if (pl.enabled("Reach")) {
            double max = pl.num("Reach", "max-distance", 3.6);
            if (p.getPing() > 150) max += 0.3;
            if (d.buffer("Reach", dist > max, 1, 0.25, 2)) {
                vm.flag(p, "Reach", String.format("%.2f > %.2f", dist, max), 2);
                cancel = true;
            } else if (dist > max + 0.8) {
                cancel = true;
            }
        }

        // ---------- KillAura: bakış açısı ----------
        if (pl.enabled("KillAura") && dist > 1.5) {
            Vector toT = target.getBoundingBox().getCenter().subtract(eye.toVector());
            double ang = Math.toDegrees(eye.getDirection().angle(toT));
            if (d.buffer("KA.angle", ang > pl.num("KillAura", "max-angle", 65), 1, 0.5, 3)) {
                vm.flag(p, "KillAura", String.format("angle=%.0f", ang), 2);
                cancel = true;
            }
        }

        // ---------- KillAura: multi-target ----------
        if (pl.enabled("KillAura")) {
            d.recentTargets.put(target.getUniqueId(), now);
            d.recentTargets.values().removeIf(t -> now - t > 700);
            if (d.recentTargets.size() >= 3) {
                vm.flag(p, "KillAura", "multi-target x" + d.recentTargets.size(), 3);
                d.recentTargets.clear();
                cancel = true;
            }
        }

        // ---------- KillAura: duvar arkası vuruş ----------
        if (pl.enabled("KillAura") && dist > 0.8) {
            Vector dir = near.clone().subtract(eye.toVector()).normalize();
            RayTraceResult r = p.getWorld().rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
            if (r != null && r.getHitBlock() != null
                    && r.getHitPosition().distance(eye.toVector()) < dist - 0.25
                    && d.buffer("KA.wall", true, 1, 0.5, 1)) {
                vm.flag(p, "KillAura", "hit through " + r.getHitBlock().getType(), 3);
                cancel = true;
            }
        }

        // ---------- Criticals ----------
        if (pl.enabled("Criticals") && p.getFallDistance() > 0.0f && !p.isInsideVehicle()
                && now - d.lastAirMove > 500 && now - d.lastTeleport > 2000
                && Util.onGround(p.getLocation()) && !Util.inLiquid(p.getLocation())) {
            if (d.buffer("Crit", true, 1, 0.5, 1)) {
                vm.flag(p, "Criticals", "fakeFall=" + String.format("%.2f", p.getFallDistance()), 2);
                cancel = true;
            }
        }

        if (cancel && cancelCfg) e.setCancelled(true);
    }

    // ---------- AutoClicker ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        if (!pl.enabled("AutoClicker") || p.hasPermission("singularityguard.bypass")) return;
        PlayerData d = pl.data(p);
        long now = System.currentTimeMillis();

        // Blok kırarken/yerleştirirken oluşan swing'leri sayma
        if (p.getTargetBlockExact(5) != null) { d.lastClick = now; return; }

        d.clicks.addLast(now);
        PlayerData.trim(d.clicks, now, 1000);
        int cps = d.clicks.size();
        double maxCps = pl.num("AutoClicker", "max-cps", 20);
        if (cps > maxCps) {
            pl.violations().flag(p, "AutoClicker", "cps=" + cps, 1.5);
        }

        if (d.lastClick > 0 && now - d.lastClick < 1000) {
            d.clickDelays.addLast((double) (now - d.lastClick));
            if (d.clickDelays.size() > 40) d.clickDelays.pollFirst();
            if (d.clickDelays.size() >= 30) {
                double mean = d.clickDelays.stream().mapToDouble(Double::doubleValue).average().orElse(1000);
                double var = d.clickDelays.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0);
                double std = Math.sqrt(var);
                if (mean < 200 && std < pl.num("AutoClicker", "min-std", 12)) {
                    pl.violations().flag(p, "AutoClicker", String.format("robotic std=%.1fms", std), 2);
                    d.clickDelays.clear();
                }
            }
        }
        d.lastClick = now;
    }

    // ---------- Velocity (Anti-KB) ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = pl.data(p);
        long t0 = System.currentTimeMillis();
        d.lastVelocity = t0;
        if (!pl.enabled("Velocity") || p.hasPermission("singularityguard.bypass")) return;

        Vector v = e.getVelocity();
        double vh = Math.hypot(v.getX(), v.getZ());
        double vy = v.getY();
        if (vh < 0.25 && vy < 0.35) return;

        Location start = p.getLocation().clone();
        if (Util.inLiquid(start) || Util.nearSpecial(start) || Util.blockedAround(start) || Util.ceiling(start)) return;

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || p.isDead() || p.isInsideVehicle() || p.isGliding()) return;
            if (d.lastTeleport > t0 || pl.isLagging(p)) return;
            Location end = p.getLocation();
            if (!end.getWorld().equals(start.getWorld())) return;
            double moved = Math.hypot(end.getX() - start.getX(), end.getZ() - start.getZ());
            double movedY = end.getY() - start.getY();
            double ratio = pl.num("Velocity", "min-ratio", 0.35);
            boolean bad = (vh >= 0.25 && moved < vh * ratio) || (vy >= 0.35 && movedY < vy * 0.3);
            if (d.buffer("Velocity", bad, 1, 0.5, 2)) {
                pl.violations().flag(p, "Velocity", String.format("moved=%.2f exp>=%.2f", moved, vh * ratio), 2);
            }
        }, 4L);
    }
}
