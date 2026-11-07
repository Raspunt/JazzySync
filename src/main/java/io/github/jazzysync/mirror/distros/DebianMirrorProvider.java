package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

public class DebianMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "debian";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        return DebianMirror.fromConfig(name, config, logger, "trixie");
    }
}
