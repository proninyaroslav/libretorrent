/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
 * Copyright (C) 2026 Liav Mordouch <liavmordouch@gmail.com>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.proninyaroslav.libretorrent.core.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import org.proninyaroslav.libretorrent.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocaleHelper {
    public static final String SYSTEM_DEFAULT = "system";

    @NonNull
    public static Context wrapContext(@NonNull Context context, @NonNull String languageCode) {
        if (SYSTEM_DEFAULT.equals(languageCode)) {
            return context;
        }

        Configuration config = new Configuration(context.getResources().getConfiguration());
        Locale locale = localeFromLanguageCode(languageCode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            Locale.setDefault(locale);
            config.setLocale(locale);
        }

        return context.createConfigurationContext(config);
    }

    public static void setLocale(@NonNull String languageCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (SYSTEM_DEFAULT.equals(languageCode)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
            } else {
                Locale locale = localeFromLanguageCode(languageCode);
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale));
            }
        }
    }

    @NonNull
    public static List<LocaleItem> getAvailableLocales(@NonNull Context context) {
        List<LocaleItem> locales = new ArrayList<>();
        
        locales.add(new LocaleItem(SYSTEM_DEFAULT, getSystemDefaultDisplayName(context)));

        Map<String, String> localeMap = new HashMap<>();
        
        String[] localeCodes = {
            "ab", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da", "de", "el",
            "en", "eo", "es", "et", "eu", "fa", "fi", "fr", "gu", "he", "hi", "hu", "in",
            "it", "ja", "ko", "lt", "ml", "ms", "nb", "ne", "nl", "nn", "pl", "pt",
            "pt-rBR", "ro", "ru", "sat", "si", "sr", "sv", "ta", "tr", "uk", "ur",
            "vi", "zh-rCN", "zh-rTW"
        };

        for (String code : localeCodes) {
            Locale locale = localeFromLanguageCode(code);
            String displayName = locale.getDisplayName(locale);

            if (!displayName.isEmpty()) {
                displayName = displayName.substring(0, 1).toUpperCase(locale) + 
                             displayName.substring(1);
            }
            localeMap.put(code, displayName);
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(localeMap.entrySet());
        Collections.sort(entries, (a, b) -> a.getValue().compareTo(b.getValue()));

        for (Map.Entry<String, String> entry : entries) {
            locales.add(new LocaleItem(entry.getKey(), entry.getValue()));
        }

        return locales;
    }

    @NonNull
    private static Locale localeFromLanguageCode(@NonNull String languageCode) {
        if (languageCode.contains("-r")) {
            String[] parts = languageCode.split("-r");
            return new Locale(parts[0], parts[1]);
        } else if (languageCode.contains("-")) {
            String[] parts = languageCode.split("-");
            return new Locale(parts[0], parts[1]);
        } else {
            return new Locale(languageCode);
        }
    }

    @NonNull
    private static String getSystemDefaultDisplayName(@NonNull Context context) {
        return context.getString(R.string.pref_locale_system_default);
    }

    public static class LocaleItem {
        public final String code;
        public final String displayName;

        public LocaleItem(@NonNull String code, @NonNull String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
    }
}
