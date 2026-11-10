package dev.singularityguard;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Fly, Speed, NoSlow, NoFall, Jesus, Step, Timer, Phase, OmniSprint, InvMove, BadPackets */
public class MovementChecks implements Listener {
    private final SingularityGuard pl;

    public MovementChecks(SingularityGuard pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("singularityguard.bypass")) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        PlayerData d = pl.data(p);
        ViolationManager vm = pl.violations();
        long now = System.currentTimeMillis();
        boolean setback = pl.getConfig().getBoolean("setback", true);

        // ---------------- BadPackets ----------------
        if (pl.enabled("BadPackets")) {
            if (!Double.isFinite(to.getX()) || !Double.isFinite(to.getY()) || !Double.isFinite(to.getZ())
                    || !Float.isFinite(to.getYaw()) || !Float.isFinite(to.getPitch())) {
                vm.flag(p, "BadPackets", "invalid position/rotation", 5);
                e.setCancelled(true);
                return;
            }
            if (Math.abs(to.getPitch()) > 90.0f) {
                vm.flag(p, "BadPackets", "pitch=" + String.format("%.1f", to.getPitch()), 3);
                Location fix = to.clone();
                fix.setPitch(Math.max(-90f, Math.min(90f, to.getPitch())));
                e.setTo(fix);
                to = fix;
            }
        }

        // ---------------- Timer ----------------
        long delta = now - d.lastMoveEvent;
        d.lastMoveEvent = now;
        if (pl.enabled("Timer") && now - d.lastTeleport > 1500 && now - d.lastJoin > 3000 && !pl.isLagging(p)) {
            d.timerBalance += 50 - delta;
            if (d.timerBalance > 150) d.timerBalance = 150;
            if (d.timerBalance < -pl.num("Timer", "max-balance-ms", 250)) {
                vm.flag(p, "Timer", String.format("balance=%.0fms", d.timerBalance), 1);
                d.timerBalance = 0;
                if (setback) { e.setCancelled(true); return; }
            }
        } else {
            d.timerBalance = 0;
        }

        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (!moved) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double h = Math.hypot(dx, dz);

        boolean ground = Util.onGround(to);
        boolean liquid = Util.inLiquid(to);
        boolean climb = Util.onClimbable(to);
        boolean airborne = !ground && !liquid && !climb;
        boolean contiguous = now - d.lastMove < 200;
        if (Util.nearSpecial(to)) d.lastSpecial = now;
        if (liquid || climb) d.lastLiquid = now;

        if (airborne) {
            d.airTicks++;
            d.groundTicks = 0;
            d.lastAirMove = now;
            if (dy < 0) d.fallTracked += -dy;
        } else {
            d.airTicks = 0;
            d.fallTracked = 0;
            if (ground) d.groundTicks++;
            d.lastGroundY = to.getY();
        }

        boolean exempt = Util.exemptMovement(pl, p, d, now);
        boolean bad = false;

