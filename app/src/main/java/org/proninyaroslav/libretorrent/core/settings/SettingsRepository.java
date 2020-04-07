/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.settings;

import io.reactivex.Flowable;

public interface SettingsRepository
{
    /*
     * Returns Flowable with key
     */

    Flowable<String> observeSettingsChanged();

    SessionSettings readSessionSettings();

    /*
     * Appearance settings
     */

    String notifySound();

    void notifySound(String val);

    boolean torrentFinishNotify();

    void torrentFinishNotify(boolean val);

    boolean playSoundNotify();

    void playSoundNotify(boolean val);

    boolean ledIndicatorNotify();

    void ledIndicatorNotify(boolean val);

    boolean vibrationNotify();

    void vibrationNotify(boolean val);

    int theme();

    void theme(int val);

    int ledIndicatorColorNotify();

    void ledIndicatorColorNotify(int val);

    /*
     * Behavior settings
     */

    boolean autostart();

    void autostart(boolean val);

    boolean keepAlive();

    void keepAlive(boolean val);

    boolean shutdownDownloadsComplete();

    void shutdownDownloadsComplete(boolean val);

    boolean cpuDoNotSleep();

    void cpuDoNotSleep(boolean val);

    boolean onlyCharging();

    void onlyCharging(boolean val);

    boolean batteryControl();

    void batteryControl(boolean val);

    boolean customBatteryControl();

    void customBatteryControl(boolean val);

    int customBatteryControlValue();

    void customBatteryControlValue(int val);

    boolean unmeteredConnectionsOnly();

    void unmeteredConnectionsOnly(boolean val);

    boolean enableRoaming();

    void enableRoaming(boolean val);

    /*
     * Network settings
     */

    int portRangeFirst();

    void portRangeFirst(int val);

    int portRangeSecond();

    void portRangeSecond(int val);

    boolean enableDht();

    void enableDht(boolean val);

    boolean enableLsd();

    void enableLsd(boolean val);

    boolean enableUtp();

    void enableUtp(boolean val);

    boolean enableUpnp();

    void enableUpnp(boolean val);

    boolean enableNatPmp();

    void enableNatPmp(boolean val);

    boolean useRandomPort();

    void useRandomPort(boolean val);

    boolean encryptInConnections();

    void encryptInConnections(boolean val);

    boolean encryptOutConnections();

    void encryptOutConnections(boolean val);

    boolean enableIpFiltering();

    void enableIpFiltering(boolean val);

    String ipFilteringFile();

    void ipFilteringFile(String val);

    int encryptMode();

    void encryptMode(int val);

    boolean showNatErrors();

    void showNatErrors(boolean val);

    boolean anonymousMode();

    void anonymousMode(boolean val);

    boolean seedingOutgoingConnections();

    void seedingOutgoingConnections(boolean val);

    /*
     * Storage settings
     */

    String saveTorrentsIn();

    void saveTorrentsIn(String val);

    boolean moveAfterDownload();

    void moveAfterDownload(boolean val);

    String moveAfterDownloadIn();

    void moveAfterDownloadIn(String val);

    boolean saveTorrentFiles();

    void saveTorrentFiles(boolean val);

    String saveTorrentFilesIn();

    void saveTorrentFilesIn(String val);

    boolean watchDir();

    void watchDir(boolean val);

    String dirToWatch();

    void dirToWatch(String val);

    /*
     * Limitations settings
     */

    int maxDownloadSpeedLimit();

    void maxDownloadSpeedLimit(int val);

    int maxUploadSpeedLimit();

    void maxUploadSpeedLimit(int val);

    int maxConnections();

    void maxConnections(int val);

    int maxConnectionsPerTorrent();

    void maxConnectionsPerTorrent(int val);

    int maxUploadsPerTorrent();

    void maxUploadsPerTorrent(int val);

    int maxActiveUploads();

    void maxActiveUploads(int val);

    int maxActiveDownloads();

    void maxActiveDownloads(int val);

    int maxActiveTorrents();

    void maxActiveTorrents(int val);

    boolean autoManage();

    void autoManage(boolean val);

    /*
     * Proxy settings
     */

    int proxyType();

    void proxyType(int val);

    String proxyAddress();

    void proxyAddress(String val);

    int proxyPort();

    void proxyPort(int val);

    boolean proxyPeersToo();

    void proxyPeersToo(boolean val);

    boolean proxyRequiresAuth();

    void proxyRequiresAuth(boolean val);

    String proxyLogin();

    void proxyLogin(String val);

    String proxyPassword();

    void proxyPassword(String val);

    boolean proxyChanged();

    void proxyChanged(boolean val);

    boolean applyProxy();

    void applyProxy(boolean val);

    /*
     * Scheduling settings
     */

    boolean enableSchedulingStart();

    void enableSchedulingStart(boolean val);

    boolean enableSchedulingShutdown();

    void enableSchedulingShutdown(boolean val);

    int schedulingStartTime();

    void schedulingStartTime(int val);

    int schedulingShutdownTime();

    void schedulingShutdownTime(int val);

    boolean schedulingRunOnlyOnce();

    void schedulingRunOnlyOnce(boolean val);

    boolean schedulingSwitchWiFi();

    void schedulingSwitchWiFi(boolean val);

    /*
     * Feed settings
     */

    long feedItemKeepTime();

    void feedItemKeepTime(long val);

    boolean autoRefreshFeeds();

    void autoRefreshFeeds(boolean val);

    long refreshFeedsInterval();

    void refreshFeedsInterval(long val);

    boolean autoRefreshFeedsUnmeteredConnectionsOnly();

    void autoRefreshFeedsUnmeteredConnectionsOnly(boolean val);

    boolean autoRefreshFeedsEnableRoaming();

    void autoRefreshFeedsEnableRoaming(boolean val);

    boolean feedStartTorrents();

    void feedStartTorrents(boolean val);

    boolean feedRemoveDuplicates();

    void feedRemoveDuplicates(boolean val);

    /*
     * Streaming settings
     */

    boolean enableStreaming();

    void enableStreaming(boolean val);

    String streamingHostname();

    void streamingHostname(String val);

    int streamingPort();

    void streamingPort(int val);

    /*
     * Logging settings
     */

    boolean logging();

    void logging(boolean val);

    int maxLogSize();

    void maxLogSize(int val);

    boolean logSessionFilter();

    void logSessionFilter(boolean val);

    boolean logDhtFilter();

    void logDhtFilter(boolean val);

    boolean logPeerFilter();

    void logPeerFilter(boolean val);

    boolean logPortmapFilter();

    void logPortmapFilter(boolean val);

    boolean logTorrentFilter();

    void logTorrentFilter(boolean val);
}
