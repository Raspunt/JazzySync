package io.github.jazzysync.mirror.metadata;

import io.github.jazzysync.mirror.api.ExpectedFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ArchDbParserTest {

    @Test
    void parsesDescEntriesOfEveryRepo(@TempDir Path root) throws Exception {
        writeDb(root.resolve("core/os/x86_64/core.db"),
                "bash-5.3-1/", null,
                "bash-5.3-1/desc", desc("bash-5.3-1-x86_64.pkg.tar.zst", 1900000, "aaaa"));
        writeDb(root.resolve("extra/os/x86_64/extra.db"),
                "vim-9.1-1/desc", desc("vim-9.1-1-x86_64.pkg.tar.zst", 2400000, "bbbb"));
        // not a repository database: must be ignored
        Files.writeString(root.resolve("core/os/x86_64/core.files"), "junk");

        Map<Path, ExpectedFile> files = new ArchDbParser().parse(root);

        assertEquals(2, files.size());
        assertEquals(new ExpectedFile(1900000, "aaaa"),
                files.get(root.resolve("core/os/x86_64/bash-5.3-1-x86_64.pkg.tar.zst")));
        assertEquals(new ExpectedFile(2400000, "bbbb"),
                files.get(root.resolve("extra/os/x86_64/vim-9.1-1-x86_64.pkg.tar.zst")));
    }

    @Test
    void rejectsNonGzipDb(@TempDir Path root) throws Exception {
        Path db = root.resolve("core/os/x86_64/core.db");
        Files.createDirectories(db.getParent());
        Files.write(db, new byte[]{0x28, (byte) 0xb5, 0x2f, (byte) 0xfd});
        assertThrows(IOException.class, () -> new ArchDbParser().parse(root));
    }

    private static String desc(String file, long size, String sha) {
        return "%FILENAME%\n" + file + "\n\n%NAME%\nx\n\n%CSIZE%\n" + size + "\n\n%SHA256SUM%\n" + sha + "\n\n";
    }

    /** Writes a tar.gz from (name, content) pairs; null content makes a directory entry. */
    private static void writeDb(Path db, String... entries) throws IOException {
        Files.createDirectories(db.getParent());
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (int i = 0; i < entries.length; i += 2) {
            byte[] data = entries[i + 1] == null ? new byte[0] : entries[i + 1].getBytes(StandardCharsets.UTF_8);
            byte[] h = new byte[512];
            byte[] name = entries[i].getBytes(StandardCharsets.UTF_8);
            System.arraycopy(name, 0, h, 0, name.length);
            byte[] size = String.format("%011o", data.length).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(size, 0, h, 124, size.length);
            h[156] = (byte) (entries[i + 1] == null ? '5' : '0');
            tar.write(h);
            tar.write(data);
            tar.write(new byte[(512 - data.length % 512) % 512]);
        }
        tar.write(new byte[1024]);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(db))) {
            out.write(tar.toByteArray());
        }
    }
}
