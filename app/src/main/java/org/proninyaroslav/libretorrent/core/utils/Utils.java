/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.motion.MotionUtils;

import org.acra.ACRA;
import org.acra.ReportField;
import org.apache.commons.io.IOUtils;
import org.libtorrent4j.FileStorage;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.HttpConnection;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.exception.FetchLinkException;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilter;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilterCollection;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.sorting.BaseSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SafFileSystem;
import org.proninyaroslav.libretorrent.core.system.SystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.receiver.BootReceiver;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerDateAddedFilter;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerSort;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerSortDirection;
import org.proninyaroslav.libretorrent.ui.home.drawer.model.DrawerStatusFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/*
 * General utils.
 */

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static final String INFINITY_SYMBOL = "\u221e";
    public static final String MAGNET_PREFIX = "magnet";
    public static final String HTTP_PREFIX = "http";
    public static final String HTTPS_PREFIX = "https";
    public static final String INFOHASH_PREFIX = "magnet:?xt=urn:btih:";
    public static final String FILE_PREFIX = "file";
    public static final String CONTENT_PREFIX = "content";
    public static final String TRACKER_URL_PATTERN =
            "^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    public static final String HASH_PATTERN = "\\b[0-9a-fA-F]{5,40}\\b";
    public static final String MIME_TORRENT = "application/x-bittorrent";
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String NEWLINE_PATTERN = "\\r\\n|\\r|\\n";
    private static final String FEED_MIME_TYPE_PATTERN = "^(application|text)/((atom|rss)\\+)?xml";
    private static final String FEED_FILE_PATH_PATTERN = ".*\\.(xml|rss|atom)";

    /*
     * Colorize the progress bar in the accent color (for pre-Lollipop).
     */

    public static void colorizeProgressBar(@NonNull Context context,
                                           @NonNull ProgressBar progress) {
        progress.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(
                getAttributeColor(context, R.attr.colorSecondary),
                PorterDuff.Mode.SRC_IN)
        );
    }

    /*
     * Returns the list of BencodeFileItem objects, extracted from FileStorage.
     * The order of addition in the list corresponds to the order of indexes in libtorrent4j.FileStorage
     */

    public static ArrayList<BencodeFileItem> getFileList(@NonNull FileStorage storage) {
        ArrayList<BencodeFileItem> files = new ArrayList<>();
        for (int i = 0; i < storage.numFiles(); i++) {
            BencodeFileItem file = new BencodeFileItem(storage.filePath(i), i, storage.fileSize(i));
            files.add(file);
        }

        return files;
    }

    public static void setBackground(@NonNull View v,
                                     @NonNull Drawable d) {
        v.setBackground(d);
    }

    public static boolean checkConnectivity(@NonNull Context context) {
        SystemFacade systemFacade = SystemFacadeHelper.getSystemFacade(context);
        NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();

        return netInfo != null && netInfo.isConnected() && isNetworkTypeAllowed(context);
    }

    public static boolean isNetworkTypeAllowed(@NonNull Context context) {
        SystemFacade systemFacade = SystemFacadeHelper.getSystemFacade(context);

        SettingsRepository pref = RepositoryHelper.getSettingsRepository(context);
        boolean enableRoaming = pref.enableRoaming();
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();

        boolean noUnmeteredOnly;
        boolean noRoaming;

        NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
        /*
         * Use ConnectivityManager#isActiveNetworkMetered() instead of NetworkCapabilities#NET_CAPABILITY_NOT_METERED,
         * since Android detection VPN as metered, including on Android 9, oddly enough.
         * I think this is due to what VPN services doesn't use setUnderlyingNetworks() method.
         *
         * See for details: https://developer.android.com/about/versions/pie/android-9.0-changes-all#network-capabilities-vpn
         */
        boolean unmetered = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                !systemFacade.isActiveNetworkMetered();
        noUnmeteredOnly = !unmeteredOnly || unmetered;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            noRoaming = !enableRoaming || caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        } else {
            NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
            noRoaming = netInfo != null && !(enableRoaming && netInfo.isRoaming());
        }

        return noUnmeteredOnly && noRoaming;
    }

    public static boolean isMetered(@NonNull Context context) {
        SystemFacade systemFacade = SystemFacadeHelper.getSystemFacade(context);

        NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
        return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                systemFacade.isActiveNetworkMetered();
    }

    public static boolean isRoaming(@NonNull Context context) {
        SystemFacade systemFacade = SystemFacadeHelper.getSystemFacade(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NetworkCapabilities caps = systemFacade.getNetworkCapabilities();
            return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        } else {
            NetworkInfo netInfo = systemFacade.getActiveNetworkInfo();
            return netInfo != null && netInfo.isRoaming();
        }
    }

    /*
     * Returns the link as "(http[s]|ftp)://[www.]name.domain/...".
     */

    public static String normalizeURL(@NonNull String url) {
        url = IDN.toUnicode(url);

        if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX))
            return HTTP_PREFIX + url;
        else
            return url;
    }

    /*
     * Returns the link as "magnet:?xt=urn:btih:hash".
     */

    public static String normalizeMagnetHash(@NonNull String hash) {
        return INFOHASH_PREFIX + hash;
    }

    /*
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isTwoPane(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.isTwoPane);
    }

    /*
     * Tablets (from 7"), notebooks, TVs
     *
     * Don't use app context (its doesn't reload after configuration changes)
     */

    public static boolean isLargeScreenDevice(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.isLargeScreenDevice);
    }

    /*
     * Returns true if link has the form "http[s][udp]://[www.]name.domain/...".
     *
     * Returns false if the link is not valid.
     */

    public static boolean isValidTrackerUrl(@NonNull String url) {
        if (TextUtils.isEmpty(url))
            return false;

        Pattern pattern = Pattern.compile(TRACKER_URL_PATTERN);
        Matcher matcher = pattern.matcher(url.trim());

        return matcher.matches();
    }

    public static boolean isHash(@NonNull String hash) {
        if (TextUtils.isEmpty(hash))
            return false;

        Pattern pattern = Pattern.compile(HASH_PATTERN);
        Matcher matcher = pattern.matcher(hash.trim());

        return matcher.matches();
    }

    /*
     * Return system text line separator (in android it '\n').
     */

    public static String getLineSeparator() {
        return System.lineSeparator();
    }

    @Nullable
    public static ClipData getClipData(@NonNull Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (!clipboard.hasPrimaryClip())
            return null;

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0)
            return null;

        return clip;
    }

    public static List<CharSequence> getClipboardText(@NonNull Context context) {
        ArrayList<CharSequence> clipboardText = new ArrayList<>();

        ClipData clip = Utils.getClipData(context);
        if (clip == null)
            return clipboardText;

        for (int i = 0; i < clip.getItemCount(); i++) {
            CharSequence item = clip.getItemAt(i).getText();
            if (item == null)
                continue;
            clipboardText.add(item);
        }

        return clipboardText;
    }

    public static void reportError(@Nullable Throwable error,
                                   @Nullable String comment
    ) {
        if (comment != null) {
            ACRA.getErrorReporter().putCustomData(ReportField.USER_COMMENT.toString(), comment);
        }

        ACRA.getErrorReporter().handleSilentException(error);
    }

    public static int dpToPx(@NonNull Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics());
    }

    public static int getDefaultBatteryLowLevel() {
        return Resources.getSystem().getInteger(
                Resources.getSystem().getIdentifier("config_lowBatteryWarningLevel", "integer", "android"));
    }

    public static float getBatteryLevel(@NonNull Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null)
            return 50.0f;
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        /* Error checking that probably isn't needed but I added just in case */
        if (level == -1 || scale == -1)
            return 50.0f;

        return ((float) level / (float) scale) * 100.0f;
    }

    public static boolean isBatteryCharging(@NonNull Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null)
            return false;
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean isBatteryLow(@NonNull Context context) {
        return Utils.getBatteryLevel(context) <= Utils.getDefaultBatteryLowLevel();
    }

    public static boolean isBatteryBelowThreshold(@NonNull Context context, int threshold) {
        return Utils.getBatteryLevel(context) <= threshold;
    }

    public static int getThemePreference(@NonNull Context context) {
        return RepositoryHelper.getSettingsRepository(context).theme();
    }

    public static int getAppTheme(@NonNull Context context) {
        return R.style.AppTheme;
//        int theme = getThemePreference(context);
//
//        if (theme == Integer.parseInt(context.getString(R.string.pref_theme_light_value)))
//            return R.style.AppThemeM2;
//        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_dark_value)))
//            return R.style.AppThemeM2_Dark;
//        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_black_value)))
//            return R.style.AppThemeM2_Black;
//
//        return R.style.AppThemeM2;
    }

    public static int getTranslucentAppTheme(@NonNull Context appContext) {
        return R.style.AppTheme_Translucent;
//        int theme = getThemePreference(appContext);
//
//        if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_light_value)))
//            return R.style.AppThemeM2_Translucent;
//        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_dark_value)))
//            return R.style.AppThemeM2_Translucent_Dark;
//        else if (theme == Integer.parseInt(appContext.getString(R.string.pref_theme_black_value)))
//            return R.style.AppThemeM2_Translucent_Black;
//
//        return R.style.AppThemeM2_Translucent;
    }

    public static int getSettingsTheme(@NonNull Context context) {
        return R.style.AppTheme;
//        int theme = getThemePreference(context);
//
//        if (theme == Integer.parseInt(context.getString(R.string.pref_theme_light_value)))
//            return R.style.AppThemeM2_Settings;
//        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_dark_value)))
//            return R.style.AppThemeM2_Settings_Dark;
//        else if (theme == Integer.parseInt(context.getString(R.string.pref_theme_black_value)))
//            return R.style.AppThemeM2_Settings_Black;
//
//        return R.style.AppThemeM2_Settings;
    }

    public static boolean shouldRequestStoragePermission(@NonNull Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        );
    }

    /*
     * Workaround for start service in Android 8+ if app no started.
     * We have a window of time to get around to calling startForeground() before we get ANR,
     * if work is longer than a millisecond but less than a few seconds.
     */

    public static void startServiceBackground(@NonNull Context context, @NonNull Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Postpone the service start until the main thread is free to avoid ANR
            new Handler(Looper.getMainLooper())
                    .post(() -> context.startForegroundService(i));
        } else {
            context.startService(i);
        }
    }

    public static void enableBootReceiver(@NonNull Context context, boolean enable) {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(context);
        boolean schedulingStart = pref.enableSchedulingStart();
        boolean schedulingStop = pref.enableSchedulingShutdown();
        boolean autostart = pref.autostart();
        int flag = (!(enable || schedulingStart || schedulingStop || autostart) ?
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        ComponentName bootReceiver = new ComponentName(context, BootReceiver.class);
        context.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);
    }

    public static void enableBootReceiverIfNeeded(@NonNull Context context) {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(context);
        boolean schedulingStart = pref.enableSchedulingStart();
        boolean schedulingStop = pref.enableSchedulingShutdown();
        boolean autostart = pref.autostart();
        int flag = (!(schedulingStart || schedulingStop || autostart) ?
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        ComponentName bootReceiver = new ComponentName(context, BootReceiver.class);
        context.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);
    }

    public static byte[] fetchHttpUrl(@NonNull Context context,
                                      @NonNull String url) throws FetchLinkException {
        byte[][] response = new byte[1][];

        if (!Utils.checkConnectivity(context))
            throw new FetchLinkException("No network connection");

        final ArrayList<Throwable> errorArray = new ArrayList<>(1);
        HttpConnection connection;
        try {
            connection = new HttpConnection(url);
        } catch (Exception e) {
            throw new FetchLinkException(e);
        }

        connection.setListener(new HttpConnection.Listener() {
            @Override
            public void onConnectionCreated(HttpURLConnection conn) {
                /* Nothing */
            }

            @Override
            public void onResponseHandle(HttpURLConnection conn, int code, String message) {
                if (code == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = conn.getInputStream()) {
                        response[0] = IOUtils.toByteArray(is);

                    } catch (IOException e) {
                        errorArray.add(e);
                    }
                } else {
                    errorArray.add(new FetchLinkException("Failed to fetch link, response code: " + code));
                }
            }

            @Override
            public void onMovedPermanently(String newUrl) {
                /* Nothing */
            }

            @Override
            public void onIOException(IOException e) {
                errorArray.add(e);
            }

            @Override
            public void onTooManyRedirects() {
                errorArray.add(new FetchLinkException("Too many redirects"));
            }
        });
        connection.run();

        if (!errorArray.isEmpty()) {
            StringBuilder s = new StringBuilder();
            for (Throwable e : errorArray) {
                String msg = e.getMessage();
                if (msg != null)
                    s.append(msg.concat("\n"));
            }

            throw new FetchLinkException(s.toString());
        }

        return response[0];
    }

    /*
     * Without additional information (e.g -DEBUG)
     */

    public static String getAppVersionNumber(@NonNull String versionName) {
        int index = versionName.indexOf("-");
        if (index >= 0)
            versionName = versionName.substring(0, index);

        return versionName;
    }

    /*
     * Return version components in these format: [major, minor, revision]
     */

    public static int[] getVersionComponents(@NonNull String versionName) {
        int[] version = new int[3];

        /* Discard additional information */
        versionName = getAppVersionNumber(versionName);

        String[] components = versionName.split("\\.");
        if (components.length < 2)
            return version;

        try {
            version[0] = Integer.parseInt(components[0]);
            version[1] = Integer.parseInt(components[1]);
            if (components.length >= 3)
                version[2] = Integer.parseInt(components[2]);

        } catch (NumberFormatException e) {
            /* Ignore */
        }

        return version;
    }

    public static String makeSha1Hash(@NonNull String s) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        messageDigest.update(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sha1 = new StringBuilder();
        for (byte b : messageDigest.digest()) {
            if ((0xff & b) < 0x10)
                sha1.append("0");
            sha1.append(Integer.toHexString(0xff & b));
        }

        return sha1.toString();
    }

    public static SSLContext getSSLContext() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        TrustManager[] trustManagers = tmf.getTrustManagers();
        final X509TrustManager origTrustManager = (X509TrustManager) trustManagers[0];

        TrustManager[] wrappedTrustManagers = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return origTrustManager.getAcceptedIssuers();
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                        origTrustManager.checkClientTrusted(certs, authType);
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                        origTrustManager.checkServerTrusted(certs, authType);
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, wrappedTrustManagers, null);

        return sslContext;
    }

    public static void showActionModeStatusBar(@NonNull Activity activity, boolean show) {
        var actionModeColor = getAttributeColor(activity, R.attr.actionModeBackground);
        var statusBarColor = getAttributeColor(activity, R.attr.colorSurface);
        var transparent = ColorUtils.setAlphaComponent(actionModeColor, 0);

        var colorFrom = show ? statusBarColor : actionModeColor;
        var colorTo = show ? actionModeColor : transparent;
        var colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);

        colorAnimation.setDuration(MotionUtils.resolveThemeDuration(
                activity,
                R.attr.motionDurationLong1,
                450
        ));
        colorAnimation.addUpdateListener(animation ->
                activity.getWindow().setStatusBarColor((int) animation.getAnimatedValue()));
        colorAnimation.start();
    }

    public static int getAttributeColor(@NonNull Context context, int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getResources().getColor(colorRes, null);

        } catch (Resources.NotFoundException e) {
            return color;
        }

        return color;
    }

    /*
     * Return path to the current torrent download directory.
     * If the directory doesn't exist, the function creates it automatically
     */

    public static Uri getTorrentDownloadPath(@NonNull Context appContext) {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(appContext);
        String path = pref.saveTorrentsIn();

        FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(appContext);

        path = (TextUtils.isEmpty(path) ? fs.getDefaultDownloadPath() : path);

        return (path == null ? null : Uri.parse(fs.normalizeFileSystemPath(path)));
    }

    public static boolean isFileSystemPath(@NonNull Uri path) {
        String scheme = path.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + path.getPath() + " is null");

        return scheme.equals(ContentResolver.SCHEME_FILE);
    }

    public static boolean isSafPath(@NonNull Context appContext, @NonNull Uri path) {
        return SafFileSystem.getInstance(appContext).isSafPath(path);
    }

    public static int getRandomColor() {
        return ((int) (Math.random() * 16777215)) | (0xFF << 24);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public static Intent buildExactAlarmPermissionRequest(@NonNull Context context) {
        var uri = Uri.fromParts("package", context.getPackageName(), null);
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(uri);
    }

    public static void requestExactAlarmPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(buildExactAlarmPermissionRequest(context));
        }
    }

    public static boolean hasManageExternalStoragePermission(@NonNull Context appContext) {
        try {
            var info = appContext.getPackageManager().getPackageInfo(
                    appContext.getPackageName(),
                    PackageManager.GET_PERMISSIONS
            );
            if (info.requestedPermissions == null) {
                return false;
            }
            for (var p : info.requestedPermissions) {
                if ("android.permission.MANAGE_EXTERNAL_STORAGE".equals(p)) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to get permissions", e);
            return false;
        }

        return false;
    }

    public static TorrentFilter getStatusFilterById(@NonNull DrawerStatusFilter filter) {
        switch (filter) {
            case Downloading -> {
                return TorrentFilterCollection.statusDownloading();
            }
            case Downloaded -> {
                return TorrentFilterCollection.statusDownloaded();
            }
            case DownloadingMetadata -> {
                return TorrentFilterCollection.statusDownloadingMetadata();
            }
            case Error -> {
                return TorrentFilterCollection.statusError();
            }
        }

        return TorrentFilterCollection.all();
    }

    @Nullable
    public static DrawerStatusFilter getDrawerStatusFilterByChip(@IdRes int chipId) {
        if (chipId == R.id.drawer_status_downloading) {
            return DrawerStatusFilter.Downloading;
        } else if (chipId == R.id.drawer_status_downloaded) {
            return DrawerStatusFilter.Downloaded;
        } else if (chipId == R.id.drawer_status_downloading_metadata) {
            return DrawerStatusFilter.DownloadingMetadata;
        } else if (chipId == R.id.drawer_status_error) {
            return DrawerStatusFilter.Error;
        }

        return null;
    }

    public static TorrentFilter getDateAddedFilterById(DrawerDateAddedFilter filter) {
        switch (filter) {
            case Today -> {
                return TorrentFilterCollection.dateAddedToday();
            }
            case Yesterday -> {
                return TorrentFilterCollection.dateAddedYesterday();
            }
            case Week -> {
                return TorrentFilterCollection.dateAddedWeek();
            }
            case Month -> {
                return TorrentFilterCollection.dateAddedMonth();
            }
            case Year -> {
                return TorrentFilterCollection.dateAddedYear();
            }
        }

        return TorrentFilterCollection.all();
    }

    @Nullable
    public static DrawerDateAddedFilter getDrawerDateAddedFilterByChip(@IdRes int chipId) {
        if (chipId == R.id.drawer_date_added_today) {
            return DrawerDateAddedFilter.Today;
        } else if (chipId == R.id.drawer_date_added_yesterday) {
            return DrawerDateAddedFilter.Yesterday;
        } else if (chipId == R.id.drawer_date_added_week) {
            return DrawerDateAddedFilter.Week;
        } else if (chipId == R.id.drawer_date_added_month) {
            return DrawerDateAddedFilter.Month;
        } else if (chipId == R.id.drawer_date_added_year) {
            return DrawerDateAddedFilter.Year;
        }

        return null;
    }

    public static TorrentSortingComparator getSortingById(@NonNull DrawerSort sort, BaseSorting.Direction direction) {
        switch (sort) {
            case None -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.none, direction));
            }
            case DateAdded -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.dateAdded, direction));
            }
            case Size -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.size, direction));
            }
            case Name -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.name, direction));
            }
            case Progress -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.progress, direction));
            }
            case Eta -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.ETA, direction));
            }
            case Peers -> {
                return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.peers, direction));
            }
        }
        return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.none, direction));
    }

    public static DrawerSort getDrawerSortingByChip(@IdRes int chipId) {
        if (chipId == R.id.drawer_sorting_date_added) {
            return DrawerSort.DateAdded;
        } else if (chipId == R.id.drawer_sorting_size) {
            return DrawerSort.Size;
        } else if (chipId == R.id.drawer_sorting_name) {
            return DrawerSort.Name;
        } else if (chipId == R.id.drawer_sorting_progress) {
            return DrawerSort.Progress;
        } else if (chipId == R.id.drawer_sorting_ETA) {
            return DrawerSort.Eta;
        } else if (chipId == R.id.drawer_sorting_peers) {
            return DrawerSort.Peers;
        }

        return DrawerSort.None;
    }

    public static BaseSorting.Direction getSortingDirection(@NonNull DrawerSortDirection direction) {
        switch (direction) {
            case Ascending -> {
                return BaseSorting.Direction.ASC;
            }
            case Descending -> {
                return BaseSorting.Direction.DESC;
            }
        }
        return BaseSorting.Direction.DESC;
    }

    public static TorrentFilter getForegroundNotifyFilter(
            @NonNull Context context,
            @NonNull SettingsRepository pref
    ) {
        String val = pref.foregroundNotifyStatusFilter();
        if (val.equals(context.getString(R.string.pref_foreground_notify_status_all_value))) {
            return TorrentFilterCollection.all();
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_status_downloading_value))) {
            return TorrentFilterCollection.statusDownloading();
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_status_downloaded_value))) {
            return TorrentFilterCollection.statusDownloaded();
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_status_downloading_metadata_value))) {
            return TorrentFilterCollection.statusDownloadingMetadata();
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_status_error_value))) {
            return TorrentFilterCollection.statusError();
        }
        throw new IllegalStateException("Unknown filter type: " + val);
    }

    public static TorrentSortingComparator getForegroundNotifySorting(
            @NonNull Context context,
            @NonNull SettingsRepository pref
    ) {
        String val = pref.foregroundNotifySorting();
        if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_name_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.name, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_name_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.name, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_date_added_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.dateAdded, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_date_added_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.dateAdded, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_size_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.size, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_size_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.size, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_progress_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.progress, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_progress_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.progress, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_eta_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.ETA, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_eta_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.ETA, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_peers_asc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.peers, TorrentSorting.Direction.ASC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_peers_desc_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.peers, TorrentSorting.Direction.DESC));
        } else if (val.equals(context.getString(R.string.pref_foreground_notify_sorting_no_sorting_value))) {
            return new TorrentSortingComparator(new TorrentSorting(TorrentSorting.SortingColumns.none, TorrentSorting.Direction.ASC));
        }
        throw new IllegalStateException("Unknown sorting type: " + val);
    }

    public static void enableEdgeToEdge(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
    }

    public static void applyWindowInsets(View view) {
        applyWindowInsets(null, view, WindowInsetsSide.ALL, (baseMask) -> baseMask);
    }

    public static void applyWindowInsets(@Nullable View parent, View child) {
        applyWindowInsets(parent, child, WindowInsetsSide.ALL, (baseMask) -> baseMask);
    }

    public static void applyWindowInsets(View view, @WindowInsetsSide.Flag int sideMask) {
        applyWindowInsets(null, view, sideMask, (baseMask) -> baseMask);
    }

    public static void applyWindowInsets(
            View view,
            @WindowInsetsSide.Flag int sideMask,
            @WindowInsetsCompat.Type.InsetsType int typeMask
    ) {
        applyWindowInsets(null, view, sideMask,
                (baseMask) -> typeMask == -1 ? baseMask : typeMask | baseMask);
    }

    public static void applyWindowInsets(
            View view,
            @WindowInsetsSide.Flag int sideMask,
            WindowInsetsMaskCallback onApplyInsetsMask
    ) {
        applyWindowInsets(null, view, sideMask, onApplyInsetsMask);
    }

    public static void applyWindowInsets(@Nullable View parent,
                                         View child,
                                         @WindowInsetsSide.Flag int sideMask,
                                         WindowInsetsMaskCallback onApplyInsetsMask
    ) {
        var baseTypeMask = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

        var params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        var initialTop = params == null ? 0 : params.topMargin;
        var initialBottom = params == null ? 0 : params.bottomMargin;
        var initialLeft = params == null ? 0 : params.leftMargin;
        var initialRight = params == null ? 0 : params.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(parent == null ? child : parent, (v, windowInsets) -> {
            var insets = windowInsets.getInsets(onApplyInsetsMask.onApply(baseTypeMask));
            var p = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            if ((sideMask & WindowInsetsSide.TOP) != 0) {
                p.topMargin = initialTop + insets.top;
            }
            if ((sideMask & WindowInsetsSide.BOTTOM) != 0) {
                p.bottomMargin = initialBottom + insets.bottom;
            }
            if ((sideMask & WindowInsetsSide.LEFT) != 0) {
                p.leftMargin = initialLeft + insets.left;
            }
            if ((sideMask & WindowInsetsSide.RIGHT) != 0) {
                p.rightMargin = initialRight + insets.right;
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static RecyclerView.ItemDecoration buildListDivider(@NonNull Context context) {
        var divider = new MaterialDividerItemDecoration(context, LinearLayoutManager.VERTICAL);
        divider.setDividerInsetEnd(32);
        divider.setDividerInsetStart(32);
        divider.setLastItemDecorated(false);

        return divider;
    }

    public static Typeface getBoldTypeface(Typeface typeface) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(typeface, 500, false);
        } else {
            return Typeface.create(typeface, Typeface.BOLD);
        }
    }

    public static boolean matchFeedMimeType(@NonNull String mimeType) {
        var pattern = Pattern.compile(FEED_MIME_TYPE_PATTERN, Pattern.CASE_INSENSITIVE);
        return pattern.matcher(mimeType).matches();
    }

    public static boolean matchFeedFilePath(@NonNull String path) {
        var pattern = Pattern.compile(FEED_FILE_PATH_PATTERN, Pattern.CASE_INSENSITIVE);
        return pattern.matcher(path).matches();
    }
}
