package com.firefly.utils;

import static net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences.PREF_EXP_SETUP;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;

import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.R;

import com.movtery.plugins.renderer.RendererPlugin;

public class ListUtils {
    private static CDriverModelList sCompatibleCDriverModel;
    private static CMesaLibList sCompatibleCMesaLibs;
    private static CMesaLDOList sCompatibleCMesaLDO;
    private static CTurnipDriverList sCompatibleCTurnipDriver;
    private static RenderersList sCompatibleRenderers;
    private static ConfigBridgeList sCompatibleConfigBridge;
    private static LibGLGLList sCompatibleLibGLGL;

    public static interface ListAndArray {
        List<String> getList();

        String[] getArray();
    }

    public static class RenderersList implements ListAndArray {
        public final List<String> rendererIds;
        public final String[] rendererDisplayNames;

        public RenderersList(List<String> rendererIds, String[] rendererDisplayNames) {
            this.rendererIds = rendererIds;
            this.rendererDisplayNames = rendererDisplayNames;
        }

        @Override
        public List<String> getList() {
            return rendererIds;
        }

        @Override
        public String[] getArray() {
            return rendererDisplayNames;
        }
    }

    /**
     * Return the renderers that are compatible with this device
     */
    public static RenderersList getCompatibleRenderers(Context context) {
        Resources resources = context.getResources();
        String[] defaultRenderers = resources.getStringArray(R.array.renderer_values);
        String[] defaultRendererNames = resources.getStringArray(R.array.renderer);
        List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
        List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
        for (int i = 0; i < defaultRenderers.length; i++) {
            String rendererlist = defaultRenderers[i];
            if (rendererlist.contains("mesa_3d") && !PREF_EXP_SETUP) continue;
            if (rendererlist.contains("zink") && PREF_EXP_SETUP) continue;
            if (rendererlist.contains("virgl") && PREF_EXP_SETUP) continue;
            if (rendererlist.contains("freedreno") && PREF_EXP_SETUP) continue;
            if (rendererlist.contains("panfrost") && PREF_EXP_SETUP) continue;
            rendererIds.add(rendererlist);
            rendererNames.add(defaultRendererNames[i]);
        }
        // 渲染器插件
        if (RendererPlugin.isAvailable()) {
            RendererPlugin.getRendererList().forEach(renderer -> {
                rendererIds.add(renderer.getIdName());
                rendererNames.add(renderer.getDes());
            });
        }
        sCompatibleRenderers = new RenderersList(rendererIds,
                rendererNames.toArray(new String[0]));

        return sCompatibleRenderers;
    }

    public static class ConfigBridgeList implements ListAndArray {
        public final List<String> configIds;
        public final String[] configNames;

        public ConfigBridgeList(List<String> configIds, String[] configNames) {
            this.configIds = configIds;
            this.configNames = configNames;
        }

        @Override
        public List<String> getList() {
            return configIds;
        }

        @Override
        public String[] getArray() {
            return configNames;
        }
    }

    public static ConfigBridgeList getCompatibleConfigBridge(Context context) {
        Resources resources = context.getResources();
        String[] defaultIds = resources.getStringArray(R.array.bridge_config_values);
        String[] defaultNames = resources.getStringArray(R.array.bridge_config_names);
        List<String> Ids = new ArrayList<>(defaultIds.length);
        List<String> Names = new ArrayList<>(defaultNames.length);
        for (int i = 0; i < defaultIds.length; i++) {
            Ids.add(defaultIds[i]);
            Names.add(defaultNames[i]);
        }
        sCompatibleConfigBridge = new ConfigBridgeList(Ids, Names.toArray(new String[0]));
        return sCompatibleConfigBridge;
    }

    public static class CMesaLibList implements ListAndArray {
        public final List<String> CMesaLibIds;
        public final String[] CMesaLibs;

        public CMesaLibList(List<String> CMesaLibIds, String[] CMesaLibs) {
            this.CMesaLibIds = CMesaLibIds;
            this.CMesaLibs = CMesaLibs;
        }

        @Override
        public List<String> getList() {
            return CMesaLibIds;
        }

        @Override
        public String[] getArray() {
            return CMesaLibs;
        }
    }

