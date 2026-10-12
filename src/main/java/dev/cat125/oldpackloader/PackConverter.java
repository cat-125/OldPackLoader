package dev.cat125.oldpackloader;

import com.agentdid127.resourcepack.forwards.ForwardsPackConverter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class PackConverter {
    private static final Gson GSON = new Gson();
    private static final Object CONVERSION_LOCK = new Object();
    private static final int CONVERTER_VERSION = 2;
    public static final Logger LOGGER = LoggerFactory.getLogger("OldPackLoader");
    private static final Logger LOGGER_RPC = LoggerFactory.getLogger("ResourcePackConverter");
    public static final String RESULT_FAIL = "FAIL!";

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Path cacheDir = mc.gameDirectory.toPath().resolve(".converted-packs");
    private static final File cacheFile = cacheDir.resolve("data.json").toFile();
    private static final JsonObject cache;

    static {
        JsonObject d;
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile)) {
                d = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException e) {
                d = new JsonObject();
            }
        } else {
            d = new JsonObject();
        }
        cache = d;
    }

    public static PackResources convertResources(PackResources originalResources) {
        try {
            // Check if we need to convert the pack
            var packVersion = PackVersion.detect(originalResources);

            if (!packVersion.needsMigration()) return originalResources;

            if (originalResources instanceof FilePackResources res) {
                PackResources converted = convertPackFile(originalResources, res, packVersion);
                if (converted != null) return converted;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to convert resource pack", e);
        }

        return originalResources;
    }

    private static @Nullable PackResources convertPackFile(
            PackResources originalResources,
            FilePackResources res,
            PackVersion packVersion
    ) throws NoSuchAlgorithmException, IOException {

        var packFile = res.zipFileAccess.file;

        // Skip built-in packs
        if (!packFile.getName().endsWith(".zip")) return originalResources;

        var packHash = fileHash(packFile);
        var packKey = CONVERTER_VERSION + "|" + packHash;

        File convertedFile;

        if (cache.has(packKey)) {
            var converted = cache.get(packKey).getAsString();
            if (converted.equals(RESULT_FAIL)) {
                // Don't try converting it again until the mod updates
                convertedFile = null;
            } else {
                // Use the existing converted pack
                convertedFile = new File(converted);
                if (!convertedFile.exists()) {
                    convertedFile = null;
                    cache.remove(packKey);
                    saveCache();
                }
            }
        } else {
            // Check if we need to delete old converted file
            var oldKey = (CONVERTER_VERSION - 1) + "|" + packHash;
            if (cache.has(oldKey)) {
                var oldFile = new File(cache.get(oldKey).getAsString());
                if (oldFile.exists() && oldFile.toPath().startsWith(cacheDir)) {
                    var _ = oldFile.delete();
                }
                cache.remove(oldKey);
            }

            // Try converting the pack
            var targetVersion = PackVersion.CURRENT;
            var outPath = cacheDir.resolve(packFile.getName());
            var pack = new CachedZipPack(packFile.toPath(), outPath);
            var converter = new ForwardsPackConverter(
                    GSON,
                    packVersion.toInt(),
                    targetVersion.toInt(),
                    "none",
                    packFile.toPath(),
                    true,
                    loggingPrintStream()
            );
            LOGGER.info("Converting pack {} from {} to {}", packFile.getName(), packVersion, targetVersion);
            synchronized (CONVERSION_LOCK) {
                converter.runPack(pack);
            }
            convertedFile = outPath.toFile();

            // Save the result
            if (convertedFile.exists()) {
                cache.addProperty(packKey, convertedFile.getAbsolutePath());
            } else {
                cache.addProperty(packKey, RESULT_FAIL);
                convertedFile = null;
            }
            saveCache();
        }

        return convertedFile != null ? new FilePackResources(
                originalResources.location(),
                new FilePackResources.SharedZipFileAccess(convertedFile),
                res.prefix
        ) : null;
    }

    public static void applyFixes(Path workDir) {
        try {
            var postEffects = workDir.resolve("assets/minecraft/post_effect").toFile();
            if (postEffects.exists()) {
                // TODO: actual fix
                var files = postEffects.listFiles();
                var removed = 0;
                if (files != null) {
                    for (var file : files) {
                        if (file.delete()) removed++;
                    }
                }
                LOGGER.info("Removed {} post effects", removed);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply fixes");
        }
    }

    private static PrintStream loggingPrintStream() {
        return new PrintStream(System.out) {
            @Override
            public void println(String s) {
                LOGGER_RPC.info(s);
            }
        };
    }

    private static void saveCache() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            GSON.toJson(cache, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save cache file", e);
        }
    }

    private static String fileHash(File inFile) throws NoSuchAlgorithmException, IOException {
        var digest = MessageDigest.getInstance("SHA-256");

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inFile))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }
}
