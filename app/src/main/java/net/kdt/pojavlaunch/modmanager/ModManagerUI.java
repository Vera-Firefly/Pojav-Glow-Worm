package net.kdt.pojavlaunch.modmanager;

import android.view.ViewGroup;
import android.content.Context;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.*;

/**
 * ModManagerUI предоставляет пользовательский интерфейс для управления модами
 */
public class ModManagerUI {
    private final Context context;
    private final ModManager modManager;
    private final ModInstaller modInstaller;
    private final ModDependencyResolver dependencyResolver;
    
    private EditText searchInput;
    private ListView modListView;
    private TextView warningsText;
    private ProgressBar downloadProgress;
    private TextView statusText;
    private Button downloadButton;

    public ModManagerUI(Context context, ModManager modManager, 
                        ModInstaller modInstaller, ModDependencyResolver dependencyResolver) {
        this.context = context;
        this.modManager = modManager;
        this.modInstaller = modInstaller;
        this.dependencyResolver = dependencyResolver;
    }

    /**
     * Создает основной экран управления модами
     */
    public View createModManagerView() {
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);

        // === Заголовок ===
        TextView titleView = new TextView(context);
        titleView.setText("📦 Менеджер Модов");
        titleView.setTextSize(24);
        titleView.setPadding(0, 0, 0, 16);
        mainLayout.addView(titleView);

        // === Вкладка 1: Поиск и загрузка модов ===
        mainLayout.addView(createSearchTab());

        // === Вкладка 2: Установленные моды ===
        mainLayout.addView(createInstalledTab());

        // === Вкладка 3: Предупреждения ===
        mainLayout.addView(createWarningsSection());

