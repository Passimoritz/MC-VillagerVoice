package de.passimoritz.voiceapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Small helpers so VoiceAPI runs on every server: Java 17 (Minecraft 1.17–1.20.4) up to today, old and new Gson versions. Nothing in here
 * depends on a version except what is detected at runtime.
 */
public final class Compat {

    private Compat() {
    }

    // ── Numbers (Math.clamp only exists from Java 21) ──

    public static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── Lists (getFirst/getLast/removeFirst/removeLast only exist from Java 21) ──

    public static <T> T first(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.get(0);
    }

    public static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    public static <T> T removeFirst(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.remove(0);
    }

    public static <T> T removeLast(List<T> list) {
        if (list.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return list.remove(list.size() - 1);
    }

    // ── JSON: JsonParser.parseString only exists from Gson 2.8.6 (Spigot 1.17 ships 2.8.0); new JsonParser().parse is deprecated in new versions ──

    private static final Method PARSE_STRING = findParseString();

    private static Method findParseString() {
        try {
            return JsonParser.class.getMethod("parseString", String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Parses JSON with whatever Gson version the server ships. */
    @SuppressWarnings("deprecation")
    public static JsonElement json(String text) {
        if (PARSE_STRING != null) {
            try {
                return (JsonElement) PARSE_STRING.invoke(null, text);
            } catch (ReflectiveOperationException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(e);
            }
        }
        return new JsonParser().parse(text);
    }
}
