package org.proninyaroslav.libretorrent.ui.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.preferences.PrefTheme;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.LocaleHelper;


public abstract class ThemeActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SettingsRepository pref;

    @Override
    protected void attachBaseContext(Context base) {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(base);
        String locale = pref.locale();
        Context context = LocaleHelper.wrapContext(base, locale);
        super.attachBaseContext(context);
    }

    private boolean isNightModeActive() {
        int currentMode = Configuration.UI_MODE_NIGHT_MASK & getResources().getConfiguration().uiMode;
        return currentMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        enableEdgeToEdge();
        setupTheme();
        super.onCreate(savedInstanceState);
        pref.registerOnSettingsChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pref.unregisterOnSettingsChangeListener(this);
    }

    private void enableEdgeToEdge() {
        SystemBarStyle systemBarStyle = isNightModeActive() ? SystemBarStyle.dark(Color.TRANSPARENT) : SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(this, systemBarStyle, systemBarStyle);
    }

    private void setupTheme() {
        int themeResId = R.style.AppTheme;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (pref.dynamicColors()) {
                if (pref.blackBackgrounds()) {
                    themeResId = R.style.AppTheme_Black;
                }
            } else {
                if (pref.blackBackgrounds()) {
                    themeResId = R.style.AppTheme_NoDynamicColors_Black;
                } else {
                    themeResId = R.style.AppTheme_NoDynamicColors;
                }
            }
        } else if (pref.blackBackgrounds()) {
            themeResId = R.style.AppTheme_Black;
        }
        setTheme(themeResId);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) {
            return;
        }
        if (key.equals(getString(R.string.pref_key_theme))) {
            onThemeChange();
        } else if (key.equals(getString(R.string.pref_key_locale))) {
            onLocaleChange();
        } else if (key.equals(getString(R.string.pref_key_theme_dynamic_colors))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recreate();
            }
        } else if (key.equals(getString(R.string.pref_key_theme_black_backgrounds))) {
            if (isNightModeActive()) {
                recreate();
            }
        }
    }

    private void onThemeChange() {
        PrefTheme theme = pref.theme();
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (theme instanceof PrefTheme.Light) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (theme instanceof PrefTheme.Dark) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void onLocaleChange() {
        LocaleHelper.setLocale(pref.locale());
        recreate();
    }
}