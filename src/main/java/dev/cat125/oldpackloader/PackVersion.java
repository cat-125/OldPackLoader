package dev.cat125.oldpackloader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public record PackVersion(int major, int minor) implements Comparable<PackVersion> {
    public static final PackVersion CURRENT;
    public static final PackVersion UNKNOWN = new PackVersion(Integer.MAX_VALUE, Integer.MAX_VALUE);

    static {
        var ver = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        CURRENT = new PackVersion(ver.major(), ver.minor());
    }

    public static PackVersion detect(PackResources resources) throws IOException {
        IoSupplier<InputStream> supplier = resources.getRootResource(PackResources.PACK_META);
        if (supplier == null) {
            return UNKNOWN;
        }

        try (InputStream input = supplier.get(); InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(reader));
        }
    }

    public static PackVersion fromJson(JsonElement root) {
        if (!root.isJsonObject()) {
            return UNKNOWN;
        }

        JsonObject packMeta = root.getAsJsonObject().getAsJsonObject("pack");
        if (packMeta == null) {
            return UNKNOWN;
        }

        PackVersion modern = readFormat(packMeta.get("min_format"));
        if (modern.isKnown()) {
            return modern;
        }

        JsonElement legacy = packMeta.get("pack_format");
        if (legacy != null && legacy.isJsonPrimitive() && legacy.getAsJsonPrimitive().isNumber()) {
            return new PackVersion(legacy.getAsInt(), 0);
        }

        JsonElement supported = packMeta.get("supported_formats");
        return readFormat(supported);
    }

    private static PackVersion readFormat(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return UNKNOWN;
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new PackVersion(element.getAsInt(), 0);
        }

        if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
            JsonElement first = element.getAsJsonArray().get(0);
            if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isNumber()) {
                int minor = element.getAsJsonArray().size() > 1 ? element.getAsJsonArray().get(1).getAsInt() : 0;
                return new PackVersion(first.getAsInt(), minor);
            }
        }

        if (element.isJsonObject()) {
            return readFormat(element.getAsJsonObject().get("min_inclusive"));
        }

        return UNKNOWN;
    }

    public int toInt() { return major() * 10 + minor(); }

    public boolean isKnown() {
        return major != Integer.MAX_VALUE || minor != Integer.MAX_VALUE;
    }

    public boolean needsMigration() {
        return isKnown() && compareTo(CURRENT) < 0;
    }

    @Override
    public int compareTo(PackVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        return majorComparison == 0 ? Integer.compare(minor, other.minor) : majorComparison;
    }

    @Override
    public @NonNull String toString() {
        return major == Integer.MAX_VALUE ? "unknown" : major + "." + minor;
    }
}
