package io.github.jazzysync.config;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_DIR = "~/.config/jazzy";
    private static final String CONFIG_FILE = "config.toml";

    private final AppConfig config;
    private final Path targetDir;
    private final Path logDir;

    public ConfigManager() {
        this.config = loadOrCreateConfig();
        this.targetDir = expandHome(config.getTargetDir());
        this.logDir = expandHome(config.getLogDir());

    }

    public ConfigManager(String targetDirOverride) {
        this(loadOrCreateConfig(), targetDirOverride);
    }

    public ConfigManager(AppConfig config, String targetDirOverride) {
        this.config = config;
        this.targetDir = expandHome(targetDirOverride);
        this.logDir = expandHome(config.getLogDir());
    }

    public ConfigManager(AppConfig config) {
        this.config = config;
        this.targetDir = expandHome(config.getTargetDir());
        this.logDir = expandHome(config.getLogDir());
    }

    private static AppConfig loadOrCreateConfig() {
        Path configDir = expandHome(Paths.get(CONFIG_DIR));
        Path configFile = configDir.resolve(CONFIG_FILE);

        boolean created = false;
        AppConfig config;

        if (Files.exists(configFile)) {
            try {
                TomlParseResult result = Toml.parse(configFile);
                config = parseConfig(result);
            } catch (Exception e) {
                System.err.println("Warning: failed to read config, using defaults. " + e.getMessage());
                config = new AppConfig();
            }
        } else {
            config = new AppConfig();
            try {
                Files.createDirectories(configDir);
                Files.writeString(configFile, toToml(config));
                System.out.println("Created default config: " + configFile);
                created = true;
            } catch (IOException e) {
                System.err.println("Warning: failed to create default config. " + e.getMessage());
            }
        }

        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("baseUrl is not configured. Please set baseUrl in ").append(configFile).append("\n");
            sb.append("Example: baseUrl = \"rsync://mirror.yandex.ru/\"");
            if (created) {
                sb.append("\nA default config has been created for you.");
            }
            throw new IllegalStateException(sb.toString());
        }

        return config;
    }

    private static AppConfig parseConfig(TomlParseResult toml) {
        AppConfig config = new AppConfig();
        config.setBaseUrl(toml.getString("baseUrl"));
        config.setTargetDir(toml.getString("targetDir", () -> "~/mirrors"));
        config.setLogDir(toml.getString("logDir", () -> "~/.cache/jazzy"));

        TomlTable distrosTable = toml.getTable("distros");
        if (distrosTable != null) {
            LinkedHashMap<String, AppConfig.DistroConfig> distros = new LinkedHashMap<>();
            for (String name : distrosTable.keySet()) {
                TomlTable t = distrosTable.getTable(name);
                if (t == null) continue;
                AppConfig.DistroConfig dc = new AppConfig.DistroConfig();
                dc.setSourcePath(t.getString("sourcePath"));
                dc.setFamily(t.getString("family"));
                dc.setEnabled(t.getBoolean("enabled", () -> true));
                dc.setBaseUrl(t.getString("baseUrl"));
                dc.setRepos(getStringList(t, "repos"));
                dc.setExcludes(getStringList(t, "excludes"));
                dc.setSourcePaths(getStringList(t, "sourcePaths"));
                dc.setProperties(getStringMap(t, "properties"));
                distros.put(name, dc);
            }
            config.setDistros(distros);
        }

        return config;
    }

    private static List<String> getStringList(TomlTable table, String key) {
        TomlArray arr = table.getArray(key);
        if (arr == null) return null;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            result.add(arr.getString(i));
        }
        return result;
    }

    private static Map<String, String> getStringMap(TomlTable table, String key) {
        TomlTable t = table.getTable(key);
        if (t == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (String k : t.keySet()) {
            result.put(k, t.getString(k));
        }
        return result;
    }

    private static String toToml(AppConfig config) {
        StringBuilder sb = new StringBuilder();
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            sb.append("baseUrl = \"").append(escape(config.getBaseUrl())).append("\"\n");
        } else {
            sb.append("# Set your mirror base URL, e.g.:\n");
            sb.append("# baseUrl = \"rsync://mirror.yandex.ru/\"\n");
        }
        sb.append("targetDir = \"").append(escape(config.getTargetDir())).append("\"\n");
        sb.append("logDir = \"").append(escape(config.getLogDir())).append("\"\n\n");
        for (Map.Entry<String, AppConfig.DistroConfig> e : config.getDistros().entrySet()) {
            sb.append("[distros.").append(e.getKey()).append("]\n");
            AppConfig.DistroConfig dc = e.getValue();
            if (dc.getSourcePath() != null) {
                sb.append("sourcePath = \"").append(escape(dc.getSourcePath())).append("\"\n");
            }
            if (dc.getFamily() != null) {
                sb.append("family = \"").append(escape(dc.getFamily())).append("\"\n");
            }
            sb.append("enabled = ").append(dc.isEnabled()).append("\n");
            if (dc.getBaseUrl() != null) {
                sb.append("baseUrl = \"").append(escape(dc.getBaseUrl())).append("\"\n");
            }
            appendStringList(sb, "repos", dc.getRepos());
            appendStringList(sb, "excludes", dc.getExcludes());
            appendStringList(sb, "sourcePaths", dc.getSourcePaths());
            if (dc.getProperties() != null && !dc.getProperties().isEmpty()) {
                sb.append("[distros.").append(e.getKey()).append(".properties]\n");
                for (Map.Entry<String, String> pe : dc.getProperties().entrySet()) {
                    sb.append(pe.getKey()).append(" = \"").append(escape(pe.getValue())).append("\"\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void appendStringList(StringBuilder sb, String key, List<String> list) {
        if (list == null || list.isEmpty()) return;
        sb.append(key).append(" = [");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(escape(list.get(i))).append("\"");
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static Path expandHome(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            String home = System.getenv("HOME");
            if (home == null) {
                home = System.getProperty("user.home");
            }
            path = home + path.substring(1);
        }
        return Paths.get(path);
    }

    private static Path expandHome(Path path) {
        return expandHome(path.toString());
    }

    public String getBaseUrl() {
        String url = config.getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                "baseUrl is not configured. Please set baseUrl in ~/.config/jazzy/config.toml"
            );
        }
        return url.endsWith("/") ? url : url + "/";
    }

    public Path getTargetDir() {
        return targetDir;
    }

    public Path getLogDir() {
        return logDir;
    }

    public Path getDistroTarget(String distro) {
        String subdir = switch (distro) {
            case "arch", "archlinux" -> "archlinux";
            case "debian" -> "debian";
            default -> distro;
        };
        return targetDir.resolve(subdir);
    }

    public String getDistroBaseUrl(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String url = dc.getBaseUrl();
        if (url != null && !url.isBlank()) {
            return url.endsWith("/") ? url : url + "/";
        }
        return getBaseUrl();
    }

    public String getDistroSourceUrl(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String path = dc.getSourcePath();
        return getDistroBaseUrl(distro) + (path.endsWith("/") ? path : path + "/");
    }

    public List<String> getDistroSourceUrls(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String base = getDistroBaseUrl(distro);
        List<String> paths = dc.getSourcePaths();
        if (paths != null && !paths.isEmpty()) {
            return paths.stream()
                .map(p -> base + (p.endsWith("/") ? p : p + "/"))
                .toList();
        }
        String path = dc.getSourcePath();
        return List.of(base + (path.endsWith("/") ? path : path + "/"));
    }

    public boolean isDistroEnabled(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        return dc != null && dc.isEnabled();
    }

    public AppConfig.DistroConfig getDistroConfig(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        return dc;
    }

    public String getDistroFamily(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        String family = dc.getFamily();
        if (family != null && !family.isBlank()) {
            return family;
        }
        // Fallback for legacy configs without family field
        return switch (distro) {
            case "arch", "archlinux", "manjaro" -> "arch";
            case "debian", "ubuntu", "mint" -> "debian";
            default -> distro;
        };
    }

    public List<String> getDistroRepos(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getRepos() == null) {
            return List.of();
        }
        return dc.getRepos();
    }

    public List<String> getDistroExcludes(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getExcludes() == null) {
            return List.of();
        }
        return dc.getExcludes();
    }

    public Map<String, String> getDistroProperties(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getProperties() == null) {
            return Map.of();
        }
        return dc.getProperties();
    }

    public String getDistroProperty(String distro, String key, String defaultValue) {
        return getDistroProperties(distro).getOrDefault(key, defaultValue);
    }

    public AppConfig getRawConfig() {
        return config;
    }
}
