/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.frostwire.jlibtorrent.FileStorage;

import org.acra.ACRA;
import org.acra.ReportField;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.receivers.BootReceiver;
import org.proninyaroslav.libretorrent.receivers.SchedulerReceiver;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * General utils.
 */

public class Utils
{
    public static final String INFINITY_SYMBOL = "\u221e";
    public static final String MAGNET_PREFIX = "magnet";
    public static final String HTTP_PREFIX = "http";
    public static final String HTTPS_PREFIX = "https";
    public static final String INFOHASH_PREFIX = "magnet:?xt=urn:btih:";
    public static final String FILE_PREFIX = "file";
    public static final String CONTENT_PREFIX = "content";
    public static final String TRACKER_URL_PATTERN =
            "^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

    /*
     * Colored status bar in KitKat.
     */

    public static void showColoredStatusBar_KitKat(Activity activity)
    {
        RelativeLayout statusBar = (RelativeLayout) activity.findViewById(R.id.statusBarKitKat);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            statusBar.setVisibility(View.VISIBLE);
        }
    }

    public static void showActionModeStatusBar(Activity activity, boolean mode)
    {
        int color = (mode ? R.color.action_mode_dark : R.color.primary_dark);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            RelativeLayout statusBar = (RelativeLayout) activity.findViewById(R.id.statusBarKitKat);
            statusBar.setBackground(ContextCompat.getDrawable(activity, color));
            statusBar.setVisibility(View.VISIBLE);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, color));
        }
    }

    /*
     * Colorize the progress bar in the accent color (for pre-Lollipop).
     */

    public static void colorizeProgressBar(Context context, ProgressBar progress)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            progress.getProgressDrawable()
                    .setColorFilter(
                            ContextCompat.getColor(context, R.color.accent),
                            android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    /*
     * Returns the list of BencodeFileItem objects, extracted from FileStorage.
     * The order of addition in the list corresponds to the order of indexes in jlibtorrent.FileStorage
     */

    public static ArrayList<BencodeFileItem> getFileList(FileStorage storage)
    {
        ArrayList<BencodeFileItem> files = new ArrayList<BencodeFileItem>();
        for (int i = 0; i < storage.numFiles(); i++) {
            BencodeFileItem file = new BencodeFileItem(storage.filePath(i), i, storage.fileSize(i));
            files.add(file);
        }

        return files;
    }

    public static void setBackground(View v, Drawable d)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            v.setBackgroundDrawable(d);
        } else {
            v.setBackground(d);
        }
    }

    public static byte[] toPrimitive(Collection<Byte> array) {
        if (array == null) {
            return null;
        } else if (array.isEmpty()) {
            return new byte[]{};
        }

        final byte[] result = new byte[array.size()];

        int i = 0;
        for (Byte b : array) {
            result[i++] = b;
        }

        return result;
    }

    /*
     * Returns the checking result or throws an exception.
     */

    public static boolean checkNetworkConnection(Context context)
    {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        return activeNetwork.isConnectedOrConnecting();
    }

    /*
     * Returns the link as "http[s]://[www.]name.domain/...".
     *
     * Returns null if the link is not valid.
     */

    public static String normalizeURL(String url)
    {
        if (!isValidUrl(url)) {
            return null;
        }

        if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX)) {
            return HTTP_PREFIX + "://" + url;
        } else {
            return url;
        }
    }

    public static boolean isTwoPane(Context context)
    {
        return context.getResources().getBoolean(R.bool.isTwoPane);
    }

    public static boolean isTablet(Context context)
    {
        return context.getResources().getBoolean(R.bool.isTablet);
    }

    /*
     * Returns true if link has the form "http[s][udp]://[www.]name.domain/...".
     *
     * Returns false if the link is not valid.
     */

    public static boolean isValidTrackerUrl(String url)
    {
        if (url == null || TextUtils.isEmpty(url)) {
            return false;
        }

        Pattern pattern = Pattern.compile(TRACKER_URL_PATTERN);
        Matcher matcher = pattern.matcher(url.trim());

        return matcher.matches();
    }

    /**
     * Check whether given URL is valid.
     * @param url URL to be checked.
     * @return true if the URL is valid, return false otherwise.
     */
    public static boolean isValidUrl (String url) {
        if (url == null || TextUtils.isEmpty(url)) {
            return false;
        }

        try {
            URL parsedUrl = new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /*
     * Return system text line separator (in android it '\n').
     */

    public static String getLineSeparator()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return System.lineSeparator();
        } else {
            return System.getProperty("line.separator");
        }
    }

    /*
     * Returns the first item from clipboard.
     */

    @Nullable
    public static String getClipboard(Context context)
    {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Activity.CLIPBOARD_SERVICE);

        if (!clipboard.hasPrimaryClip()) {
            return null;
        }

        ClipData clip = clipboard.getPrimaryClip();

        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }

        CharSequence text = clip.getItemAt(0).getText();
        if (text == null) {
            return null;
        }

        return text.toString();
    }

    public static void reportError(Throwable error, String comment)
    {
        if (error == null) {
            return;
        }

        if (comment != null) {
            ACRA.getErrorReporter().putCustomData(ReportField.USER_COMMENT.toString(), comment);
        }

        ACRA.getErrorReporter().handleSilentException(error);
    }

    public static int dpToPx(Context context, float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp, context.getResources().getDisplayMetrics());
    }

    public static int getDefaultBatteryLowLevel()
    {
        return Resources.getSystem().getInteger(
                Resources.getSystem().getIdentifier("config_lowBatteryWarningLevel", "integer", "android"));
    }

    public static float getBatteryLevel(Context context)
    {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        /* Error checking that probably isn't needed but I added just in case */
        if (level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float) level / (float) scale) * 100.0f;
    }

    public static boolean isBatteryCharging(Context context)
    {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean isBatteryLow(Context context)
    {
        return Utils.getBatteryLevel(context) <= Utils.getDefaultBatteryLowLevel();
    }

    public static boolean isBatteryBelowThreshold(Context context, int threshold)
    {
        return Utils.getBatteryLevel(context) <= threshold;
    }

    public static int getTheme(Context context)
    {
        int theme = SettingsManager.getPreferences(context)
                .getInt(context.getString(R.string.pref_key_theme),
                        SettingsManager.Default.theme(context));

        return theme;
    }
    
    public static boolean isDarkTheme(Context context)
    {
        return getTheme(context) == Integer.parseInt(context.getString(R.string.pref_theme_dark_value));
    }
    
    public static boolean isBlackTheme(Context context)
    {
        return getTheme(context) == Integer.parseInt(context.getString(R.string.pref_theme_black_value));
    }

    public static TorrentSorting getTorrentSorting(Context context)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);

        String column = pref.getString(context.getString(R.string.pref_key_sort_torrent_by),
                                       SettingsManager.Default.sortTorrentBy);
        String direction = pref.getString(context.getString(R.string.pref_key_sort_torrent_direction),
                                          SettingsManager.Default.sortTorrentDirection);

        return new TorrentSorting(TorrentSorting.SortingColumns.fromValue(column),
                TorrentSorting.Direction.fromValue(direction));
    }

    public static boolean checkStoragePermission(Context context)
    {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isWifiEnabled(Context context)
    {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        return manager != null && manager.isWifiEnabled();
    }

    /*
     * Migrate from Tray settings database to shared preferences.
     * TODO: delete after some releases
     */
    @Deprecated
    public static void migrateTray2SharedPreferences(Context context)
    {
        final String TAG = "tray2shared";
        final String migrate_key = "tray2shared_migrated";
        SharedPreferences pref = SettingsManager.getPreferences(context);

        if (pref.getBoolean(migrate_key, false))
            return;

        File dbFile = context.getDatabasePath("tray.db");
        if (dbFile == null || !dbFile.exists()) {
            Log.w(TAG, "Database not found");
            pref.edit().putBoolean(migrate_key, true).apply();

            return;
        }
        SQLiteDatabase db;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't open database: " + Log.getStackTraceString(e));
            context.deleteDatabase("tray");
            pref.edit().putBoolean(migrate_key, true).apply();

            return;
        }
        Cursor c = db.query("TrayPreferences",
                            new String[]{"KEY", "VALUE"},
                            null,
                            null,
                            null,
                            null,
                            null);
        SharedPreferences.Editor edit = pref.edit();
        Log.i(TAG, "Start migrate");
        try {
            int key_i = c.getColumnIndex("KEY");
            int value_i = c.getColumnIndex("VALUE");
            while (c.moveToNext()) {
                String key = c.getString(key_i);
                String value = c.getString(value_i);

                if (value.equalsIgnoreCase("true")) {
                    edit.putBoolean(key, true);
                } else if (value.equalsIgnoreCase("false")) {
                    edit.putBoolean(key, false);
                } else {
                    try {
                        int number = Integer.parseInt(value);
                        edit.putInt(key, number);
                    } catch (NumberFormatException e) {
                        edit.putString(key, value);
                    }
                }
            }
            Log.i(TAG, "Migrate completed");

        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            c.close();
            context.deleteDatabase("tray.db");
            edit.putBoolean(migrate_key, true);
            edit.apply();
        }
    }

    /*
     * Workaround for start service in Android 8+ after BOOT_COMPLETED.
     * We have a window of time to get around to calling startForeground() before we get ANR,
     * if work is longer than a millisecond but less than a few seconds.
     */

    public static void startServiceAfterBoot(Context context)
    {
        Intent i = new Intent(context, TorrentTaskService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(i);
        else
            context.startService(i);
    }

    /*
     * Time in minutes after 00:00
     */
    public static void addScheduledTime(Context context, String action, int time)
    {
        Calendar calendar = Calendar.getInstance();
        long timeInMillis = System.currentTimeMillis();
        calendar.setTimeInMillis(timeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, time / 60);
        calendar.set(Calendar.MINUTE, time % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() < timeInMillis + 2000L)
            calendar.add(Calendar.DAY_OF_MONTH, 1);

        addScheduledAlarm(context, action, calendar);
    }

    public static void addScheduledAlarm(Context context, @NonNull String action, Calendar calendar)
    {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, action.hashCode(), intent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            am.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
    }

    public static void cancelScheduledAlarm(Context context, @NonNull String action)
    {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, action.hashCode(), intent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }

    public static void enableBootReceiverIfNeeded(Context context)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        int flag = (
                !(pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_start),
                        SettingsManager.Default.enableSchedulingStart) ||
                pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown),
                        SettingsManager.Default.enableSchedulingShutdown) ||
                pref.getBoolean(context.getString(R.string.pref_key_autostart),
                        SettingsManager.Default.autostart)) ?
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        ComponentName bootReceiver = new ComponentName(context, BootReceiver.class);
        context.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);
    }
}
