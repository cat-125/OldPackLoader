package dev.cat125.oldpackloader;

import com.agentdid127.resourcepack.forwards.ForwardsPackConverter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class PackConverter {
    private static final Gson GSON = new Gson();
    private static final Object CONVERSION_LOCK = new Object();
    private static final int CONVERTER_VERSION = 1;

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Path convertedPath = mc.gameDirectory.toPath().resolve(".converted-packs");
    private static final File dataFile = convertedPath.resolve("data.json").toFile();
    private static final JsonObject data;

    static {
        JsonObject d;
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                d = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException e) {
                d = new JsonObject();
            }
        } else {
            d = new JsonObject();
        }
        data = d;
    }

    public static PackResources convertResources(PackResources originalResources) {
        try {
            var originalVersion = PackVersion.detect(originalResources);

            if (!originalVersion.needsMigration()) return originalResources;

            if (originalResources instanceof FilePackResources res) {
                var inFile = res.zipFileAccess.file;
                var packKey  = CONVERTER_VERSION + "|" + fileHash(inFile);

                File convertedFile;

                if (data.has(packKey)) {
                    convertedFile = new File(data.get(packKey).getAsString());
                } else {
                    var targetVersion = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);

                    var outPath = convertedPath.resolve(inFile.getName());
                    var pack = new CachedZipPack(inFile.toPath(), outPath);
                    var converter = new ForwardsPackConverter(
                            GSON,
                            originalVersion.major() * 10 + originalVersion.minor(),
                            targetVersion.major() * 10 + targetVersion.minor(),
                            "none",
                            inFile.toPath(),
                            true,
                            System.out
                    );
                    synchronized (CONVERSION_LOCK) {
                        converter.runPack(pack);
                    }
                    convertedFile = outPath.toFile();
                    if (convertedFile.exists()) {
                        data.addProperty(packKey, convertedFile.getAbsolutePath());
                        saveData();
                    }
                }

                if (convertedFile.exists()) {
                    return new FilePackResources(
                            originalResources.location(),
                            new FilePackResources.SharedZipFileAccess(convertedFile),
                            res.prefix
                    );
                } else {
                    data.remove(packKey);
                    saveData();
                }
            }
        } catch (Exception ignored) {
        }
        return originalResources;
    }

    private static void saveData() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
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