    public static CMesaLibList getCompatibleCMesaLib(Context context) {
        Resources resources = context.getResources();
        String[] defaultCMesaLib = resources.getStringArray(R.array.osmesa_values);
        String[] defaultCMesaLibNames = resources.getStringArray(R.array.osmesa_library);
        List<String> CMesaLibIds = new ArrayList<>(defaultCMesaLib.length);
        List<String> CMesaLibNames = new ArrayList<>(defaultCMesaLibNames.length);
        for (int i = 0; i < defaultCMesaLib.length; i++) {
            CMesaLibIds.add(defaultCMesaLib[i]);
            CMesaLibNames.add(defaultCMesaLibNames[i]);
        }
        List<String> downloadList = MesaUtils.INSTANCE.getMesaLibList();
        for (String item : downloadList) {
            CMesaLibIds.add(item);
            CMesaLibNames.add("Mesa " + item);
        }
        sCompatibleCMesaLibs = new CMesaLibList(CMesaLibIds, CMesaLibNames.toArray(new String[0]));
        return sCompatibleCMesaLibs;
    }

    public static class CDriverModelList implements ListAndArray {
        public final List<String> CDriverModelIds;
        public final String[] CDriverModels;

        public CDriverModelList(List<String> CDriverModelIds, String[] CDriverModels) {
            this.CDriverModelIds = CDriverModelIds;
            this.CDriverModels = CDriverModels;
        }

        @Override
        public List<String> getList() {
            return CDriverModelIds;
        }

        @Override
        public String[] getArray() {
            return CDriverModels;
        }
    }

    public static CDriverModelList getCompatibleCDriverModel(Context context) {
        Resources resources = context.getResources();
        String[] defaultCDriverModel = resources.getStringArray(R.array.driver_model_values);
        String[] defaultCDriverModelNames = resources.getStringArray(R.array.driver_model);
        List<String> CDriverModelIds = new ArrayList<>(defaultCDriverModel.length);
        List<String> CDriverModelNames = new ArrayList<>(defaultCDriverModelNames.length);
        for (int i = 0; i < defaultCDriverModel.length; i++) {
            String driverModel = defaultCDriverModel[i];
            switch (Tools.MESA_LIBS) {
                case "default":
                case "mesa2320d":
                case "mesa2304": {
                    if (driverModel.contains("virgl")) continue;
                    if (driverModel.contains("softpipe")) continue;
                    if (driverModel.contains("llvmpipe")) continue;
                }
                break;
                case "mesa2300d": {
                    if (driverModel.contains("virgl")) continue;
                    if (driverModel.contains("freedreno")) continue;
                    if (driverModel.contains("softpipe")) continue;
                    if (driverModel.contains("llvmpipe")) continue;
                }
                break;
                case "mesa2205": {
                    if (driverModel.contains("panfrost")) continue;
                    if (driverModel.contains("freedreno")) continue;
                    if (driverModel.contains("softpipe")) continue;
                    if (driverModel.contains("llvmpipe")) continue;
                }
                break;
                case "mesa2121": {
                    if (driverModel.contains("panfrost")) continue;
                    if (driverModel.contains("freedreno")) continue;
                    if (driverModel.contains("softpipe")) continue;
                    if (driverModel.contains("llvmpipe")) continue;
                }
                break;
            }
            CDriverModelIds.add(driverModel);
            CDriverModelNames.add(defaultCDriverModelNames[i]);
        }
        sCompatibleCDriverModel = new CDriverModelList(CDriverModelIds,
                CDriverModelNames.toArray(new String[0]));

        return sCompatibleCDriverModel;
    }

    public static class CMesaLDOList implements ListAndArray {
        public final List<String> CMesaLDOIds;
        public final String[] CMesaLDO;

        public CMesaLDOList(List<String> CMesaLDOIds, String[] CMesaLDO) {
            this.CMesaLDOIds = CMesaLDOIds;
            this.CMesaLDO = CMesaLDO;
        }

        @Override
        public List<String> getList() {
            return CMesaLDOIds;
        }

        @Override
        public String[] getArray() {
            return CMesaLDO;
        }
    }

