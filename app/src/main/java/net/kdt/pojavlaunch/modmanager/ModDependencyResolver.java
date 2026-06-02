package net.kdt.pojavlaunch.modmanager;

import java.util.*;

/**
 * ModDependencyResolver resolves and handles mod dependencies and conflicts
 */
public class ModDependencyResolver {
    private final ModManager modManager;
    private final ModInstaller modInstaller;
    private final Map<String, List<ModDependency>> dependencyMap;
    private final List<DependencyListener> listeners;

    public interface DependencyListener {
        void onDependencyFound(String modId, String dependencyId, ModDependency.DependencyType type);
        void onAutoInstalling(String modId);
        void onConflictDetected(String mod1, String mod2, String reason);
        void onCrashRiskDetected(String modId, String crashReason);
        void onDependencyResolved(String modId);
    }

    public ModDependencyResolver(ModManager modManager, ModInstaller modInstaller) {
        this.modManager = modManager;
        this.modInstaller = modInstaller;
        this.dependencyMap = new HashMap<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());
        initializeDependencies();
    }

    /**
     * Инициализирует известные зависимости между модами
     */
    private void initializeDependencies() {
        // Добавляем известные зависимости
        // Пример: API моды, которые нужны другим модам
        addDependency("optifine", "minecraft-api", ModDependency.DependencyType.REQUIRED);
        addDependency("sodium", "fabric-api", ModDependency.DependencyType.REQUIRED);
        addDependency("lithium", "fabric-api", ModDependency.DependencyType.REQUIRED);
        addDependency("jei", "minecraft-api", ModDependency.DependencyType.OPTIONAL);
    }

    /**
     * Добавляет зависимость
     */
    public void addDependency(String modId, String dependencyId, ModDependency.DependencyType type) {
        ModDependency dep = new ModDependency(modId, dependencyId, type);
        dependencyMap.computeIfAbsent(modId, k -> new ArrayList<>()).add(dep);
    }

    /**
     * Разрешает все зависимости для мода
     */
    public List<ModDependency> resolveDependencies(String modId) {
        return dependencyMap.getOrDefault(modId, new ArrayList<>());
    }

    /**
     * Автоматически скачивает все нужные зависимости
     */
    public void autoInstallDependencies(String modId, String downloadUrl) {
        new Thread(() -> {
            try {
                List<ModDependency> dependencies = resolveDependencies(modId);

                for (ModDependency dep : dependencies) {
                    if (dep.getType() == ModDependency.DependencyType.REQUIRED) {
                        // Проверяем, установлен ли нужный мод
                        if (modManager.getMod(dep.getRequiredModId()) == null) {
                            notifyAutoInstalling(dep.getRequiredModId());
                            // Здесь должна быть загрузка с репозитория
                            // Для примера просто уведомляем
                            notifyDependencyFound(modId, dep.getRequiredModId(), 
                                ModDependency.DependencyType.REQUIRED);
                        }
                    }
                }

                notifyDependencyResolved(modId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Проверяет конфликты модов
     */
    public List<ConflictWarning> checkForConflicts(String modId) {
        List<ConflictWarning> warnings = new ArrayList<>();
        List<ModMetadata> allMods = modManager.getAllMods();

        for (ModMetadata installedMod : allMods) {
            // Проверяем несовместимость
            List<ModDependency> deps = resolveDependencies(modId);
            for (ModDependency dep : deps) {
                if (dep.getType() == ModDependency.DependencyType.INCOMPATIBLE &&
                    installedMod.getId().equals(dep.getRequiredModId())) {
                    ConflictWarning warning = new ConflictWarning(
                        modId,
                        installedMod.getId(),
                        "Этот мод несовместим с " + installedMod.getName()
                    );
                    warnings.add(warning);
                    notifyConflictDetected(modId, installedMod.getId(), warning.reason);
                }
            }
        }

        return warnings;
    }

    /**
     * Проверяет риск краша
     */
    public List<CrashRisk> checkForCrashRisks(String modId) {
        List<CrashRisk> risks = new ArrayList<>();

        // Известные моды, которые могут крашить
        Map<String, String> knownCrashes = new HashMap<>();
        knownCrashes.put("old-fabric-api", "Старая версия Fabric API может крашить");
        knownCrashes.put("incompatible-optifine", "Несовместимая версия OptiFine крашит Fabric");
        knownCrashes.put("broken-mod", "Этот мод имеет критические баги");

        for (Map.Entry<String, String> crash : knownCrashes.entrySet()) {
            if (modId.contains(crash.getKey())) {
                CrashRisk risk = new CrashRisk(modId, crash.getValue());
                risks.add(risk);
                notifyCrashRiskDetected(modId, crash.getValue());
            }
        }

        return risks;
    }

    public void addListener(DependencyListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DependencyListener listener) {
        listeners.remove(listener);
    }

    private void notifyDependencyFound(String modId, String dependencyId, 
                                       ModDependency.DependencyType type) {
        for (DependencyListener listener : listeners) {
            listener.onDependencyFound(modId, dependencyId, type);
        }
    }

    private void notifyAutoInstalling(String modId) {
        for (DependencyListener listener : listeners) {
            listener.onAutoInstalling(modId);
        }
    }

    private void notifyConflictDetected(String mod1, String mod2, String reason) {
        for (DependencyListener listener : listeners) {
            listener.onConflictDetected(mod1, mod2, reason);
        }
    }

    private void notifyCrashRiskDetected(String modId, String reason) {
        for (DependencyListener listener : listeners) {
            listener.onCrashRiskDetected(modId, reason);
        }
    }

    private void notifyDependencyResolved(String modId) {
        for (DependencyListener listener : listeners) {
            listener.onDependencyResolved(modId);
        }
    }

    /**
     * Предупреждение о конфликте
     */
    public static class ConflictWarning {
        public String mod1;
        public String mod2;
        public String reason;

        public ConflictWarning(String mod1, String mod2, String reason) {
            this.mod1 = mod1;
            this.mod2 = mod2;
            this.reason = reason;
        }
    }

    /**
     * Риск краша
     */
    public static class CrashRisk {
        public String modId;
        public String reason;

        public CrashRisk(String modId, String reason) {
            this.modId = modId;
            this.reason = reason;
        }
    }
}