        if (!exempt) {
            int speedAmp = Util.effect(p, "SPEED");
            int slowAmp = Util.effect(p, "SLOW", "SLOWNESS");
            int jumpAmp = Util.effect(p, "JUMP", "JUMP_BOOST");
            boolean levit = Util.effect(p, "LEVITATION") >= 0 || Util.effect(p, "SLOW_FALLING") >= 0;
            double mult = 1.0;
            if (speedAmp >= 0) mult += 0.2 * (speedAmp + 1);
            if (slowAmp >= 0) mult = Math.max(0.2, mult - 0.15 * (slowAmp + 1));
            double walkRatio = p.getWalkSpeed() / 0.2;
            boolean ice = Util.onIce(to);

            // ---------- Fly (gravity/hover/glide) ----------
            if (pl.enabled("Fly") && contiguous && airborne && d.airTicks >= 2 && !levit && now - d.lastLiquid > 500) {
                double expected = (d.lastDy - 0.08) * 0.98;
                double diff = dy - expected;
                boolean bump = Util.ceiling(to);
                if (d.buffer("Fly.gravity", diff > pl.num("Fly", "tolerance", 0.06) && !bump, 1, 0.25, 4)
                        && !Util.nearEntityPlatform(p)) {
                    vm.flag(p, "Fly", String.format("gravity dy=%.3f exp=%.3f", dy, expected), 2);
                    bad = true;
                }
            }
            // ---------- Fly (max height) ----------
            if (!bad && pl.enabled("Fly") && contiguous && airborne && dy > 0 && !levit) {
                double maxJump = 1.3 + (jumpAmp >= 0 ? 0.55 * (jumpAmp + 1) : 0);
                if (to.getY() - d.lastGroundY > maxJump && !Util.nearEntityPlatform(p)) {
                    vm.flag(p, "Fly", String.format("height=%.2f", to.getY() - d.lastGroundY), 2);
                    bad = true;
                }
            }

            // ---------- NoFall (ground spoof) ----------
            if (!bad && pl.enabled("NoFall") && contiguous && airborne && d.airTicks >= 3 && dy < -0.15
                    && d.buffer("NoFall", p.isOnGround(), 1, 0.5, 3) && !Util.nearEntityPlatform(p)) {
                vm.flag(p, "NoFall", String.format("spoofed ground fall=%.1f", d.fallTracked), 2);
                bad = true;
            }

            // ---------- Speed ----------
            if (!bad && pl.enabled("Speed") && contiguous && !liquid && !climb && h > 0) {
                double limit = d.groundTicks >= 3 ? 0.36 : 0.66;
                limit *= mult * walkRatio;
                if (ice) limit *= 2.2;
                if (Util.ceiling(to)) limit *= 1.5;
                limit += pl.num("Speed", "leniency", 0.03);
                if (d.buffer("Speed", h > limit, 1, 0.5, 4)) {
                    vm.flag(p, "Speed", String.format("h=%.3f limit=%.3f", h, limit), 1.5);
                    bad = true;
                }
            }

            // ---------- NoSlow ----------
            if (!bad && pl.enabled("NoSlow") && ground && d.groundTicks >= 4 && !ice && p.isHandRaised()) {
                double lim = 0.13 * mult * walkRatio;
                if (d.buffer("NoSlow", h > lim, 1, 0.5, 5)) {
                    vm.flag(p, "NoSlow", String.format("h=%.3f while using item", h), 1.5);
                    bad = true;
                }
            }

            // ---------- Jesus ----------
            if (!bad && pl.enabled("Jesus") && !ground && !p.isSwimming() && to.getBlock().isLiquid()
                    && !to.clone().add(0, 1, 0).getBlock().isLiquid()) {
                if (d.buffer("Jesus", Math.abs(dy) < 0.001 && h > 0.1, 1, 1, 6)) {
                    vm.flag(p, "Jesus", "walking on liquid", 2);
                    bad = true;
                }
            }

            // ---------- Step ----------
            if (!bad && pl.enabled("Step") && contiguous && d.lastServerGround && ground && jumpAmp < 0
                    && d.buffer("Step", dy > pl.num("Step", "max-height", 0.65), 1, 0.5, 1)) {
                vm.flag(p, "Step", String.format("dy=%.2f", dy), 2);
                bad = true;
            }

            // ---------- Phase ----------
            if (!bad && pl.enabled("Phase") && h + Math.abs(dy) > 0.05) {
                if (d.buffer("Phase", phase(from, to), 1, 0.5, 1)) {
                    vm.flag(p, "Phase", "moved through solid block", 3);
                    bad = true;
                }
            }

            // ---------- OmniSprint ----------
            if (!bad && pl.enabled("OmniSprint") && p.isSprinting() && ground && d.groundTicks >= 3 && h > 0.2) {
                double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double diff = Math.abs(Util.wrap(moveYaw - to.getYaw()));
                if (d.buffer("Omni", diff > 100, 1, 0.5, 6)) {
                    vm.flag(p, "OmniSprint", String.format("angle=%.0f", diff), 1.5);
                    bad = true;
                }
            }

            // ---------- InvMove ----------
            if (!bad && pl.enabled("InvMove") && ground && d.groundTicks >= 3 && h > 0.15
                    && p.getOpenInventory().getType() != InventoryType.CRAFTING
                    && now - d.invOpened > 700) {
                if (d.buffer("InvMove", true, 1, 0.5, 4)) {
                    vm.flag(p, "InvMove", "moving with open container", 1.5);
                    bad = true;
                }
            }
        }

        if (bad && setback && d.lastSafe != null && d.lastSafe.getWorld() != null
                && d.lastSafe.getWorld().equals(to.getWorld())) {
            Location back = d.lastSafe.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
            d.lastDy = 0;
            d.airTicks = 0;
            d.lastMove = now;
            return;
        }

        d.lastDy = dy;
        d.lastH = h;
        d.lastServerGround = ground;
        d.lastMove = now;
        if (ground && !liquid) d.lastSafe = to.clone();
    }

    /** from->to hattında dolu (occluding) bloğa ışın çarpıyor mu? */
    private boolean phase(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 0.05) return false;
        dir.normalize();
        for (double off : new double[]{0.3, 1.0, 1.6}) {
            Location start = from.clone().add(0, off, 0);
            if (start.getBlock().getType().isOccluding()) continue; // zaten içeride
            RayTraceResult r = from.getWorld().rayTraceBlocks(start, dir, len, FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) continue;
            Block hit = r.getHitBlock();
            if (!hit.getType().isOccluding()) continue;
            if (r.getHitPosition().distance(start.toVector()) < len - 0.03) return true;
        }
        return false;
    }
}
