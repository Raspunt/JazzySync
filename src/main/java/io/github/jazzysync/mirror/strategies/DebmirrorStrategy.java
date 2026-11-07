package io.github.jazzysync.mirror.strategies;

import io.github.jazzysync.mirror.api.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DebmirrorStrategy implements SyncStrategy {
    private final String dist;
    private final String arch;
    private final String section;
    private final String method;

    public DebmirrorStrategy(String dist, String arch, String section) {
        this(dist, arch, section, "rsync");
    }

    /** method: rsync, http, https or ftp (passed to debmirror --method). */
    public DebmirrorStrategy(String dist, String arch, String section, String method) {
        this.dist = dist;
        this.arch = arch;
        this.section = section;
        this.method = method == null || method.isBlank() ? "rsync" : method;
    }

    @Override
    public List<String> buildSyncCommand(String sourceUrl, Path targetDir) {
        return buildCommand(sourceUrl, targetDir, false);
    }

    @Override
    public List<String> buildCheckCommand(String sourceUrl, Path targetDir) {
        return buildCommand(sourceUrl, targetDir, true);
    }

    private List<String> buildCommand(String sourceUrl, Path targetDir, boolean dryRun) {
        URI uri = URI.create(sourceUrl);

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Cannot extract host from source URL: " + sourceUrl);
        }
        if (uri.getPort() != -1) {
            host += ":" + uri.getPort();
        }

        String root = uri.getPath();
        if (root == null || root.isEmpty()) {
            root = "/";
        } else if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.isEmpty()) {
            root = "/";
        }

        List<String> cmd = new ArrayList<>(List.of(
            "debmirror",
            "--host=" + host,
            "--root=" + root,
            "--method=" + method,
            "--dist=" + dist,
            "--arch=" + arch,
            "--section=" + section,
            "--getcontents",
            "--nosource",
            "--progress",
            "--ignore-release-gpg",
            targetDir.toString()
        ));
        if (!"rsync".equals(method)) {
            // by default debmirror still fetches doc/trace dirs over rsync, which http-only hosts lack
            cmd.add(cmd.size() - 1, "--rsync-extra=none");
        }
        if (dryRun) {
            cmd.add("--dry-run");
        }
        return cmd;
    }
}
