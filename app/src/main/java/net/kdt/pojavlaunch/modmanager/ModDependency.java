package net.kdt.pojavlaunch.modmanager;

import java.io.Serializable;
import java.util.*;

/**
 * ModDependency represents dependencies between mods
 */
public class ModDependency implements Serializable {
    private String modId;
    private String requiredModId;
    private String minVersion;
    private String maxVersion;
    private DependencyType type;
    private boolean optional;

    public enum DependencyType {
        REQUIRED,      // Мод не будет работать без этого
        OPTIONAL,      // Мод работает, но лучше с этим
        INCOMPATIBLE   // Несовместим с этим модом
    }

    public ModDependency(String modId, String requiredModId, DependencyType type) {
        this.modId = modId;
        this.requiredModId = requiredModId;
        this.type = type;
        this.optional = false;
    }

    // Getters and Setters
    public String getModId() { return modId; }
    public void setModId(String modId) { this.modId = modId; }

    public String getRequiredModId() { return requiredModId; }
    public void setRequiredModId(String requiredModId) { this.requiredModId = requiredModId; }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }

    public String getMaxVersion() { return maxVersion; }
    public void setMaxVersion(String maxVersion) { this.maxVersion = maxVersion; }

    public DependencyType getType() { return type; }
    public void setType(DependencyType type) { this.type = type; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    @Override
    public String toString() {
        return "ModDependency{" +
                "modId='" + modId + '\'' +
                ", requiredModId='" + requiredModId + '\'' +
                ", type=" + type +
                ", optional=" + optional +
                '}';
    }
}
