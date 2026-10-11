package dev.cat125.oldpackloader;

import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.BomDetector;
import com.agentdid127.resourcepack.library.utilities.FileUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;

import java.io.IOException;
import java.nio.file.Path;

public class CachedZipPack extends Pack {

    private final Path out;

    public CachedZipPack(Path in, Path out) {
        this.out = out;
        super(in);
    }

    public Handler createHandler() {
        return new Handler(this, out);
    }

    public String getFileName() {
        return this.path.getFileName().toString().substring(0, this.path.getFileName().toString().length() - 4);
    }

    @Override
    public Path getWorkingPath() {
        return out.getParent().resolve(out.getFileName() + "_temp");
    }

    public static class Handler extends Pack.Handler {

        private final Path out;

        public Handler(Pack pack, Path out) {
            this.out = out;
            super(pack);
        }

        public Path getConvertedZipPath() {
            return out;
        }

        public void setup() throws IOException {
            if (this.pack.getWorkingPath().toFile().exists()) {
                Logger.log("Deleting existing conversion");
                FileUtil.deleteDirectoryAndContents(this.pack.getWorkingPath());
            }

            Path convertedZipPath = this.getConvertedZipPath();
            if (convertedZipPath.toFile().exists()) {
                Logger.log("Deleting existing conversion zip");
                var _ = convertedZipPath.toFile().delete();
            }

            var _ = this.pack.getWorkingPath().toFile().mkdir();

            try {
                ZipFile zipFile = new ZipFile(this.pack.getOriginalPath().toFile());
                zipFile.extractAll(this.pack.getWorkingPath().toString());
                zipFile.close();
            } catch (IOException e) {
                Util.propagate(e);
            }

            bomRemover(this.pack.getWorkingPath());
        }

        // it was private
        static void bomRemover(Path workingPath) throws IOException {
            BomDetector bom = new BomDetector(workingPath.toString(), ".txt", ".json", ".mcmeta", ".properties", ".lang");
            int count = bom.findBOMs().size();
            if (count > 0) {
                Logger.debug("Removing BOMs from " + count + " files.");
            }

            bom.removeBOMs();
        }

        public void finish() throws IOException {
            try {
                Logger.debug("Zipping working directory");
                ZipFile zipFile = new ZipFile(this.getConvertedZipPath().toFile());
                ZipParameters parameters = new ZipParameters();
                parameters.setIncludeRootFolder(false);
                zipFile.createSplitZipFileFromFolder(this.pack.getWorkingPath().toFile(), parameters, false, 65536L);
                zipFile.close();
            } catch (ZipException e) {
                Util.propagate(e);
            }

            Logger.debug("Deleting working directory");
            FileUtil.deleteDirectoryAndContents(this.pack.getWorkingPath());
            Logger.log("Finished!");
        }

        public String toString() {
            return "Handler{} " + super.toString();
        }
    }
}

