package de.passimoritz.voiceapi;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Tasks "on the main thread" on every server: Spigot/Paper/Purpur through the Bukkit scheduler, Folia (no main loop, no Bukkit scheduler)
 * through the global region scheduler. Folia is detected at runtime and called via reflection – so the same jar runs everywhere, also on
 * future servers, without compiling against their API.
 */
public final class Scheduling {

    /** A running repetition that can be stopped. */
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

    /** Whether the server is Folia (or a Folia fork). */
    public static boolean isFolia() {
        return FOLIA;
    }

    /** Runs a task on the next tick on the "main thread" (Folia: on the global region thread). */
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
                throw new IllegalStateException("Folia scheduler not reachable", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Repeats a task every tick on the "main thread" (Folia: on the global region thread). */
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
                        // already finished
                    }
                };
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia scheduler not reachable", e);
            }
        }
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, 1L, 1L);
        return () -> Bukkit.getScheduler().cancelTask(id);
    }
}
