package io.github.jazzysync.mirror.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface MetadataParser {
    /**
     * Reads the repository indexes under {@code repoRoot} and returns every package file
     * they list, keyed by absolute path. Returns an empty map when no index is found.
     */
    Map<Path, ExpectedFile> parse(Path repoRoot) throws IOException;
}
