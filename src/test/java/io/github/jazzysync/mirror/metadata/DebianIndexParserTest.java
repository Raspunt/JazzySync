package io.github.jazzysync.mirror.metadata;

import io.github.jazzysync.mirror.api.ExpectedFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DebianIndexParserTest {

    private static final String PACKAGES = """
            Package: hello
            Version: 2.10-3
            Filename: pool/main/h/hello/hello_2.10-3_amd64.deb
            Size: 53248
            MD5sum: 0123
            SHA256: aaaa

            Package: tzdata
            Filename: pool/main/t/tzdata/tzdata_2025b-4_all.deb
            Size: 260000
            SHA256: bbbb
            """;

    @Test
    void parsesGzipPackagesFromAllComponents(@TempDir Path root) throws Exception {
        writeGz(root.resolve("dists/trixie/main/binary-amd64/Packages.gz"), PACKAGES);
        writeGz(root.resolve("dists/trixie/contrib/binary-amd64/Packages.gz"), """
                Package: foo
                Filename: pool/contrib/f/foo/foo_1_amd64.deb
                Size: 10
                SHA256: cccc
                """);

        Map<Path, ExpectedFile> files = new DebianIndexParser("trixie").parse(root);

        assertEquals(3, files.size());
        assertEquals(new ExpectedFile(53248, "aaaa"),
                files.get(root.resolve("pool/main/h/hello/hello_2.10-3_amd64.deb")));
        assertEquals(new ExpectedFile(260000, "bbbb"),
                files.get(root.resolve("pool/main/t/tzdata/tzdata_2025b-4_all.deb")));
        assertEquals(new ExpectedFile(10, "cccc"),
                files.get(root.resolve("pool/contrib/f/foo/foo_1_amd64.deb")));
    }

    @Test
    void readsPlainPackages(@TempDir Path root) throws Exception {
        Path p = root.resolve("dists/trixie/main/binary-amd64/Packages");
        Files.createDirectories(p.getParent());
        Files.writeString(p, PACKAGES);
        assertEquals(2, new DebianIndexParser("trixie").parse(root).size());
    }

    @Test
    void missingDistGivesEmptyMap(@TempDir Path root) throws Exception {
        assertTrue(new DebianIndexParser("trixie").parse(root).isEmpty());
    }

    private static void writeGz(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
