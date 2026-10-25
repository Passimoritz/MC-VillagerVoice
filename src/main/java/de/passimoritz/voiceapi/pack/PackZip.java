package de.passimoritz.voiceapi.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes resource packs reproducibly: the same files give the same bytes and so the same SHA-1 – on every start and every machine. The
 * client keeps a pack by its SHA-1 and does not download it again; a pack whose zip changed only in its timestamps cost every player a new
 * download. So: entries sorted by name, one fixed time (1980-01-01), OGG/PNG stored (they are compressed already), the rest deflated.
 */
public final class PackZip {

    /** The fixed time of every entry (1980-01-01 as DOS time, independent of the time zone). */
    public static final java.time.LocalDateTime TIME = java.time.LocalDateTime.of(1980, 1, 1, 0, 0);

    private PackZip() {
    }

    /** The zip of these entries (name → content), sorted by name. */
    public static byte[] bytes(SortedMap<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, entries);
        return out.toByteArray();
    }

    /** Writes the entries to {@code target} (via a temporary file); unchanged content leaves the file untouched. @return whether it was rewritten */
    public static boolean write(Path target, SortedMap<String, byte[]> entries) throws IOException {
        byte[] data = bytes(entries);
        if (Files.isRegularFile(target) && Files.size(target) == data.length && java.util.Arrays.equals(Files.readAllBytes(target), data)) {
            return false;
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, data);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private static void write(OutputStream target, SortedMap<String, byte[]> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(target)) {
            out.setLevel(9);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(TIME);
                byte[] data = e.getValue();
                if (stored(e.getKey())) {
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(data.length);
                    entry.setCompressedSize(data.length);
                    entry.setCrc(crc.getValue());
                } else {
                    entry.setMethod(ZipEntry.DEFLATED);
                }
                out.putNextEntry(entry);
                out.write(data);
                out.closeEntry();
            }
        }
    }

    private static boolean stored(String name) {
        return name.endsWith(".ogg") || name.endsWith(".png");
    }

    /** The SHA-1 (hex) Minecraft checks a pack with. */
    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
