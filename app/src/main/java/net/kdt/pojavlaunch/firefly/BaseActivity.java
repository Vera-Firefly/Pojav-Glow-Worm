package net.kdt.pojavlaunch.firefly;

import android.content.*;
import android.os.*;

import androidx.appcompat.app.*;

import net.kdt.pojavlaunch.firefly.utils.*;

import static net.kdt.pojavlaunch.firefly.prefs.LauncherPreferences.PREF_IGNORE_NOTCH;

import com.firefly.feature.MarkdownRenderer;
import com.movtery.context.ContextExecutor;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleUtils.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleUtils.setLocale(this);
        MarkdownRenderer.INSTANCE.init(this);
        Tools.setFullscreen(this, setFullscreen());
        Tools.updateWindowSize(this);
    }

    /**
     * @return Whether the activity should be set as a fullscreen one
     */
    public boolean setFullscreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        Tools.checkStorageInteractive(this);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Tools.setFullscreen(this, setFullscreen());
        Tools.ignoreNotch(shouldIgnoreNotch(), this);
    }

    /**
     * @return Whether or not the notch should be ignored
     */
    protected boolean shouldIgnoreNotch() {
        return PREF_IGNORE_NOTCH;
    }
}
