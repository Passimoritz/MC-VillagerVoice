package de.passimoritz.voiceapi;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Aufgaben „im Hauptthread“ auf jedem Server: Spigot/Paper/Purpur mit dem Bukkit-Scheduler, Folia (keine Hauptschleife, kein Bukkit-Scheduler) mit
 * dem Global-Region-Scheduler. Folia wird zur Laufzeit erkannt und per Reflexion angesprochen – so läuft dasselbe Jar überall, auch auf
 * künftigen Servern, ohne gegen deren API zu übersetzen.
 */
public final class Scheduling {

    /** Eine laufende Wiederholung, die sich stoppen lässt. */
    public interface Repeating {
        void cancel();
    }

    private static final boolean FOLIA = detectFolia();

    private Scheduling() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Ob der Server Folia (oder ein Folia-Abkömmling) ist. */
    public static boolean isFolia() {
        return FOLIA;
    }

    /** Führt eine Aufgabe beim nächsten Tick im „Hauptthread“ aus (Folia: im globalen Region-Thread). */
    public static void run(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                run.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run());
                return;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia-Scheduler nicht erreichbar", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Wiederholt eine Aufgabe jeden Tick im „Hauptthread“ (Folia: im globalen Region-Thread). */
    public static Repeating everyTick(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method repeat = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                Object handle = repeat.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(), 1L, 1L);
                Method cancel = handle.getClass().getMethod("cancel");
                return () -> {
                    try {
                        cancel.invoke(handle);
                    } catch (ReflectiveOperationException e) {
                        // schon beendet
                    }
                };
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia-Scheduler nicht erreichbar", e);
            }
        }
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, 1L, 1L);
        return () -> Bukkit.getScheduler().cancelTask(id);
    }
}
