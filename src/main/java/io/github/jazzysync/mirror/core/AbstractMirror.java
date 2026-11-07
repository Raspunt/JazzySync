package io.github.jazzysync.mirror.core;

import io.github.jazzysync.mirror.api.*;
import io.github.jazzysync.mirror.checkers.HashVerifier;

import io.github.jazzysync.logging.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class AbstractMirror implements IMirror {
    protected final String name;
    protected final Path targetDir;
    protected final LogManager logger;
    protected final SyncStrategy syncStrategy;
    protected final List<IntegrityChecker> integrityCheckers;

    protected AbstractMirror(String name, Path targetDir, LogManager logger,
                             SyncStrategy syncStrategy, List<IntegrityChecker> integrityCheckers) {
        this.name = name;
        this.targetDir = targetDir;
        this.logger = logger;
        this.syncStrategy = syncStrategy;
        this.integrityCheckers = integrityCheckers != null ? integrityCheckers : List.of();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void checkDependencies() {
        // override where needed
    }

    protected void ensureTargetDir() throws IOException {
        Files.createDirectories(targetDir);
    }

    protected ProcessResult runProcess(List<String> command, long timeoutMinutes) throws IOException, InterruptedException {
        logger.info("[" + name + "] Executing: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                logger.info("[" + name + "] " + line);
            }
        }

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            logger.error("[" + name + "] Process timed out after " + timeoutMinutes + " minutes");
            return new ProcessResult(-1, output);
        }
        return new ProcessResult(process.exitValue(), output);
    }

    protected ProcessResult runProcess(List<String> command) throws IOException, InterruptedException {
        return runProcess(command, 30);
    }

    protected ProcessResult runProcessQuiet(List<String> command, long timeoutMinutes) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, output);
        }
        return new ProcessResult(process.exitValue(), output);
    }

    protected long getDirSize(Path dir) {
        try {
            ProcessResult r = runProcessQuiet(List.of("du", "-sb", dir.toString()), 1);
            if (r.exitCode() == 0 && !r.output().isEmpty()) {
                String line = r.output().get(0).trim();
                String[] parts = line.split("\\s+");
                return Long.parseLong(parts[0]);
            }
        } catch (Exception e) {
            logger.warn("[" + name + "] Failed to get directory size: " + e.getMessage());
        }
        return -1;
    }

    protected Instant getLastSyncTime(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.getLastModifiedTime(file).toInstant();
            }
        } catch (IOException e) {
            logger.warn("[" + name + "] Failed to get last sync time: " + e.getMessage());
        }
        return null;
    }

    public final MirrorStatus getStatus() {
        long size = getDirSize(targetDir);
        Instant lastSync = getLastSyncTime(getLastSyncFile());
        String upToDate = checkUpToDateStatus();
        String repos = getReposDisplay();
        return new MirrorStatus(name, size, lastSync, upToDate, repos);
    }

    protected abstract Path getLastSyncFile();
    protected abstract String checkUpToDateStatus();
    protected abstract String getReposDisplay();

    /** Parser for this mirror's repository indexes, or null if it has none. */
    protected MetadataParser metadataParser() {
        return null;
    }

    /**
     * Checks every file listed in the repository indexes for presence, size and SHA256.
     * Returns null when there is no readable index, so callers can fall back to other checks.
     */
    protected List<HashVerifier.Finding> verifyAgainstIndex() {
        MetadataParser parser = metadataParser();
        if (parser == null) return null;
        Map<Path, ExpectedFile> expected;
        try {
            expected = parser.parse(targetDir);
        } catch (IOException | RuntimeException e) {
            logger.error("[" + name + "] cannot read repository index: " + e.getMessage());
            return null;
        }
        if (expected.isEmpty()) {
            logger.warn("[" + name + "] no repository index found in " + targetDir);
            return null;
        }
        logger.info("[" + name + "] checking " + expected.size() + " package(s) against the index (size + SHA256)...");
        return new HashVerifier(name).verify(expected);
    }

    /** Logs the findings of {@link #verifyAgainstIndex()}. */
    protected void reportFindings(List<HashVerifier.Finding> findings, String fixTarget) {
        if (findings.isEmpty()) {
            logger.info("[" + name + "] all packages match the index");
            return;
        }
        long missing = findings.stream().filter(f -> f.problem() == HashVerifier.Problem.MISSING).count();
        long corrupt = findings.size() - missing;
        for (HashVerifier.Finding f : findings) {
            String tag = switch (f.problem()) {
                case MISSING -> "MISSING";
                case SIZE -> "BAD SIZE";
                case SHA256 -> "BAD SHA256";
            };
            logger.warn("  " + tag + ": " + f.file());
        }
        logger.warn("[" + name + "] " + missing + " missing, " + corrupt + " corrupt");
        logger.warn("[" + name + "] run 'jazzy fix " + fixTarget + "' to remove and re-download them");
    }

    /** Deletes the corrupt files among the findings (missing ones are already gone). */
    protected void removeCorrupt(List<HashVerifier.Finding> findings, String... siblingSuffixes) throws IOException {
        for (HashVerifier.Finding f : findings) {
            if (f.problem() == HashVerifier.Problem.MISSING) continue;
            logger.info("[" + name + "] REMOVING: " + f.file());
            Files.deleteIfExists(f.file());
            for (String suffix : siblingSuffixes) {
                Files.deleteIfExists(Path.of(f.file() + suffix));
            }
        }
    }

    protected List<Path> verifyWithCheckers(List<Path> files) {
        for (IntegrityChecker checker : integrityCheckers) {
            if (checker.isAvailable()) {
                return checker.verify(files);
            }
        }
        logger.warn("[" + name + "] No integrity checker available");
        return List.of();
    }

    protected record ProcessResult(int exitCode, List<String> output) {}
}
