package io.github.jazzysync.mirror.core;

import io.github.jazzysync.config.AppConfig;
import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;
import io.github.jazzysync.mirror.api.IMirror;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MirrorFactoryTest {

    private ConfigManager createTestConfigManager() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        cfg.setTargetDir("~/mirrors");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of(
            "arch", new AppConfig.DistroConfig("archlinux/", "arch", true),
            "fedora", new AppConfig.DistroConfig("fedora/", "fedora", true),
            "fedora-updates", new AppConfig.DistroConfig("updates/", "fedora", true)
        )));
        return new ConfigManager(cfg);
    }

    @Test
    void availableDistros_readsKeysFromConfig() {
        ConfigManager cm = createTestConfigManager();
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        var distros = factory.availableDistros();
        assertTrue(distros.contains("arch"));
        assertTrue(distros.contains("fedora"));
        assertTrue(distros.contains("fedora-updates"));
    }

    @Test
    void create_fedoraFamily() {
        ConfigManager cm = createTestConfigManager();
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        IMirror mirror = factory.create("fedora-updates");
        assertNotNull(mirror);
        assertEquals("fedora-updates", mirror.getName());
    }

    @Test
    void create_kaliFamily() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of(
            "kali", new AppConfig.DistroConfig("kali/", "kali", true)
        )));
        ConfigManager cm = new ConfigManager(cfg);
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        IMirror mirror = factory.create("kali");
        assertNotNull(mirror);
        assertEquals("kali", mirror.getName());
    }

    @Test
    void createAll_skipsDisabled() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of(
            "arch", new AppConfig.DistroConfig("archlinux/", "arch", false),
            "fedora", new AppConfig.DistroConfig("fedora/", "fedora", true)
        )));
        ConfigManager cm = new ConfigManager(cfg);
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        var mirrors = factory.createAll();
        assertEquals(1, mirrors.size());
        assertEquals("fedora", mirrors.get(0).getName());
    }

    @Test
    void create_unknownFamilyThrows() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of(
            "gentoo", new AppConfig.DistroConfig("gentoo/", "gentoo", true)
        )));
        ConfigManager cm = new ConfigManager(cfg);
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        assertThrows(IllegalArgumentException.class, () -> factory.create("gentoo"));
    }

    @Test
    void create_disabledThrows() {
        ConfigManager cm = createTestConfigManager();
        LogManager logger = new LogManager(Path.of("/tmp/logs"));
        MirrorFactory factory = new MirrorFactory(cm, logger);

        assertThrows(IllegalArgumentException.class, () -> factory.create("nonexistent"));
    }
}
