package io.github.jazzysync.mirror.checkers;

import io.github.jazzysync.mirror.api.ExpectedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks files against the size and SHA256 their repository index lists. */
public class HashVerifier {
    public enum Problem { MISSING, SIZE, SHA256 }

    public record Finding(Path file, Problem problem) {}

    private final String mirrorName;
    private final boolean showProgress;

    public HashVerifier(String mirrorName) {
        this(mirrorName, true);
    }

    public HashVerifier(String mirrorName, boolean showProgress) {
        this.mirrorName = mirrorName;
        this.showProgress = showProgress;
    }

    /** Returns the files that are missing, have the wrong size or the wrong hash, sorted by path. */
    public List<Finding> verify(Map<Path, ExpectedFile> expected) {
        if (expected.isEmpty()) return List.of();

        int total = expected.size();
        int threads = Math.min(8, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        List<Future<Finding>> futures = new ArrayList<>(total);
        for (var e : expected.entrySet()) {
            futures.add(executor.submit(() -> {
                try {
                    return check(e.getKey(), e.getValue());
                } finally {
                    done.incrementAndGet();
                }
            }));
        }

        ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor();
        if (showProgress) {
            progress.scheduleAtFixedRate(() -> {
                int d = done.get();
                System.out.printf("\r[%s]   checked %d / %d (%d%%)", mirrorName, d, total, d * 100L / total);
                System.out.flush();
            }, 1, 1, TimeUnit.SECONDS);
        }

        List<Finding> findings = new ArrayList<>();
        try {
            for (Future<Finding> f : futures) {
                Finding r = f.get();
                if (r != null) findings.add(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } finally {
            progress.shutdownNow();
            executor.shutdownNow();
            if (showProgress) System.out.println();
        }
        findings.sort((a, b) -> a.file().compareTo(b.file()));
        return findings;
    }

    static Finding check(Path file, ExpectedFile want) {
        try {
            if (want.size() >= 0 && Files.size(file) != want.size()) {
                return new Finding(file, Problem.SIZE);
            }
            return sha256(file).equalsIgnoreCase(want.sha256()) ? null : new Finding(file, Problem.SHA256);
        } catch (NoSuchFileException e) {
            return new Finding(file, Problem.MISSING);
        } catch (IOException e) {
            // Unreadable counts as corrupt: re-downloading is the fix either way.
            return new Finding(file, Problem.SHA256);
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[1 << 20];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
