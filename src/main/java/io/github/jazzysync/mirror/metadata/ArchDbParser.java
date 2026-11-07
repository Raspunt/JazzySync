package io.github.jazzysync.mirror.metadata;

import io.github.jazzysync.mirror.api.ExpectedFile;
import io.github.jazzysync.mirror.api.MetadataParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** Parses the pacman databases &lt;repo&gt;/os/&lt;arch&gt;/&lt;repo&gt;.db (gzip-compressed tar) of an Arch mirror. */
public class ArchDbParser implements MetadataParser {

    @Override
    public Map<Path, ExpectedFile> parse(Path repoRoot) throws IOException {
        Map<Path, ExpectedFile> result = new HashMap<>();
        if (!Files.isDirectory(repoRoot)) return result;

        List<Path> dbs;
        try (Stream<Path> s = Files.find(repoRoot, 4, (p, a) -> {
            Path rel = repoRoot.relativize(p);
            // <repo>/os/<arch>/<repo>.db
            return rel.getNameCount() == 4
                    && rel.getName(1).toString().equals("os")
                    && rel.getFileName().toString().equals(rel.getName(0) + ".db");
        })) {
            dbs = s.sorted().toList();
        }
        for (Path db : dbs) {
            parseDb(db, result);
        }
        return result;
    }

    static void parseDb(Path db, Map<Path, ExpectedFile> out) throws IOException {
        Path dir = db.getParent();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(db), 1 << 16)) {
            in.mark(2);
            int b1 = in.read(), b2 = in.read();
            in.reset();
            if (b1 != 0x1f || b2 != 0x8b) {
                throw new IOException(db + " is not gzip-compressed, cannot read it");
            }
            readTar(new GZIPInputStream(in, 1 << 16), dir, out);
        }
    }

    /** Minimal tar reader: looks only at regular files, which is where the desc entries live. */
    private static void readTar(InputStream in, Path dir, Map<Path, ExpectedFile> out) throws IOException {
        byte[] header = new byte[512];
        while (true) {
            if (in.readNBytes(header, 0, 512) < 512) return;
            if (isZero(header)) return;
            long size = parseOctal(header, 124, 12);
            char type = (char) header[156];
            byte[] data = in.readNBytes((int) size);
            if (data.length < size) throw new IOException("truncated tar entry");
            long pad = (512 - size % 512) % 512;
            in.skipNBytes(pad);
            if (type == '0' || type == '\0') {
                parseDesc(new String(data, StandardCharsets.UTF_8), dir, out);
            }
        }
    }

    static void parseDesc(String desc, Path dir, Map<Path, ExpectedFile> out) {
        String filename = null, sha256 = null;
        long size = -1;
        String[] lines = desc.split("\n");
        for (int i = 0; i + 1 < lines.length; i++) {
            switch (lines[i].trim()) {
                case "%FILENAME%" -> filename = lines[i + 1].trim();
                case "%CSIZE%" -> size = Long.parseLong(lines[i + 1].trim());
                case "%SHA256SUM%" -> sha256 = lines[i + 1].trim();
                default -> { }
            }
        }
        if (filename != null && sha256 != null) {
            out.put(dir.resolve(filename), new ExpectedFile(size, sha256));
        }
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte b = buf[i];
            if (b == 0 || b == ' ') {
                if (v != 0) break;
                continue;
            }
            v = v * 8 + (b - '0');
        }
        return v;
    }
}