        return mainLayout;
    }

    /**
     * Создает вкладку поиска модов
     */
    private View createSearchTab() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, 16);

        // Заголовок
        TextView header = new TextView(context);
        header.setText("🔍 Поиск Модов");
        header.setTextSize(18);
        header.setPadding(0, 0, 0, 8);
        layout.addView(header);

        // Поле поиска
        searchInput = new EditText(context);
        searchInput.setHint("Введите название мода...");
        searchInput.setPadding(8, 8, 8, 8);
        layout.addView(searchInput);

        // Кнопки для выбора источника
        LinearLayout sourceLayout = new LinearLayout(context);
        sourceLayout.setOrientation(LinearLayout.HORIZONTAL);
        sourceLayout.setPadding(0, 8, 0, 8);

        Button curseforgBtn = new Button(context);
        curseforgBtn.setText("CurseForge");
        curseforgBtn.setLayoutParams(new LinearLayout.LayoutParams(0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        curseforgBtn.setOnClickListener(v -> searchMods("curseforge"));
        sourceLayout.addView(curseforgBtn);

        Button modrinthBtn = new Button(context);
        modrinthBtn.setText("Modrinth");
        modrinthBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        modrinthBtn.setOnClickListener(v -> searchMods("modrinth"));
        sourceLayout.addView(modrinthBtn);

        Button localBtn = new Button(context);
        localBtn.setText("Локальный");
        localBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        localBtn.setOnClickListener(v -> searchMods("local"));
        sourceLayout.addView(localBtn);

        layout.addView(sourceLayout);

        // Список результатов
        modListView = new ListView(context);
        modListView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200
        ));
        layout.addView(modListView);

        // Прогресс загрузки
        downloadProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        downloadProgress.setVisibility(View.GONE);
        layout.addView(downloadProgress);

        // Статус
        statusText = new TextView(context);
        statusText.setText("✅ Готово");
        statusText.setPadding(0, 8, 0, 0);
        layout.addView(statusText);

        // Кнопка скачивания
        downloadButton = new Button(context);
        downloadButton.setText("⬇️ Скачать Выбранный Мод");
        downloadButton.setOnClickListener(v -> downloadSelectedMod());
        layout.addView(downloadButton);

        return layout;
    }

    /**
     * Создает вкладку установленных модов
     */
    private View createInstalledTab() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, 16);

        // Заголовок
        TextView header = new TextView(context);
        header.setText("✅ Установленные Моды");
        header.setTextSize(18);
        header.setPadding(0, 0, 0, 8);
        layout.addView(header);

        // Список установленных модов
        ListView installedList = new ListView(context);
        installedList.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 250
        ));

        List<ModMetadata> mods = modManager.getAllMods();
        ModListAdapter adapter = new ModListAdapter(context, mods);
        installedList.setAdapter(adapter);

        layout.addView(installedList);

        return layout;
    }

    /**
     * Создает секцию предупреждений
     */
    private View createWarningsSection() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(8, 8, 8, 8);
        layout.setBackgroundColor(0xFFFFEDED); // Светлый красный фон

        // Заголовок
        TextView header = new TextView(context);
        header.setText("⚠️ Предупреждения и Конфликты");
        header.setTextSize(16);
        header.setTextColor(0xFFD32F2F); // Красный текст
        header.setPadding(0, 0, 0, 8);
        layout.addView(header);

        // Текст предупреждений
        warningsText = new TextView(context);
        warningsText.setText("✅ Нет предупреждений");
        warningsText.setPadding(8, 8, 8, 8);
        layout.addView(warningsText);

        return layout;
    }

    /**
     * Поиск модов по источнику
     */
    private void searchMods(String source) {
        statusText.setText("🔍 Поиск в " + source + "...");
        
        String query = searchInput.getText().toString();
        if (query.isEmpty()) {
            statusText.setText("❌ Введите название мода!");
            return;
        }

        new Thread(() -> {
            try {
                List<ModSearchResult> results = new ArrayList<>();

                switch (source) {
                    case "curseforge":
                        results = searchCurseForge(query);
                        break;
                    case "modrinth":
                        results = searchModrinth(query);
                        break;
                    case "local":
                        results = searchLocal(query);
                        break;
                }

                showSearchResults(results);
            } catch (Exception e) {
                statusText.setText("❌ Ошибка поиска: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Поиск в CurseForge
     */
    private List<ModSearchResult> searchCurseForge(String query) {
        List<ModSearchResult> results = new ArrayList<>();
        // TODO: Интеграция с CurseForge API
        // Пока добавляем примеры
        results.add(new ModSearchResult(
            "jei",
            "Just Enough Items",
            "Самый популярный мод для поиска предметов",
            "13.0.0",
            "https://example.com/jei.jar"
        ));
        return results;
    }

    /**
     * Поиск в Modrinth
     */
    private List<ModSearchResult> searchModrinth(String query) {
        List<ModSearchResult> results = new ArrayList<>();
        // TODO: Интеграция с Modrinth API
        results.add(new ModSearchResult(
            "sodium",
            "Sodium",
            "Оптимизация рендеринга для Fabric",
            "0.4.10",
            "https://example.com/sodium.jar"
        ));
        return results;
    }

    /**
     * Поиск локальных файлов
     */
    private List<ModSearchResult> searchLocal(String query) {
        List<ModSearchResult> results = new ArrayList<>();
        File modsDir = new File(context.getFilesDir(), "mods");
        if (modsDir.exists()) {
            File[] files = modsDir.listFiles((dir, name) -> 
                name.endsWith(".jar") && name.toLowerCase().contains(query.toLowerCase())
            );
            if (files != null) {
                for (File file : files) {
                    results.add(new ModSearchResult(
                        file.getName().replace(".jar", ""),
                        file.getName(),
                        "Локальный файл",
                        "unknown",
                        file.getAbsolutePath()
                    ));
                }
            }
        }
        return results;
    }

    /**
     * Показывает результаты поиска
     */
    private void showSearchResults(List<ModSearchResult> results) {
        // TODO: Обновить ListView с результатами
        statusText.setText("✅ Найдено модов: " + results.size());
    }

    /**
     * Скачивает выбранный мод с проверкой зависимостей
     */
    private void downloadSelectedMod() {
        statusText.setText("⬇️ Начинаем скачивание...");
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);

        // Пример скачивания мода
        String modId = "jei";
        String downloadUrl = "https://example.com/jei.jar";

        // Проверяем зависимости
        List<ModDependency> dependencies = dependencyResolver.resolveDependencies(modId);
        
        if (!dependencies.isEmpty()) {
            // Есть зависимости - скачиваем их автоматически
            statusText.setText("📦 Нужно скачать зависимости...");
            dependencyResolver.autoInstallDependencies(modId, downloadUrl);
        }

        // Проверяем конфликты
        List<ModDependencyResolver.ConflictWarning> conflicts = 
            dependencyResolver.checkForConflicts(modId);
        
        if (!conflicts.isEmpty()) {
            showConflictWarnings(conflicts);
        }

        // Проверяем риск краша
        List<ModDependencyResolver.CrashRisk> crashRisks = 
            dependencyResolver.checkForCrashRisks(modId);
        
        if (!crashRisks.isEmpty()) {
            showCrashWarnings(crashRisks);
        }

        // Скачиваем сам мод
        modInstaller.downloadAndInstall(modId, downloadUrl);
    }

    /**
     * Показывает предупреждения о конфликтах
     */
    private void showConflictWarnings(List<ModDependencyResolver.ConflictWarning> conflicts) {
        StringBuilder warningsBuilder = new StringBuilder();
        warningsBuilder.append("⚠️ КОНФЛИКТЫ ОБНАРУЖЕНЫ:\n");
        for (ModDependencyResolver.ConflictWarning conflict : conflicts) {
            warningsBuilder.append("❌ ").append(conflict.reason).append("\n");
        }
        warningsText.setText(warningsBuilder.toString());
    }

    /**
     * Показывает предупреждения о крашах
     */
    private void showCrashWarnings(List<ModDependencyResolver.CrashRisk> risks) {
        StringBuilder warningsBuilder = new StringBuilder();
        warningsBuilder.append("🔥 РИСК КРАША:\n");
        for (ModDependencyResolver.CrashRisk risk : risks) {
            warningsBuilder.append("💥 ").append(risk.reason).append("\n");
        }
        warningsText.setText(warningsBuilder.toString());
    }

    /**
     * Адаптер для списка модов
     */
    private static class ModListAdapter extends ArrayAdapter<ModMetadata> {
        public ModListAdapter(Context context, List<ModMetadata> mods) {
            super(context, 0, mods);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new LinearLayout(getContext());
                ((LinearLayout) convertView).setOrientation(LinearLayout.VERTICAL);
                ((LinearLayout) convertView).setPadding(8, 8, 8, 8);
            }

            ModMetadata mod = getItem(position);
            LinearLayout layout = (LinearLayout) convertView;
            layout.removeAllViews();

            TextView nameView = new TextView(getContext());
            nameView.setText(mod.getName() + " (" + mod.getVersion() + ")");
            nameView.setTextSize(14);
            layout.addView(nameView);

            TextView descView = new TextView(getContext());
            descView.setText(mod.getDescription() != null ? mod.getDescription() : "Нет описания");
            descView.setTextSize(12);
            descView.setTextColor(0xFF666666);
            layout.addView(descView);

            return convertView;
        }
    }

    public static class ModSearchResult {
        public String modId;
        public String name;
        public String description;
        public String version;
        public String downloadUrl;

        public ModSearchResult(String modId, String name, String description, 
                             String version, String downloadUrl) {
            this.modId = modId;
            this.name = name;
            this.description = description;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
