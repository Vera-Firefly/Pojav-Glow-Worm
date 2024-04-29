package com.qz.terminal2;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.qz.utils.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import net.kdt.pojavlaunch.R;

import java.util.List;

public class ConsoleActivity extends AppCompatActivity implements ServiceConnection{

    private Process process = null;
    
    private ProgressDialog mDialog;
    
    public TerminalView mEmulatorView;
    public ExtraKeysView mExtraKeysView;
    private TerminalSession mSession;
    public TermuxService mTermService;
    
    private int mFontSize;
    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE = 256;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);
        ignoreBatteryOptimization();
        computeFontSize();
        initView();
        startService();
    }

    private void computeFontSize(){
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, this.getResources().getDisplayMetrics());
        MIN_FONTSIZE = (int) (4f * dipInPixels);
        int defaultFontSize = Math.round(12 * dipInPixels);
        if (defaultFontSize % 2 == 1) defaultFontSize--;
        mFontSize = defaultFontSize;
        mFontSize = Math.max(MIN_FONTSIZE, Math.min(mFontSize, MAX_FONTSIZE));
    }
    
    private  void initView(){
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mEmulatorView = findViewById(R.id.emulatorView) ;
        mExtraKeysView = findViewById(R.id.extraKeysView);
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        mEmulatorView.setTextSize(mFontSize);
        mEmulatorView.requestFocus();
        mEmulatorView.setOnKeyListener(new TermuxViewClient(this));
    }
    
    private void  startService(){
        showDialog();
        Intent serviceIntent = new Intent(this, TermuxService.class);
        // Start the service and make it run regardless of who is bound to it:
        serviceIntent.setAction(TermuxService.ACTION_EXECUTE);
        startService(serviceIntent);
        if (!bindService(serviceIntent, this, 0))
            throw new RuntimeException("bindService() failed");
    }
    
    private void showDialog() {
        mDialog = new ProgressDialog(ConsoleActivity.this);
        mDialog.setTitle(R.string.main_termianl_init);
        mDialog.setMessage(getString(R.string.main_terminal_loading));
        mDialog.setCancelable(false);
        mDialog.create();
        mDialog.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mEmulatorView.onScreenUpdated();
    }

    private View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            return backkeyInterceptor(keyCode, event) || keyboardShortcuts(keyCode, event);
        }

        private boolean keyboardShortcuts(int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            boolean isCtrlPressed = (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0;
            boolean isShiftPressed = (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0;

            if (keyCode == KeyEvent.KEYCODE_TAB && isCtrlPressed) {
                if (isShiftPressed) {
                    //mViewFlipper.showPrevious();
                } else {
                    //mViewFlipper.showNext();
                }

                return true;
            } else if (keyCode == KeyEvent.KEYCODE_V && isCtrlPressed && isShiftPressed) {
                doPaste();
                return true;
            } else {
                return false;
            }
        }

        private boolean backkeyInterceptor(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK ) {
                onKeyUp(keyCode, event);
                return false;
            } else {
                return false;
            }
        }
    };
    
    void doPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null) return;
        CharSequence paste = clipData.getItemAt(0).coerceToText(this);
        if (!TextUtils.isEmpty(paste))
            getCurrentTermSession().getEmulator().paste(paste.toString());
    }

    private TerminalSession getCurrentTermSession() {
        return mTermService.getTermSession();
    }

    private void execURL(String link) {
        Uri webLink = Uri.parse(link);
        Intent openLink = new Intent(Intent.ACTION_VIEW, webLink);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(openLink, 0);
        if(handlers.size() > 0)
            startActivity(openLink);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
        mTermService.stopSelf();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem keyboard = menu.add("KeyBoard");
        keyboard.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        keyboard.setIcon(R.drawable.ic_keyboard);
        keyboard.setOnMenuItemClickListener(m -> {
            KeyboardUtils.setSoftKeyboardVisibility(() -> {
                KeyboardUtils.showSoftKeyboard(this, mEmulatorView);
            }, this, mEmulatorView, !KeyboardUtils.isSoftKeyboardVisible(this));
                
            return false;
        });
        return super.onCreateOptionsMenu(menu);
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mTermService = ((TermuxService.LocalBinder) service).service;
        mEmulatorView.attachSession(mTermService.getTermSession());
        mDialog.dismiss();
        mTermService.mSessionChangeCallback = new TerminalSession.SessionChangedCallback() {
            @Override
            public void onTextChanged(TerminalSession changedSession) {
                mEmulatorView.onScreenUpdated();
            }

            @Override
            public void onTitleChanged(TerminalSession updatedSession) {

            }

            @Override
            public void onSessionFinished(final TerminalSession finishedSession) {
                
            }

            @Override
            public void onClipboardText(TerminalSession session, String text) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(text)));
            }

            @Override
            public void onBell(TerminalSession session) {

            }

            @Override
            public void onColorsChanged(TerminalSession changedSession) {

            }
        };
    };

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    public void changeFontSize(boolean increase) {
        mFontSize += (increase ? 1 : -1) * 2;
        mFontSize = Math.max(MIN_FONTSIZE, Math.min(mFontSize, MAX_FONTSIZE));
        mEmulatorView.setTextSize(mFontSize);
    }

    private void ignoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
 
            boolean hasIgnored = powerManager.isIgnoringBatteryOptimizations(getPackageName());
            if (!hasIgnored) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            } else {
                Log.d("ignoreBattery", "hasIgnored");
            }
        }
    }
}
