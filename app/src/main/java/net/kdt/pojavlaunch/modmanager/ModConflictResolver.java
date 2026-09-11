package net.kdt.pojavlaunch.modmanager;

import java.util.*;

/**
 * ModConflictResolver detects and helps resolve mod conflicts
 */
public class ModConflictResolver {
    
    public static class ConflictInfo {
        public String mod1;
        public String mod2;
        public ConflictType type;
        public String description;
        public List<String> suggestions;

        public enum ConflictType {
            VERSION_MISMATCH,
            INCOMPATIBLE_LOADERS,
            DUPLICATE_FUNCTIONALITY,
            KNOWN_CONFLICT,
            UNKNOWN
        }

        public ConflictInfo(String mod1, String mod2, ConflictType type) {
            this.mod1 = mod1;
            this.mod2 = mod2;
            this.type = type;
            this.suggestions = new ArrayList<>();
        }
    }

    /**
     * Detect conflicts between mods
     */
    public static List<ConflictInfo> detectConflicts(List<ModMetadata> mods) {
        List<ConflictInfo> conflicts = new ArrayList<>();

        // Check for version mismatches
        checkVersionCompatibility(mods, conflicts);

        // Check for loader compatibility
        checkLoaderCompatibility(mods, conflicts);

        // Check for known conflicts
        checkKnownConflicts(mods, conflicts);

        return conflicts;
    }

    private static void checkVersionCompatibility(List<ModMetadata> mods, 
                                                 List<ConflictInfo> conflicts) {
        Map<String, List<ModMetadata>> modsByName = new HashMap<>();

        for (ModMetadata mod : mods) {
            modsByName.computeIfAbsent(mod.getName(), k -> new ArrayList<>()).add(mod);
        }

        for (List<ModMetadata> modList : modsByName.values()) {
            if (modList.size() > 1) {
                for (int i = 0; i < modList.size() - 1; i++) {
                    for (int j = i + 1; j < modList.size(); j++) {
                        ConflictInfo conflict = new ConflictInfo(
                            modList.get(i).getId(),
                            modList.get(j).getId(),
                            ConflictInfo.ConflictType.DUPLICATE_FUNCTIONALITY
                        );
                        conflict.description = "Multiple versions of the same mod detected";
                        conflict.suggestions.add("Keep only one version of each mod");
                        conflicts.add(conflict);
                    }
                }
            }
        }
    }

    private static void checkLoaderCompatibility(List<ModMetadata> mods, 
                                                List<ConflictInfo> conflicts) {
        Set<String> loaders = new HashSet<>();
        for (ModMetadata mod : mods) {
            if (mod.getModLoader() != null && !mod.getModLoader().equals("unknown")) {
                loaders.add(mod.getModLoader());
            }
        }

        // If both Forge and Fabric are detected, that's likely a conflict
        if (loaders.contains("forge") && loaders.contains("fabric")) {
            ConflictInfo conflict = new ConflictInfo(
                "forge",
                "fabric",
                ConflictInfo.ConflictType.INCOMPATIBLE_LOADERS
            );
            conflict.description = "Forge and Fabric are incompatible with each other";
            conflict.suggestions.add("Choose either Forge or Fabric, not both");
            conflict.suggestions.add("Create separate game instances for each loader");
            conflicts.add(conflict);
        }
    }

    private static void checkKnownConflicts(List<ModMetadata> mods, 
                                           List<ConflictInfo> conflicts) {
        // This would contain a database of known mod conflicts
        // For now, it's a placeholder
        Map<String, String[]> knownConflicts = getKnownConflictDatabase();

        for (ModMetadata mod : mods) {
            String[] conflictingMods = knownConflicts.get(mod.getId());
            if (conflictingMods != null) {
                for (String conflictingId : conflictingMods) {
                    for (ModMetadata otherMod : mods) {
                        if (otherMod.getId().equals(conflictingId)) {
                            ConflictInfo conflict = new ConflictInfo(
                                mod.getId(),
                                conflictingId,
                                ConflictInfo.ConflictType.KNOWN_CONFLICT
                            );
                            conflict.description = "These mods are known to conflict with each other";
                            conflicts.add(conflict);
                        }
                    }
                }
            }
        }
    }

    private static Map<String, String[]> getKnownConflictDatabase() {
        // This would be loaded from a configuration file or remote database
        Map<String, String[]> database = new HashMap<>();
        // Example entries would go here
        return database;
    }

    /**
     * Suggest a resolution for a conflict
     */
    public static String resolveConflict(ConflictInfo conflict) {
        switch (conflict.type) {
            case DUPLICATE_FUNCTIONALITY:
                return "Remove duplicate mod and keep only the latest version";
            case INCOMPATIBLE_LOADERS:
                return "Choose one mod loader and remove mods for the other";
            case VERSION_MISMATCH:
                return "Update mod to compatible version";
            case KNOWN_CONFLICT:
                return conflict.suggestions.isEmpty() ? 
                    "Disable one of the conflicting mods" : 
                    conflict.suggestions.get(0);
            default:
                return "Unable to resolve automatically";
        }
    }
}
