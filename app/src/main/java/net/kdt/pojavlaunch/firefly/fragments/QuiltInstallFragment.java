package net.kdt.pojavlaunch.firefly.fragments;

import net.kdt.pojavlaunch.firefly.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.firefly.modloaders.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS);
    }

    @Override
    protected ModloaderListenerProxy getListenerProxy() {
        return sTaskProxy;
    }

    @Override
    protected void setListenerProxy(ModloaderListenerProxy listenerProxy) {
        sTaskProxy = listenerProxy;
    }
}
