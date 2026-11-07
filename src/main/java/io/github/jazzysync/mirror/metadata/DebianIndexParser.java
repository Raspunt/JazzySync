package io.github.jazzysync.mirror.metadata;

import io.github.jazzysync.mirror.api.ExpectedFile;
import io.github.jazzysync.mirror.api.MetadataParser;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** Parses dists/&lt;dist&gt;/&lt;component&gt;/binary-&lt;arch&gt;/Packages(.gz|.xz) of a Debian-style repository. */
public class DebianIndexParser implements MetadataParser {
    private final String dist;

    public DebianIndexParser(String dist) {
        this.dist = dist;
    }

    @Override
    public Map<Path, ExpectedFile> parse(Path repoRoot) throws IOException {
        Path distDir = repoRoot.resolve("dists").resolve(dist);
        Map<Path, ExpectedFile> result = new HashMap<>();
        if (!Files.isDirectory(distDir)) return result;

        List<Path> binaryDirs;
        try (Stream<Path> s = Files.find(distDir, 2, (p, a) -> a.isDirectory()
                && p.getFileName().toString().startsWith("binary-"))) {
            binaryDirs = s.sorted().toList();
        }
        for (Path dir : binaryDirs) {
            try (InputStream in = openPackages(dir)) {
                if (in != null) {
                    parsePackages(in, repoRoot, result);
                }
            }
        }
        return result;
    }

    /** Opens the first available Packages file in a binary-* directory, or returns null. */
    private static InputStream openPackages(Path dir) throws IOException {
        Path gz = dir.resolve("Packages.gz");
        if (Files.isRegularFile(gz)) {
            return new GZIPInputStream(Files.newInputStream(gz), 1 << 16);
        }
        Path xz = dir.resolve("Packages.xz");
        if (Files.isRegularFile(xz)) {
            // The JDK has no xz decoder; xz-utils is always present on Debian hosts.
            Process p = new ProcessBuilder("xz", "-dc", xz.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return new FilterInputStream(p.getInputStream()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    try {
                        if (p.waitFor() != 0) {
                            throw new IOException("xz failed to decompress " + xz);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted while reading " + xz, e);
                    }
                }
            };
        }
        Path plain = dir.resolve("Packages");
        if (Files.isRegularFile(plain)) {
            return Files.newInputStream(plain);
        }
        return null;
    }

    static void parsePackages(InputStream in, Path repoRoot, Map<Path, ExpectedFile> out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String filename = null;
        long size = -1;
        String sha256 = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                add(out, repoRoot, filename, size, sha256);
                filename = null;
                size = -1;
                sha256 = null;
            } else if (line.startsWith("Filename: ")) {
                filename = line.substring(10).trim();
            } else if (line.startsWith("Size: ")) {
                size = Long.parseLong(line.substring(6).trim());
            } else if (line.startsWith("SHA256: ")) {
                sha256 = line.substring(8).trim();
            }
        }
        add(out, repoRoot, filename, size, sha256);
    }

    private static void add(Map<Path, ExpectedFile> out, Path root, String filename, long size, String sha256) {
        if (filename != null && sha256 != null) {
            out.put(root.resolve(filename), new ExpectedFile(size, sha256));
        }
    }
}
