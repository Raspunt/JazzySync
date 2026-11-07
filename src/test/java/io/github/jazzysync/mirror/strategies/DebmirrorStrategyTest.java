package io.github.jazzysync.mirror.strategies;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebmirrorStrategyTest {

    @Test
    void defaultsToRsync() {
        var s = new DebmirrorStrategy("trixie", "amd64", "main");
        List<String> cmd = s.buildSyncCommand("rsync://mirror.ps.kz/debian/", Paths.get("/target"));
        assertTrue(cmd.contains("--method=rsync"));
        assertTrue(cmd.contains("--host=mirror.ps.kz"));
        assertTrue(cmd.contains("--root=/debian"));
        assertFalse(cmd.contains("--rsync-extra=none"));
        assertEquals("/target", cmd.get(cmd.size() - 1));
    }

    @Test
    void httpMethodDisablesRsyncExtras() {
        var s = new DebmirrorStrategy("trixie-security", "amd64", "main", "http");
        List<String> cmd = s.buildSyncCommand("http://security.debian.org/debian-security/", Paths.get("/target"));
        assertTrue(cmd.contains("--method=http"));
        assertTrue(cmd.contains("--host=security.debian.org"));
        assertTrue(cmd.contains("--root=/debian-security"));
        assertTrue(cmd.contains("--rsync-extra=none"));
        assertEquals("/target", cmd.get(cmd.size() - 1));
    }
}
