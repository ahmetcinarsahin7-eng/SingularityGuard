package dev.singularityguard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SingularityGuardCommand implements CommandExecutor, TabCompleter {
    private final SingularityGuard pl;

    public SingularityGuardCommand(SingularityGuard pl) { this.pl = pl; }

    private void msg(CommandSender s, String m) {
        s.sendMessage(pl.color(pl.getConfig().getString("prefix", "") + m));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("alerts")) {
            if (!(s instanceof Player p)) { msg(s, "&cSadece oyuncular."); return true; }
            if (!p.hasPermission("singularityguard.alerts")) { msg(s, "&cYetkin yok."); return true; }
            if (pl.alertsDisabled().remove(p.getUniqueId())) msg(s, "&aUyarılar açıldı.");
            else { pl.alertsDisabled().add(p.getUniqueId()); msg(s, "&cUyarılar kapatıldı."); }
            return true;
        }

        if (!s.hasPermission("singularityguard.admin")) { msg(s, "&cYetkin yok."); return true; }

        switch (sub) {
            case "reload" -> { pl.reloadConfig(); msg(s, "&aConfig yenilendi."); }
            case "status" -> {
                double tps = 20;
                try { tps = Bukkit.getTPS()[0]; } catch (Throwable ignored) { }
                msg(s, String.format("&7TPS: &f%.1f &8| &7Takip edilen oyuncu: &f%d", tps, pl.allData().size()));
            }
            case "vl" -> {
                if (a.length < 2) { msg(s, "&7Kullanım: /ac vl <oyuncu>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(s, "&cOyuncu bulunamadı."); return true; }
                Map<String, Double> vl = pl.data(t).vl;
                if (vl.values().stream().allMatch(v -> v <= 0)) { msg(s, "&a" + t.getName() + " temiz."); return true; }
                msg(s, "&e" + t.getName() + " &7ihlalleri:");
                vl.forEach((k, v) -> { if (v > 0) s.sendMessage(pl.color("  &8- &c" + k + " &7: &f" + String.format("%.1f", v))); });
            }
            case "reset" -> {
                if (a.length < 2) { msg(s, "&7Kullanım: /ac reset <oyuncu>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(s, "&cOyuncu bulunamadı."); return true; }
                pl.data(t).vl.clear();
                pl.data(t).buffers.clear();
                msg(s, "&a" + t.getName() + " VL sıfırlandı.");
            }
            default -> {
                msg(s, "&7Komutlar:");
                s.sendMessage(pl.color("  &c/ac alerts &8- &7Uyarıları aç/kapat"));
                s.sendMessage(pl.color("  &c/ac vl <oyuncu> &8- &7İhlalleri göster"));
                s.sendMessage(pl.color("  &c/ac reset <oyuncu> &8- &7VL sıfırla"));
                s.sendMessage(pl.color("  &c/ac status &8- &7Durum"));
                s.sendMessage(pl.color("  &c/ac reload &8- &7Config yenile"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : new String[]{"alerts", "vl", "reset", "status", "reload", "help"})
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("vl") || a[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
