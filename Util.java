package dev.singularityguard;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public final class Util {
    private Util() { }

    private static final Set<Material> SPECIAL = new HashSet<>();
    private static final Set<Material> ICE = new HashSet<>();

    static {
        for (String n : new String[]{"COBWEB", "SWEET_BERRY_BUSH", "HONEY_BLOCK", "BUBBLE_COLUMN", "POWDER_SNOW",
                "SLIME_BLOCK", "SCAFFOLDING", "MOVING_PISTON", "PISTON_HEAD", "SOUL_SAND", "MUD"}) {
            Material m = Material.getMaterial(n);
            if (m != null) SPECIAL.add(m);
        }
        for (String n : new String[]{"ICE", "PACKED_ICE", "BLUE_ICE", "FROSTED_ICE"}) {
            Material m = Material.getMaterial(n);
            if (m != null) ICE.add(m);
        }
    }

    private static int fl(double d) { return (int) Math.floor(d); }

    /** Sunucu tarafı zemin tespiti (client'ın onGround değerine güvenmez). */
    public static boolean onGround(Location l) {
        World w = l.getWorld();
        double[] o = {-0.3, 0, 0.3};
        for (double ox : o) {
            for (double oz : o) {
                int bx = fl(l.getX() + ox), bz = fl(l.getZ() + oz);
                if (!w.getBlockAt(bx, fl(l.getY() - 0.2), bz).isPassable()) return true;
                Material m = w.getBlockAt(bx, fl(l.getY() - 0.6), bz).getType();
                if (Tag.FENCES.isTagged(m) || Tag.WALLS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) return true;
            }
        }
        return false;
    }

    public static boolean inLiquid(Location l) {
        return l.getBlock().isLiquid() || l.clone().add(0, 0.9, 0).getBlock().isLiquid();
    }

    public static boolean onClimbable(Location l) {
        return Tag.CLIMBABLE.isTagged(l.getBlock().getType())
                || Tag.CLIMBABLE.isTagged(l.clone().add(0, 1, 0).getBlock().getType());
    }

    public static boolean ceiling(Location l) {
        World w = l.getWorld();
        return !w.getBlockAt(fl(l.getX()), fl(l.getY() + 1.85), fl(l.getZ())).isPassable()
                || !w.getBlockAt(fl(l.getX()), fl(l.getY() + 2.1), fl(l.getZ())).isPassable();
    }

    public static boolean nearSpecial(Location l) {
        World w = l.getWorld();
        int bx = fl(l.getX()), by = fl(l.getY()), bz = fl(l.getZ());
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    Material m = w.getBlockAt(bx + x, by + y, bz + z).getType();
                    if (SPECIAL.contains(m) || Tag.BEDS.isTagged(m) || Tag.CLIMBABLE.isTagged(m)) return true;
                }
        return false;
    }

    public static boolean onIce(Location l) {
        World w = l.getWorld();
        return ICE.contains(w.getBlockAt(fl(l.getX()), fl(l.getY() - 0.2), fl(l.getZ())).getType())
                || ICE.contains(w.getBlockAt(fl(l.getX()), fl(l.getY() - 1.0), fl(l.getZ())).getType());
    }

    public static boolean blockedAround(Location l) {
        World w = l.getWorld();
        int bx = fl(l.getX()), by = fl(l.getY()), bz = fl(l.getZ());
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                for (int y = 0; y <= 1; y++)
                    if (!w.getBlockAt(bx + x, by + y, bz + z).isPassable()) return true;
            }
        return false;
    }

    public static boolean nearEntityPlatform(Player p) {
        for (Entity en : p.getNearbyEntities(1.3, 1.6, 1.3)) {
            if (en instanceof Boat || en instanceof Shulker || en instanceof Minecart) return true;
        }
        return false;
    }

    /** Potion efekt seviyesi (amplifier). Yoksa -1. Sürümler arası isim farkını tolere eder. */
    @SuppressWarnings("deprecation")
    public static int effect(Player p, String... names) {
        for (String n : names) {
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t == null) continue;
            PotionEffect e = p.getPotionEffect(t);
            if (e != null) return e.getAmplifier();
        }
        return -1;
    }

    public static double wrap(double a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    public static Vector closest(BoundingBox box, Location from) {
        return new Vector(
                Math.max(box.getMinX(), Math.min(from.getX(), box.getMaxX())),
                Math.max(box.getMinY(), Math.min(from.getY(), box.getMaxY())),
                Math.max(box.getMinZ(), Math.min(from.getZ(), box.getMaxZ())));
    }

    public static boolean exemptMovement(SingularityGuard pl, Player p, PlayerData d, long now) {
        if (p.hasPermission("singularityguard.bypass")) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isRiptiding()
                || p.isInsideVehicle() || p.isDead() || p.isSleeping()) return true;
        if (now - d.lastTeleport < 1500 || now - d.lastVelocity < 1500
                || now - d.lastJoin < 3000 || now - d.lastSpecial < 1000) return true;
        return pl.isLagging(p);
    }
}
