package dev.singularityguard;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {
    public final UUID uuid;

    // Hareket durumu
    public Location lastSafe;
    public double lastDy, lastH, lastGroundY, fallTracked, timerBalance;
    public int airTicks, groundTicks;
    public boolean lastServerGround;

    // Zaman damgaları
    public long lastMove, lastMoveEvent, lastAirMove, lastTeleport, lastVelocity, lastDamaged;
    public long lastJoin = System.currentTimeMillis();
    public long lastSpecial, lastLiquid, invOpened, breakStart, lastClick;

    // Kazı
    public Location breakLoc;
    public long useStart, lastRegen, lastBoost;
    public final Deque<Long> dropTimes = new ArrayDeque<>();

    // Chat
    public String lastMessage = "";
    public long lastMessageTime;

    public final Map<String, Double> buffers = new HashMap<>();
    public final Map<String, Double> vl = new ConcurrentHashMap<>();

    public final Deque<Long> clicks = new ArrayDeque<>();
    public final Deque<Double> clickDelays = new ArrayDeque<>();
    public final Deque<Long> placeTimes = new ArrayDeque<>();
    public final Deque<Long> belowTimes = new ArrayDeque<>();
    public final Deque<Long> breakTimes = new ArrayDeque<>();
    public final Deque<Long> chatTimes = new ArrayDeque<>();
    public final Deque<Long> cmdTimes = new ArrayDeque<>();
    public final Deque<Long> invClicks = new ArrayDeque<>();
    public final Deque<Boolean> oreWindow = new ArrayDeque<>();
    public final Map<UUID, Long> recentTargets = new HashMap<>();

    public PlayerData(UUID uuid) { this.uuid = uuid; }

    /**
     * Tampon sistemi: tek seferlik false-positive'leri yutar.
     * bad ise buffer += add; eşik aşılırsa true döner ve sıfırlanır.
     * bad değilse buffer -= dec.
     */
    public boolean buffer(String key, boolean bad, double add, double dec, double threshold) {
        double b = buffers.getOrDefault(key, 0.0);
        if (bad) {
            b += add;
            if (b > threshold) { buffers.put(key, 0.0); return true; }
            buffers.put(key, b);
            return false;
        }
        buffers.put(key, Math.max(0, b - dec));
        return false;
    }

    public static void trim(Deque<Long> q, long now, long window) {
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
    }
}
