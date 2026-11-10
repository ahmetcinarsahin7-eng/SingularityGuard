package dev.singularityguard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class DataListener implements Listener {
    private final SingularityGuard pl;

    public DataListener(SingularityGuard pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = pl.data(e.getPlayer());
        d.lastJoin = System.currentTimeMillis();
        d.lastSafe = e.getPlayer().getLocation().clone();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.removeData(e.getPlayer().getUniqueId());
    }

    private void reset(Player p, java.util.function.Consumer<PlayerData> extra) {
        PlayerData d = pl.data(p);
        d.lastTeleport = System.currentTimeMillis();
        d.airTicks = 0;
        d.lastDy = 0;
        d.timerBalance = 0;
        d.fallTracked = 0;
        d.lastSafe = p.getLocation().clone();
        extra.accept(d);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = pl.data(e.getPlayer());
        d.lastTeleport = System.currentTimeMillis();
        d.airTicks = 0;
        d.lastDy = 0;
        d.timerBalance = 0;
        d.fallTracked = 0;
        if (e.getTo() != null) d.lastSafe = e.getTo().clone();
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) { reset(e.getPlayer(), d -> d.lastSafe = e.getRespawnLocation().clone()); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { reset(e.getPlayer(), d -> { }); }
    @EventHandler public void onGm(PlayerGameModeChangeEvent e) { reset(e.getPlayer(), d -> { }); }
    @EventHandler public void onFlight(PlayerToggleFlightEvent e) { reset(e.getPlayer(), d -> { }); }
    @EventHandler public void onRiptide(PlayerRiptideEvent e) { pl.data(e.getPlayer()).lastSpecial = System.currentTimeMillis() + 3000; }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) reset(p, d -> { });
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) pl.data(p).lastSpecial = System.currentTimeMillis() + 2000;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) pl.data(p).lastDamaged = System.currentTimeMillis();
    }

    @EventHandler
    public void onInvOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) pl.data(p).invOpened = System.currentTimeMillis();
    }
}
