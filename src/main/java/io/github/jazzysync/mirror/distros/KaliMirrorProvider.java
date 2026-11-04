package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

/**
 * Kali Linux is a Debian-based archive (dists/ + pool/), so it reuses DebianMirror
 * with Kali-specific defaults.
 */
public class KaliMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "kali";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        String sourceUrl = config.getDistroSourceUrl(name);
        String dist = config.getDistroProperty(name, "dist", "kali-rolling");
        String arch = config.getDistroProperty(name, "arch", "amd64");
        String section = config.getDistroProperty(name, "section", "main,contrib,non-free,non-free-firmware");

        java.nio.file.Path targetDir = config.getDistroTarget(name);
        var repos = config.getDistroRepos(name);

        return new DebianMirror(name, sourceUrl, dist, arch, section, targetDir, logger, repos);
    }
}
