package io.github.jazzysync.mirror.checkers;

import io.github.jazzysync.mirror.api.ExpectedFile;
import io.github.jazzysync.mirror.checkers.HashVerifier.Finding;
import io.github.jazzysync.mirror.checkers.HashVerifier.Problem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HashVerifierTest {
    // sha256("hello\n")
    private static final String HELLO = "5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03";

    @Test
    void reportsMissingWrongSizeAndWrongHash(@TempDir Path dir) throws Exception {
        Path good = Files.writeString(dir.resolve("a-good.deb"), "hello\n");
        Path shortFile = Files.writeString(dir.resolve("b-short.deb"), "hel");
        Path tampered = Files.writeString(dir.resolve("c-tampered.deb"), "HELLO\n");
        Path missing = dir.resolve("d-missing.deb");

        List<Finding> findings = new HashVerifier("test", false).verify(Map.of(
                good, new ExpectedFile(6, HELLO),
                shortFile, new ExpectedFile(6, HELLO),
                tampered, new ExpectedFile(6, HELLO),
                missing, new ExpectedFile(6, HELLO)));

        assertEquals(List.of(
                new Finding(shortFile, Problem.SIZE),
                new Finding(tampered, Problem.SHA256),
                new Finding(missing, Problem.MISSING)), findings);
    }

    @Test
    void unknownSizeStillChecksHash(@TempDir Path dir) throws Exception {
        Path f = Files.writeString(dir.resolve("x.deb"), "hello\n");
        assertNull(HashVerifier.check(f, new ExpectedFile(-1, HELLO.toUpperCase())));
        assertEquals(Problem.SHA256, HashVerifier.check(f, new ExpectedFile(-1, "00")).problem());
    }
}
