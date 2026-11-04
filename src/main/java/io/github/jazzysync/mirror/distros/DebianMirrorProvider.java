package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;
import io.github.jazzysync.mirror.core.*;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

public class DebianMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "debian";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        String sourceUrl = config.getDistroSourceUrl(name);
        String dist = config.getDistroProperty(name, "dist", "trixie");
        String arch = config.getDistroProperty(name, "arch", "amd64");
        String section = config.getDistroProperty(name, "section", "main,contrib,non-free,non-free-firmware");

        java.nio.file.Path targetDir = config.getDistroTarget(name);
        var repos = config.getDistroRepos(name);

        return new DebianMirror(name, sourceUrl, dist, arch, section, targetDir, logger, repos);
    }
}
