package io.github.jazzysync.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    private AppConfig createTestConfig() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        cfg.setTargetDir("~/mirrors");
        cfg.setLogDir("~/.cache/jazzy");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of(
            "arch", new AppConfig.DistroConfig("archlinux/", "arch", true),
            "debian", new AppConfig.DistroConfig("debian/", "debian", true),
            "fedora", new AppConfig.DistroConfig("fedora/linux/releases/44/", "fedora", true),
            "fedora-updates", new AppConfig.DistroConfig("fedora/linux/updates/44/", "fedora", true)
        )));
        return cfg;
    }

    @Test
    void getDistroTarget_defaultMapping() {
        ConfigManager cm = new ConfigManager(createTestConfig());
        assertEquals(Path.of(System.getProperty("user.home")).resolve("mirrors/archlinux"), cm.getDistroTarget("arch"));
        assertEquals(Path.of(System.getProperty("user.home")).resolve("mirrors/debian"), cm.getDistroTarget("debian"));
        assertEquals(Path.of(System.getProperty("user.home")).resolve("mirrors/fedora"), cm.getDistroTarget("fedora"));
        assertEquals(Path.of(System.getProperty("user.home")).resolve("mirrors/fedora-updates"), cm.getDistroTarget("fedora-updates"));
    }

    @Test
    void getDistroTarget_withOverride() {
        ConfigManager cm = new ConfigManager(createTestConfig(), "/opt/mirrors");
        assertEquals(Path.of("/opt/mirrors/archlinux"), cm.getDistroTarget("arch"));
    }

    @Test
    void getDistroFamily_fallbackToName() {
        ConfigManager cm = new ConfigManager(createTestConfig());
        assertEquals("arch", cm.getDistroFamily("arch"));
        assertEquals("debian", cm.getDistroFamily("debian"));
        assertEquals("fedora", cm.getDistroFamily("fedora"));
        assertEquals("fedora", cm.getDistroFamily("fedora-updates"));
    }

    @Test
    void getDistroFamily_readsFromConfig() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig custom = new AppConfig.DistroConfig("custom/", "fedora", true);
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("my-custom", custom)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals("fedora", cm.getDistroFamily("my-custom"));
    }

    @Test
    void getDistroSourceUrls_singlePath() {
        ConfigManager cm = new ConfigManager(createTestConfig());
        List<String> urls = cm.getDistroSourceUrls("fedora");
        assertEquals(List.of("rsync://example.com/fedora/linux/releases/44/"), urls);
    }

    @Test
    void getDistroSourceUrls_multiplePaths() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig();
        dc.setSourcePaths(List.of("path/a/", "path/b/"));
        dc.setEnabled(true);
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        List<String> urls = cm.getDistroSourceUrls("fedora");
        assertEquals(List.of("rsync://example.com/path/a/", "rsync://example.com/path/b/"), urls);
    }

    @Test
    void getDistroRepos_returnsEmptyListWhenNull() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertTrue(cm.getDistroRepos("fedora").isEmpty());
    }

    @Test
    void getDistroRepos_returnsValues() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        dc.setRepos(List.of("Everything", "Workstation"));
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals(List.of("Everything", "Workstation"), cm.getDistroRepos("fedora"));
    }

    @Test
    void getDistroExcludes_returnsEmptyListWhenNull() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertTrue(cm.getDistroExcludes("fedora").isEmpty());
    }

    @Test
    void getDistroExcludes_returnsValues() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        dc.setExcludes(List.of("Everything/aarch64", "Everything/debug"));
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals(List.of("Everything/aarch64", "Everything/debug"), cm.getDistroExcludes("fedora"));
    }

    @Test
    void getBaseUrl_normalizesTrailingSlash() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://example.com");
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals("rsync://example.com/", cm.getBaseUrl());
    }

    @Test
    void getDistroBaseUrl_usesGlobalWhenNotSet() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://global.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals("rsync://global.com/", cm.getDistroBaseUrl("fedora"));
    }

    @Test
    void getDistroBaseUrl_usesDistroOverride() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://global.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("fedora/", "fedora", true);
        dc.setBaseUrl("rsync://special.com");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals("rsync://special.com/", cm.getDistroBaseUrl("fedora"));
    }

    @Test
    void getDistroSourceUrl_usesDistroBaseUrl() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl("rsync://global.com/");
        AppConfig.DistroConfig dc = new AppConfig.DistroConfig("path/", "fedora", true);
        dc.setBaseUrl("rsync://special.com/");
        cfg.setDistros(new java.util.LinkedHashMap<>(java.util.Map.of("fedora", dc)));
        ConfigManager cm = new ConfigManager(cfg);
        assertEquals("rsync://special.com/path/", cm.getDistroSourceUrl("fedora"));
    }
}
