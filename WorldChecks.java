package dev.singularityguard;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** FastBreak, Nuker, BlockReach, Xray, Scaffold, FastPlace */
public class WorldChecks implements Listener {
    private final SingularityGuard pl;

    public WorldChecks(SingularityGuard pl) { this.pl = pl; }

    private boolean skip(Player p) {
        GameMode gm = p.getGameMode();
        return p.hasPermission("singularityguard.bypass") || gm == GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        PlayerData d = pl.data(e.getPlayer());
        d.breakStart = System.currentTimeMillis();
        d.breakLoc = e.getBlock().getLocation();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (skip(p)) return;
        Block b = e.getBlock();
        PlayerData d = pl.data(p);
        ViolationManager vm = pl.violations();
        long now = System.currentTimeMillis();
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        Location eye = p.getEyeLocation();
        double dist = eye.toVector().distance(b.getLocation().add(0.5, 0.5, 0.5).toVector());

        // ---------- BlockReach ----------
        if (pl.enabled("BlockReach") && dist > pl.num("BlockReach", "max-distance", 6.5)) {
            vm.flag(p, "BlockReach", String.format("break dist=%.1f", dist), 2);
            e.setCancelled(true);
            return;
        }

        if (!creative) {
            // ---------- FastBreak ----------
            boolean started = d.breakLoc != null && d.breakLoc.equals(b.getLocation());
            float speed = b.getBreakSpeed(p);
            if (pl.enabled("FastBreak") && started && speed > 0 && speed < 1.0f) {
                double expectedMs = (1.0 / speed) * 50.0;
                double elapsed = now - d.breakStart;
                double min = expectedMs * pl.num("FastBreak", "min-ratio", 0.7) - 100;
                if (d.buffer("FastBreak", elapsed < min, 1, 0.5, 1)) {
                    vm.flag(p, "FastBreak", String.format("%.0fms < %.0fms", elapsed, expectedMs), 2);
                    e.setCancelled(true);
                    return;
                }
            }

            // ---------- Nuker ----------
            if (pl.enabled("Nuker")) {
                d.breakTimes.addLast(now);
                PlayerData.trim(d.breakTimes, now, 1000);
                if (d.breakTimes.size() > pl.num("Nuker", "max-per-second", 12)) {
                    vm.flag(p, "Nuker", "blocks/s=" + d.breakTimes.size(), 2);
                    e.setCancelled(true);
                    return;
                }
                if (!started && speed < 1.0f
                        && pl.getConfig().getBoolean("checks.Nuker.require-dig-start", false)
                        && d.buffer("Nuker.dig", true, 1, 0.5, 2)) {
                    vm.flag(p, "Nuker", "break without dig start", 2);
                    e.setCancelled(true);
                    return;
                }
                Block tb = p.getTargetBlockExact(7);
                if (tb != null && tb.getLocation().distanceSquared(b.getLocation()) > 4
                        && d.buffer("Nuker.target", true, 1, 0.5, 2)) {
                    vm.flag(p, "Nuker", "broke block not looked at", 2);
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // ---------- Xray ----------
        if (pl.enabled("Xray") && !creative) xray(p, d, b);
    }

    private void xray(Player p, PlayerData d, Block b) {
        Material m = b.getType();
        String n = m.name();
        boolean valuable = n.contains("DIAMOND_ORE") || n.contains("EMERALD_ORE")
                || n.equals("ANCIENT_DEBRIS") || n.contains("GOLD_ORE");
        boolean stone = m == Material.STONE || m == Material.DEEPSLATE || m == Material.NETHERRACK
                || m == Material.ANDESITE || m == Material.DIORITE || m == Material.GRANITE
                || m == Material.TUFF || m == Material.BASALT || m == Material.BLACKSTONE;
        if (!valuable && !stone) return;

        boolean hidden = true;
        if (valuable) {
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                    BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (!b.getRelative(f).getType().isOccluding()) { hidden = false; break; }
            }
        }
        d.oreWindow.addLast(valuable && hidden);
        int window = (int) pl.num("Xray", "window", 100);
        while (d.oreWindow.size() > window) d.oreWindow.pollFirst();
        if (d.oreWindow.size() < pl.num("Xray", "min-sample", 40)) return;

        long ores = d.oreWindow.stream().filter(x -> x).count();
        double ratio = (double) ores / d.oreWindow.size();
        if (ores >= 4 && ratio > pl.num("Xray", "max-ratio", 0.08)) {
            pl.violations().flag(p, "Xray", String.format("hidden ores %d/%d (%.0f%%)",
                    ores, d.oreWindow.size(), ratio * 100), 3);
            d.oreWindow.clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (skip(p)) return;
        PlayerData d = pl.data(p);
        ViolationManager vm = pl.violations();
        long now = System.currentTimeMillis();
        Block placed = e.getBlockPlaced();
        Block against = e.getBlockAgainst();
        Location eye = p.getEyeLocation();
        Location loc = p.getLocation();

        // ---------- BlockReach ----------
        double dist = eye.toVector().distance(against.getLocation().add(0.5, 0.5, 0.5).toVector());
        if (pl.enabled("BlockReach") && dist > pl.num("BlockReach", "max-distance", 6.5)) {
            vm.flag(p, "BlockReach", String.format("place dist=%.1f", dist), 2);
            e.setCancelled(true);
            return;
        }

        // ---------- FastPlace ----------
        d.placeTimes.addLast(now);
        PlayerData.trim(d.placeTimes, now, 1000);
        if (pl.enabled("FastPlace") && d.placeTimes.size() > pl.num("FastPlace", "max-per-second", 16)) {
            vm.flag(p, "FastPlace", "blocks/s=" + d.placeTimes.size(), 2);
            e.setCancelled(true);
            return;
        }

        if (!pl.enabled("Scaffold") || placed.equals(against)) return;

        // ---------- Scaffold: bakış açısı ----------
        Vector toAgainst = against.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        double ang = Math.toDegrees(eye.getDirection().angle(toAgainst));
        if (d.buffer("Scaffold.angle", ang > pl.num("Scaffold", "max-angle", 80), 1, 0.5, 2)) {
            vm.flag(p, "Scaffold", String.format("angle=%.0f", ang), 2);
            e.setCancelled(true);
            return;
        }

        // ---------- Scaffold: baktığı blok ile yerleştirdiği blok uyuşuyor mu ----------
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), 6.0, FluidCollisionMode.NEVER, false);
        boolean mismatch = r == null || r.getHitBlock() == null || !r.getHitBlock().equals(against);
        if (d.buffer("Scaffold.face", mismatch, 1, 0.5, 4)) {
            vm.flag(p, "Scaffold", "not looking at placed-against block", 2);
            e.setCancelled(true);
            return;
        }

        // ---------- Tower ----------
        boolean below = placed.getY() < loc.getY()
                && loc.getY() - placed.getY() <= 2.0
                && Math.abs(placed.getX() + 0.5 - loc.getX()) < 0.8
                && Math.abs(placed.getZ() + 0.5 - loc.getZ()) < 0.8;
        if (below) {
            d.belowTimes.addLast(now);
            PlayerData.trim(d.belowTimes, now, 1000);
            if (d.belowTimes.size() > pl.num("Scaffold", "max-tower-per-second", 5)) {
                vm.flag(p, "Scaffold", "tower x" + d.belowTimes.size() + "/s", 2);
                e.setCancelled(true);
            }
        }
    }
}
