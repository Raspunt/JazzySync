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
        return DebianMirror.fromConfig(name, config, logger, "kali-rolling");
    }
}
