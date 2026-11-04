package io.github.jazzysync.config;

import java.util.List;
import java.util.Map;

public class AppConfig {
    private String baseUrl;
    private String targetDir = "~/mirrors";
    private String logDir = "~/.cache/jazzy";
    private java.util.LinkedHashMap<String, DistroConfig> distros = defaultDistros();

    private static java.util.LinkedHashMap<String, DistroConfig> defaultDistros() {
        var d = new java.util.LinkedHashMap<String, DistroConfig>();
        d.put("arch", new DistroConfig("archlinux/", "arch", true));
        d.put("debian", new DistroConfig("debian/", "debian", true));
        d.put("fedora", new DistroConfig("fedora/linux/releases/44/Everything/x86_64/os/", "fedora", true));
        // Disabled by default: mirror.yandex.ru has no Kali, so it needs its own mirror
        DistroConfig kali = new DistroConfig("kali/", "kali", false);
        kali.setBaseUrl("rsync://mirror.truenetwork.ru/");
        d.put("kali", kali);
        return d;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTargetDir() { return targetDir; }
    public void setTargetDir(String targetDir) { this.targetDir = targetDir; }

    public String getLogDir() { return logDir; }
    public void setLogDir(String logDir) { this.logDir = logDir; }

    public java.util.LinkedHashMap<String, DistroConfig> getDistros() { return distros; }
    public void setDistros(java.util.LinkedHashMap<String, DistroConfig> distros) { this.distros = distros; }

    public static class DistroConfig {
        private String sourcePath;
        private String family;
        private boolean enabled = true;
        private String baseUrl;
        private List<String> sourcePaths;
        private List<String> repos;
        private List<String> excludes;
        private Map<String, String> properties;

        public DistroConfig() {}

        public DistroConfig(String sourcePath, String family, boolean enabled) {
            this.sourcePath = sourcePath;
            this.family = family;
            this.enabled = enabled;
        }

        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

        public String getFamily() { return family; }
        public void setFamily(String family) { this.family = family; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public List<String> getSourcePaths() { return sourcePaths; }
        public void setSourcePaths(List<String> sourcePaths) { this.sourcePaths = sourcePaths; }

        public List<String> getRepos() { return repos; }
        public void setRepos(List<String> repos) { this.repos = repos; }

        public List<String> getExcludes() { return excludes; }
        public void setExcludes(List<String> excludes) { this.excludes = excludes; }

        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }
}