    public static CMesaLDOList getCompatibleCMesaLDO(Context context) {
        if (sCompatibleCMesaLDO != null) return sCompatibleCMesaLDO;
        Resources resources = context.getResources();
        String[] defaultCMesaLDO = resources.getStringArray(R.array.osmesa_mldo_values);
        String[] defaultCMesaLDONames = resources.getStringArray(R.array.osmesa_mldo);
        List<String> CMesaLDOIds = new ArrayList<>(defaultCMesaLDO.length);
        List<String> CMesaLDONames = new ArrayList<>(defaultCMesaLDONames.length);
        for (int i = 0; i < defaultCMesaLDO.length; i++) {
            CMesaLDOIds.add(defaultCMesaLDO[i]);
            CMesaLDONames.add(defaultCMesaLDONames[i]);
        }
        sCompatibleCMesaLDO = new CMesaLDOList(CMesaLDOIds,
                CMesaLDONames.toArray(new String[0]));
        return sCompatibleCMesaLDO;
    }

    public static class CTurnipDriverList implements ListAndArray {
        public final List<String> CTurnipDriverIds;
        public final String[] CTurnipDriver;

        public CTurnipDriverList(List<String> CTurnipDriverIds, String[] CTurnipDriver) {
            this.CTurnipDriverIds = CTurnipDriverIds;
            this.CTurnipDriver = CTurnipDriver;
        }

        @Override
        public List<String> getList() {
            return CTurnipDriverIds;
        }

        @Override
        public String[] getArray() {
            return CTurnipDriver;
        }
    }

    public static CTurnipDriverList getCompatibleCTurnipDriver(Context context) {
        Resources resources = context.getResources();
        String[] defaultCTurnipDriver = resources.getStringArray(R.array.turnip_values);
        String[] defaultCTurnipDriverNames = resources.getStringArray(R.array.turnip_files);
        List<String> CTurnipDriverIds = new ArrayList<>(defaultCTurnipDriver.length);
        List<String> CTurnipDriverNames = new ArrayList<>(defaultCTurnipDriverNames.length);
        for (int i = 0; i < defaultCTurnipDriver.length; i++) {
            CTurnipDriverIds.add(defaultCTurnipDriver[i]);
            CTurnipDriverNames.add(defaultCTurnipDriverNames[i]);
        }
        List<String> addTurnipList = TurnipUtils.INSTANCE.getTurnipDriverList();
        for (String item : addTurnipList) {
            CTurnipDriverIds.add(item);
            CTurnipDriverNames.add(item);
        }
        sCompatibleCTurnipDriver = new CTurnipDriverList(CTurnipDriverIds, CTurnipDriverNames.toArray(new String[0]));
        return sCompatibleCTurnipDriver;
    }

    public static class LibGLGLList implements ListAndArray {
        public final List<String> LIBGLGLIds;
        public final String[] LIBGLGL;

        public LibGLGLList(List<String> LIBGLGLIds, String[] LIBGLGL) {
            this.LIBGLGLIds = LIBGLGLIds;
            this.LIBGLGL = LIBGLGL;
        }

        @Override
        public List<String> getList() {
            return LIBGLGLIds;
        }

        @Override
        public String[] getArray() {
            return LIBGLGL;
        }
    }

    public static LibGLGLList getCompatibleLibGLGL(Context context) {
        if (sCompatibleLibGLGL != null) return sCompatibleLibGLGL;
        Resources resources = context.getResources();
        String[] defaultLIBGLGL = resources.getStringArray(R.array.libgl_gl_values);
        String[] defaultLIBGLGLNames = resources.getStringArray(R.array.libgl_gl);
        List<String> LIBGLGLIds = new ArrayList<>(defaultLIBGLGL.length);
        List<String> LIBGLGLNames = new ArrayList<>(defaultLIBGLGLNames.length);
        for (int i = 0; i < defaultLIBGLGL.length; i++) {
            LIBGLGLIds.add(defaultLIBGLGL[i]);
            LIBGLGLNames.add(defaultLIBGLGLNames[i]);
        }
        sCompatibleLibGLGL = new LibGLGLList(LIBGLGLIds,
                LIBGLGLNames.toArray(new String[0]));
        return sCompatibleLibGLGL;
    }

}