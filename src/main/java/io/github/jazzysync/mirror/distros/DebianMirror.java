package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;
import io.github.jazzysync.mirror.core.*;
import io.github.jazzysync.mirror.strategies.*;
import io.github.jazzysync.mirror.checkers.*;
import io.github.jazzysync.mirror.metadata.DebianIndexParser;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DebianMirror extends AbstractMirror {
    private final String sourceUrl;
    private final String dist;
    private final String arch;
    private final String section;
    private final List<String> repos;
    private final int retries;
    private final int retryDelaySeconds;

    public DebianMirror(String sourceUrl, String dist, String arch, String section,
                        Path targetDir, LogManager logger, List<String> repos) {
        this("debian", sourceUrl, dist, arch, section, targetDir, logger, repos);
    }

    public DebianMirror(String name, String sourceUrl, String dist, String arch, String section,
                        Path targetDir, LogManager logger, List<String> repos) {
        this(name, sourceUrl, dist, arch, section, "rsync", 0, 0, targetDir, logger, repos);
    }

    public DebianMirror(String name, String sourceUrl, String dist, String arch, String section,
                        String method, int retries, int retryDelaySeconds,
                        Path targetDir, LogManager logger, List<String> repos) {
        super(name, targetDir, logger,
              buildSyncStrategy(dist, arch, effectiveSection(section, repos), method),
              buildCheckers(logger, name));
        this.sourceUrl = sourceUrl;
        this.dist = dist;
        this.arch = arch;
        this.section = effectiveSection(section, repos);
        this.repos = repos != null ? repos : List.of();
        this.retries = Math.max(0, retries);
        this.retryDelaySeconds = Math.max(0, retryDelaySeconds);
    }

    /**
     * Builds a Debian-style mirror from config. Properties: dist, arch, section,
     * method (rsync|http|https|ftp, default rsync), retries (default 5), retryDelay (seconds, default 60).
     */
    public static DebianMirror fromConfig(String name, ConfigManager config, LogManager logger, String defaultDist) {
        String dist = config.getDistroProperty(name, "dist", defaultDist);
        String arch = config.getDistroProperty(name, "arch", "amd64");
        String section = config.getDistroProperty(name, "section", "main,contrib,non-free,non-free-firmware");
        String method = config.getDistroProperty(name, "method", "rsync");
        int retries = parseInt(config.getDistroProperty(name, "retries", "5"), 5);
        int retryDelay = parseInt(config.getDistroProperty(name, "retryDelay", "60"), 60);
        return new DebianMirror(name, config.getDistroSourceUrl(name), dist, arch, section,
                                method, retries, retryDelay,
                                config.getDistroTarget(name), logger, config.getDistroRepos(name));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** `repos` from the config overrides the `section` property (as documented in README). */
    private static String effectiveSection(String section, List<String> repos) {
        if (repos != null && !repos.isEmpty()) {
            return String.join(",", repos);
        }
        return section;
    }

    private static SyncStrategy buildSyncStrategy(String dist, String arch, String section, String method) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "command -v debmirror");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                return new DebmirrorStrategy(dist, arch, section, method);
            }
        } catch (Exception e) {
            // debmirror not available
        }
        return new RsyncStrategy();
    }

    private static List<IntegrityChecker> buildCheckers(LogManager logger, String name) {
        List<IntegrityChecker> checkers = new ArrayList<>();
        var dpkgChecker = new ExternalToolChecker(List.of("dpkg-deb", "-I"), 60, logger, name);
        if (dpkgChecker.isAvailable()) {
            checkers.add(dpkgChecker);
        }
        var arTarChecker = new ArTarChecker(60);
        if (arTarChecker.isAvailable()) {
            checkers.add(arTarChecker);
        }
        return checkers;
    }

    @Override
    public void checkDependencies() {
        try {
            ProcessResult r = runProcess(List.of("sh", "-c", "command -v rsync"), 1);
            if (r.exitCode() != 0) {
                throw new RuntimeException("Missing dependency: rsync is not installed");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check dependencies", e);
        }
    }

    @Override
    public void syncRepo() {
        logger.info("[" + name + "] syncing " + dist + "...");
        try {
            ensureTargetDir();
            List<String> cmd = syncStrategy.buildSyncCommand(sourceUrl, targetDir);
            // Mirrors sometimes drop long transfers ("Connection reset by peer").
            // Files already fetched are kept, so each retry resumes where the last one stopped.
            for (int attempt = 0; ; attempt++) {
                ProcessResult result = runProcess(cmd, 180);
                if (result.exitCode() == 0) {
                    logger.info("[" + name + "] done");
                    return;
                }
                logger.error("[" + name + "] Synchronization failed with exit code: " + result.exitCode());
                if (attempt >= retries) {
                    if (retries > 0) {
                        logger.error("[" + name + "] giving up after " + (retries + 1) + " attempts");
                    }
                    return;
                }
                logger.warn("[" + name + "] retrying in " + retryDelaySeconds + "s (attempt "
                            + (attempt + 2) + " of " + (retries + 1) + ")");
                Thread.sleep(retryDelaySeconds * 1000L);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Synchronization error: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void checkRepo() {
        logger.info("[" + name + "] checking " + dist + " for updates...");
        try {
            List<String> cmd = syncStrategy.buildCheckCommand(sourceUrl, targetDir);
            ProcessResult result = runProcess(cmd, 60);
            if (result.exitCode() == 0) {
                logger.info("[" + name + "] Check completed");
            } else {
                logger.error("[" + name + "] Check failed with exit code: " + result.exitCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Check error: " + e.getMessage());
        }
    }

    @Override
    protected MetadataParser metadataParser() {
        return new DebianIndexParser(dist);
    }

    @Override
    public void verifyRepo() {
        List<HashVerifier.Finding> findings = verifyAgainstIndex();
        if (findings != null) {
            reportFindings(findings, name);
            return;
        }
        Path pool = targetDir.resolve("pool");
        if (!Files.isDirectory(pool)) {
            logger.error("[" + name + "] error: " + pool + " not found");
            return;
        }
        logger.info("[" + name + "] verifying all .deb files in " + pool + " ...");
        try {
            List<Path> packages = Files.walk(pool)
                .filter(p -> p.toString().endsWith(".deb"))
                .collect(Collectors.toList());

            if (packages.isEmpty()) {
                logger.info("[" + name + "] no .deb files found");
                return;
            }

            if (integrityCheckers.isEmpty()) {
                logger.info("[" + name + "] dpkg-deb/ar not available, skipping integrity check for " + packages.size() + " package(s)");
                return;
            }

            List<Path> bad = verifyWithCheckers(packages);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .deb files are healthy");
            } else {
                logger.warn("[" + name + "] found " + bad.size() + " corrupt package(s):");
                for (Path p : bad) {
                    logger.warn("  CORRUPT: " + p);
                }
                logger.warn("[" + name + "] run 'jazzy fix " + name + "' to remove and re-download them");
                logPackageWord(bad.size());
            }
        } catch (IOException e) {
            logger.error("[" + name + "] Verification error: " + e.getMessage());
        }
    }

    @Override
    public void fixRepo() {
        List<HashVerifier.Finding> findings = verifyAgainstIndex();
        if (findings != null) {
            if (findings.isEmpty()) {
                logger.info("[" + name + "] all packages match the index, nothing to fix");
                return;
            }
            reportFindings(findings, name);
            try {
                removeCorrupt(findings);
            } catch (IOException e) {
                logger.error("[" + name + "] Fix error: " + e.getMessage());
                return;
            }
            logger.info("[" + name + "] re-downloading missing packages...");
            syncRepo();
            logger.info("[" + name + "] fix done, run 'jazzy verify " + name + "' to confirm");
            return;
        }
        Path pool = targetDir.resolve("pool");
        if (!Files.isDirectory(pool)) {
            logger.error("[" + name + "] error: " + pool + " not found");
            return;
        }
        logger.info("[" + name + "] scanning for corrupt packages in " + pool + " ...");
        try {
            List<Path> packages = Files.walk(pool)
                .filter(p -> p.toString().endsWith(".deb"))
                .collect(Collectors.toList());

            if (integrityCheckers.isEmpty()) {
                logger.info("[" + name + "] dpkg-deb/ar not available, cannot verify packages; running sync to ensure consistency");
                syncRepo();
                return;
            }

            List<Path> bad = verifyWithCheckers(packages);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .deb files are healthy, nothing to fix");
                return;
            }

            logger.info("[" + name + "] found corrupt packages, removing...");
            for (Path p : bad) {
                logger.info("[" + name + "] REMOVING: " + p);
                Files.deleteIfExists(p);
            }

            logger.info("[" + name + "] re-downloading missing packages...");
            syncRepo();

            logger.info("[" + name + "] fix done");
        } catch (IOException e) {
            logger.error("[" + name + "] Fix error: " + e.getMessage());
        }
    }

    @Override
    protected Path getLastSyncFile() {
        return targetDir.resolve("dists").resolve(dist).resolve("Release");
    }

    @Override
    protected String checkUpToDateStatus() {
        Instant lastSync = getLastSyncTime(getLastSyncFile());
        if (lastSync == null) return "unknown";
        long days = Duration.between(lastSync, Instant.now()).toDays();
        if (days < 2) return "yes";
        if (days < 7) return "likely";
        return "stale (" + days + "d)";
    }

    @Override
    protected String getReposDisplay() {
        return repos.isEmpty() ? section : String.join(", ", repos);
    }

    private void logPackageWord(int count) {
        String word;
        if (count % 10 == 1 && count % 100 != 11) {
            word = "пакет";
        } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
            word = "пакета";
        } else {
            word = "пакетов";
        }
        logger.warn("[" + name + "] " + count + " " + word + " сломано");
    }
}
