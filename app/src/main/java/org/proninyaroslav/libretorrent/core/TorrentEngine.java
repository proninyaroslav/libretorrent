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

package org.proninyaroslav.libretorrent.core;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.AnnounceEntry;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.swig.settings_pack;
import org.proninyaroslav.libretorrent.MainApplication;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.server.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.ConnectionReceiver;
import org.proninyaroslav.libretorrent.receivers.PowerReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;
import org.proninyaroslav.libretorrent.worker.DeleteTorrentsWorker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

public class TorrentEngine
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentEngine.class.getSimpleName();

    private Context appContext;
    private TorrentSession session;
    private TorrentStreamServer torrentStreamServer;
    private TorrentRepository repo;
    private SharedPreferences pref;
    private TorrentNotifier notifier;
    private CompositeDisposable disposables = new CompositeDisposable();
    private TorrentFileObserver fileObserver;
    private PowerReceiver powerReceiver = new PowerReceiver();
    private ConnectionReceiver connectionReceiver = new ConnectionReceiver();

    private static TorrentEngine INSTANCE;

    public static TorrentEngine getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (TorrentEngine.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentEngine(appContext);
            }
        }

        return INSTANCE;
    }

    private TorrentEngine(@NonNull Context appContext)
    {
        this.appContext = appContext;
        repo = ((MainApplication)appContext).getTorrentRepository();
        pref = SettingsManager.getInstance(appContext).getPreferences();
        notifier = ((MainApplication)appContext).getTorrentNotifier();
        session = new TorrentSession(appContext);
        session.setSettings(SettingsManager.getInstance(appContext).readSessionSettings(appContext));
        session.addListener(engineListener);

        switchConnectionReceiver();
        switchPowerReceiver();
        pref.registerOnSharedPreferenceChangeListener(sharedPrefListener);
    }

    public void start()
    {
        if (isRunning())
            return;

        session.start();
    }

    public void stop()
    {
        if (!isRunning())
            return;

        stopWatchDir();
        stopStreamingServer();
        session.stop();
        cleanTemp();
    }

    public boolean isRunning()
    {
        return session.isRunning();
    }

    public void addListener(TorrentEngineListener listener)
    {
        session.addListener(listener);
    }

    public void removeListener(TorrentEngineListener listener)
    {
        session.removeListener(listener);
    }

    public void rescheduleTorrents()
    {
        if (checkPauseTorrents())
            pauseAll();
        else
            resumeAll();
    }

    public void addTorrent(@NonNull final Torrent torrent,
                           @NonNull String source,
                           boolean fromMagnet,
                           boolean removeFile)
    {
        disposables.add(Completable.fromRunnable(() -> {
            try {
                addTorrentSync(torrent, source, fromMagnet, removeFile);

            } catch (Exception e) {
                handleAddTorrentError(torrent.name, e);
            }
        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void addTorrent(@NonNull Uri file)
    {
        disposables.add(Completable.fromRunnable(() -> {
            ContentResolver contentResolver = appContext.getContentResolver();
            FileInputStream is = null;
            TorrentInfo ti = null;
            try {
                ParcelFileDescriptor outPfd = contentResolver.openFileDescriptor(file, "r");
                FileDescriptor outFd = outPfd.getFileDescriptor();
                is = new FileInputStream(outFd);
                FileChannel chan = is.getChannel();

                try {
                    ti = new TorrentInfo(chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size()));

                } catch (Exception e) {
                    throw new DecodeException(e);
                }
                addTorrentSync(file, ti);

            } catch (Exception e) {
                handleAddTorrentError((ti == null ? file.getPath() : ti.name()), e);
            } finally {
                IOUtils.closeQuietly(is);
            }

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    /*
     * Do not run in the UI thread
     */

    public Torrent addTorrentSync(@NonNull final Torrent torrent,
                                  @NonNull String source,
                                  boolean fromMagnet,
                                  boolean removeFile) throws IOException, FileAlreadyExistsException, DecodeException
    {
        Torrent t = session.addTorrent(torrent, source, fromMagnet, removeFile);

        boolean saveTorrentFile = pref.getBoolean(appContext.getString(R.string.pref_key_save_torrent_files),
                                                  SettingsManager.Default.saveTorrentFiles);
        if (saveTorrentFile) {
            String savePath = pref.getString(appContext.getString(R.string.pref_key_save_torrent_files_in),
                                             t.downloadPath.toString());
            saveTorrentFileIn(t, Uri.parse(org.proninyaroslav.libretorrent.core.utils.FileUtils.normalizeFilesystemPath(savePath)));
        }

        return t;
    }

    public Pair<MagnetInfo, Single<TorrentMetaInfo>> fetchMagnet(@NonNull String uri) throws Exception
    {
        AddTorrentParams params = session.parseMagnetUri(uri);
        AddTorrentParams res_params = session.fetchMagnet(params);
        Single<TorrentMetaInfo> res = createFetchMagnetSingle(params.infoHash().toHex());
        MagnetInfo info = null;
        List<Priority> priorities;
        if (res_params != null) {
            priorities = Arrays.asList(res_params.filePriorities());
            info = new MagnetInfo(uri, res_params.infoHash().toHex(), res_params.name(), priorities);
        }

        return Pair.create(info, res);
    }

    private Single<TorrentMetaInfo> createFetchMagnetSingle(String targetHash)
    {
        return Single.create((emitter) -> {
                TorrentEngineListener listener = new TorrentEngineListener() {
                    @Override
                    public void onMagnetLoaded(String hash, byte[] bencode)
                    {
                        if (!targetHash.equals(hash))
                            return;

                        if (bencode == null)
                            emitter.onError(new NullPointerException());
                        else
                            sendInfoToEmitter(emitter, bencode);
                    }
                };
                if (!emitter.isDisposed()) {
                    /* Check if metadata is already loaded */
                    byte[] bencode = session.getLoadedMagnet(targetHash);
                    if (bencode == null) {
                        session.addListener(listener);
                        emitter.setDisposable(Disposables.fromAction(() ->
                                session.removeListener(listener)));
                    } else {
                        sendInfoToEmitter(emitter, bencode);
                    }
                }
        });
    }

    private void sendInfoToEmitter(SingleEmitter<TorrentMetaInfo> emitter, byte[] bencode)
    {
        TorrentMetaInfo info;
        try {
            info = new TorrentMetaInfo(bencode);

        } catch (DecodeException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            if (!emitter.isDisposed())
                emitter.onError(e);
            return;
        }

        if (!emitter.isDisposed())
            emitter.onSuccess(info);
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public void cancelFetchMagnet(@NonNull String infoHash)
    {
        if (!isRunning())
            return;

        session.cancelFetchMagnet(infoHash);
    }

    public void pauseResumeTorrents(@NonNull List<String> ids)
    {
        for (String id : ids) {
            if (id == null)
                continue;

            pauseResumeTorrent(id);
        }
    }

    public void pauseResumeTorrent(@NonNull String id)
    {
        disposables.add(Completable.fromRunnable(() -> {
            TorrentDownload task = session.getTask(id);
            if (task == null)
                return;
            try {
                if (task.isPaused())
                    task.resume();
                else
                    task.pause();

            } catch (Exception e) {
                /* Ignore */
            }

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void forceRecheckTorrents(@NonNull List<String> ids)
    {
        if (!isRunning())
            return;

        for (String id : ids) {
            if (id == null)
                continue;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.forceRecheck();
        }
    }

    public void forceAnnounceTorrents(@NonNull List<String> ids)
    {
        if (!isRunning())
            return;

        for (String id : ids) {
            if (id == null)
                continue;

            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.requestTrackerAnnounce();
        }
    }

    public void deleteTorrents(@NonNull List<String> ids, boolean withFiles)
    {
        runDeleteTorrentsWorker(ids.toArray(new String[0]), withFiles);
    }

    public void replaceTrackers(@NonNull String id, @NonNull List<String> urls)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            task.replaceTrackers(new HashSet<>(urls));
    }

    public void addTrackers(@NonNull String id, @NonNull List<String> urls)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }

    public String makeMagnet(@NonNull String id, boolean includePriorities)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet(includePriorities);
    }

    public TorrentMetaInfo getTorrentMetaInfo(@NonNull String id)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        TorrentInfo ti = task.getTorrentInfo();
        TorrentMetaInfo info = null;
        try {
            if (ti != null)
                info = new TorrentMetaInfo(ti);
            else
                info = new TorrentMetaInfo(task.getTorrentName(), task.getInfoHash());

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public boolean[] getPieces(@NonNull String id)
    {
        if (!isRunning())
            return new boolean[0];

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new boolean[0];

        return task.pieces();
    }

    public boolean isTorrentsFinished()
    {
        if (!isRunning())
            return true;

        List<TorrentStateCode> inProgressStates = Arrays.asList(TorrentStateCode.DOWNLOADING,
                TorrentStateCode.PAUSED,
                TorrentStateCode.CHECKING,
                TorrentStateCode.DOWNLOADING_METADATA,
                TorrentStateCode.ALLOCATING);

        for (TorrentDownload task : session.getTasks())
            if (inProgressStates.contains(task.getStateCode()) || task.isDuringChangeParams())
                return false;

        return true;
    }

    public void pauseAll()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.pauseAll();

        }).observeOn(Schedulers.io())
          .subscribe());
    }

    public void resumeAll()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.resumeAll();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public void changeParams(@NonNull String id,
                             @NonNull ChangeableParams params)
    {
        disposables.add(Completable.fromRunnable(() -> {
            TorrentDownload task = session.getTask(id);
            if (task != null)
                task.applyParams(params);

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    public TorrentStream getStream(@NonNull String id, int fileIndex)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getStream(fileIndex);
    }

    public TorrentInputStream getTorrentInputStream(@NonNull TorrentStream stream)
    {
        return new TorrentInputStream(session, stream);
    }

    /*
     * Do not run in the UI thread
     */

    public BasicStateParcel makeBasicStateSync(@NonNull String id)
    {
        if (!isRunning())
            return null;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        return makeBasicState(torrent);
    }

    private BasicStateParcel makeBasicState(Torrent torrent)
    {
        TorrentDownload task = session.getTask(torrent.id);
        if (task == null)
            return new BasicStateParcel(torrent.id,
                    torrent.name,
                    torrent.dateAdded,
                    torrent.error);
        else
            return new BasicStateParcel(
                    torrent.id,
                    torrent.name,
                    task.getStateCode(),
                    task.getProgress(),
                    task.getTotalReceivedBytes(),
                    task.getTotalSentBytes(),
                    task.getTotalWanted(),
                    task.getDownloadSpeed(),
                    task.getUploadSpeed(),
                    task.getETA(),
                    torrent.dateAdded,
                    task.getTotalPeers(),
                    task.getConnectedPeers(),
                    torrent.error);
    }

    /*
     * Do not run in the UI thread
     */

    public List<BasicStateParcel> makeBasicStateListSync()
    {
        ArrayList<BasicStateParcel> stateList = new ArrayList<>();

        if (!isRunning())
            return stateList;

        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null)
                continue;
            stateList.add(makeBasicState(torrent));
        }

        return stateList;
    }

    /*
     * Do not run in the UI thread
     */

    public AdvanceStateParcel makeAdvancedStateSync(@NonNull String id)
    {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        Torrent torrent = repo.getTorrentById(id);
        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvanceStateParcel(
                torrent.id,
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail));
    }

    public List<TrackerStateParcel> makeTrackerStateParcelList(@NonNull String id)
    {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = session.isDHTEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = session.isLSDEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = session.isPeXEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;

        states.add(new TrackerStateParcel(TrackerStateParcel.DHT_ENTRY_NAME, "", -1, statusDHT));
        states.add(new TrackerStateParcel(TrackerStateParcel.LSD_ENTRY_NAME, "", -1, statusLSD));
        states.add(new TrackerStateParcel(TrackerStateParcel.PEX_ENTRY_NAME, "", -1, statusPeX));

        for (AnnounceEntry entry : trackers) {
            String url = entry.url();
            /* Prevent duplicate */
            if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
                continue;
            }

            states.add(new TrackerStateParcel(entry));
        }

        return states;
    }

    public List<PeerStateParcel> makePeerStateParcelList(@NonNull String id)
    {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        ArrayList<PeerStateParcel> states = new ArrayList<>();
        List<AdvancedPeerInfo> peers = task.advancedPeerInfo();

        TorrentStatus status = task.getTorrentStatus();
        if (status == null)
            return states;

        for (AdvancedPeerInfo peer : peers) {
            PeerStateParcel state = new PeerStateParcel(peer, status);
            states.add(state);
        }

        return states;
    }

    /*
     * Do not call directly
     */

    public void doDeleteTorrent(@NonNull Torrent torrent, boolean withFiles)
    {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(torrent.id);
        if (task != null)
            task.remove(withFiles);
    }

    private void saveTorrentFileIn(@NonNull Torrent torrent,
                                   @NonNull Uri saveDir)
    {
        String torrentFileName = torrent.name + ".torrent";
        try {
            if (!saveTorrentFile(torrent.id, saveDir, torrentFileName))
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    private boolean saveTorrentFile(String id, Uri destDir, String fileName) throws IOException
    {
        String pathToTorrent = repo.getTorrentFile(appContext, id);
        if (pathToTorrent == null)
            return false;

        File torrent = new File(pathToTorrent);
        if (!torrent.exists())
            return false;

        String name = (fileName != null ? fileName : id);

        Pair<Uri, String> ret = FileUtils.createFile(appContext, destDir, name, Utils.MIME_TORRENT, true);
        if (ret == null || ret.first == null)
            return false;

        FileUtils.copyFile(appContext, Uri.fromFile(torrent), ret.first);

        return true;
    }

    private void runDeleteTorrentsWorker(String[] idList, boolean withFiles)
    {
        Data data = new Data.Builder()
                .putStringArray(DeleteTorrentsWorker.TAG_ID_LIST, idList)
                .putBoolean(DeleteTorrentsWorker.TAG_WITH_FILES, withFiles)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(DeleteTorrentsWorker.class)
                .setInputData(data)
                .build();
        WorkManager.getInstance().enqueue(work);
    }

    private void switchPowerReceiver()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                SettingsManager.Default.customBatteryControl);
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging),
                SettingsManager.Default.onlyCharging);

        try {
            appContext.unregisterReceiver(powerReceiver);

        } catch (IllegalArgumentException e)
        {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
            /* Custom receiver doesn't send sticky intent, reschedule manually */
            rescheduleTorrents();
        } else if (batteryControl || onlyCharging) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getFilter());
        }
    }

    private void switchConnectionReceiver()
    {
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                SettingsManager.Default.enableRoaming);

        try {
            appContext.unregisterReceiver(connectionReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (unmeteredOnly || roaming)
            appContext.registerReceiver(connectionReceiver, ConnectionReceiver.getFilter());
    }

    private boolean checkPauseTorrents()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                SettingsManager.Default.customBatteryControl);
        int customBatteryControlValue = pref.getInt(appContext.getString(R.string.pref_key_custom_battery_control_value),
                Utils.getDefaultBatteryLowLevel());
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging),
                SettingsManager.Default.onlyCharging);
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                SettingsManager.Default.enableRoaming);

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }

    private void initSession()
    {
        loadTorrents();

        if (!pref.getBoolean(appContext.getString(R.string.pref_key_use_random_port),
                SettingsManager.Default.useRandomPort)) {
            int portFirst = pref.getInt(appContext.getString(R.string.pref_key_port_range_first),
                                        SettingsManager.Default.portRangeFirst);
            int portSecond = pref.getInt(appContext.getString(R.string.pref_key_port_range_second),
                                         SettingsManager.Default.portRangeSecond);
            session.setPortRange(portFirst, portSecond);
        }

        if (pref.getBoolean(appContext.getString(R.string.pref_key_proxy_changed),
                SettingsManager.Default.proxyChanged)) {
            pref.edit().putBoolean(appContext.getString(R.string.pref_key_proxy_changed), false).apply();
            setProxy();
        }

        if (pref.getBoolean(appContext.getString(R.string.pref_key_enable_ip_filtering),
                SettingsManager.Default.enableIpFiltering)) {
            String path = pref.getString(appContext.getString(R.string.pref_key_ip_filtering_file),
                                         SettingsManager.Default.ipFilteringFile);
            if (path != null)
                session.enableIpFilter(Uri.parse(FileUtils.normalizeFilesystemPath(path)));
        }

        if (pref.getBoolean(appContext.getString(R.string.pref_key_watch_dir),
                SettingsManager.Default.watchDir))
            startWatchDir();

        boolean enableStreaming = pref.getBoolean(appContext.getString(R.string.pref_key_streaming_enable),
                                                  SettingsManager.Default.enableStreaming);
        if (enableStreaming)
            startStreamingServer();
    }

    private void startStreamingServer()
    {
        stopStreamingServer();

        String hostname = pref.getString(appContext.getString(R.string.pref_key_streaming_hostname),
                                         SettingsManager.Default.streamingHostname);
        int port = pref.getInt(appContext.getString(R.string.pref_key_streaming_port),
                               SettingsManager.Default.streamingPort);

        torrentStreamServer = new TorrentStreamServer(hostname, port);
        try {
            torrentStreamServer.start(appContext);

        } catch (IOException e) {
            notifier.makeErrorNotify(appContext.getString(R.string.pref_streaming_error));
        }
    }

    private void stopStreamingServer()
    {
        if (torrentStreamServer != null)
            torrentStreamServer.stop();
        torrentStreamServer = null;
    }

    private void loadTorrents()
    {
        disposables.add(Completable.fromRunnable(() -> {
            if (isRunning())
                session.restoreDownloads();

        }).subscribeOn(Schedulers.io())
          .subscribe());
    }

    private void setProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();
        ProxySettingsPack.ProxyType type = ProxySettingsPack.ProxyType.fromValue(
                pref.getInt(appContext.getString(R.string.pref_key_proxy_type),
                        SettingsManager.Default.proxyType));
        proxy.setType(type);
        if (type == ProxySettingsPack.ProxyType.NONE)
            session.setProxy(proxy);
        proxy.setAddress(pref.getString(appContext.getString(R.string.pref_key_proxy_address),
                SettingsManager.Default.proxyAddress));
        proxy.setPort(pref.getInt(appContext.getString(R.string.pref_key_proxy_port),
                SettingsManager.Default.proxyPort));
        proxy.setProxyPeersToo(pref.getBoolean(appContext.getString(R.string.pref_key_proxy_peers_too),
                SettingsManager.Default.proxyPeersToo));
        if (pref.getBoolean(appContext.getString(R.string.pref_key_proxy_requires_auth),
                SettingsManager.Default.proxyRequiresAuth)) {
            proxy.setLogin(pref.getString(appContext.getString(R.string.pref_key_proxy_login),
                    SettingsManager.Default.proxyLogin));
            proxy.setPassword(pref.getString(appContext.getString(R.string.pref_key_proxy_password),
                    SettingsManager.Default.proxyPassword));
        }
        session.setProxy(proxy);
    }

    private int getEncryptMode()
    {
        int mode = pref.getInt(appContext.getString(R.string.pref_key_enc_mode),
                               SettingsManager.Default.encryptMode(appContext));

        if (mode == Integer.parseInt(appContext.getString(R.string.pref_enc_mode_prefer_value)))
            return settings_pack.enc_policy.pe_enabled.swigValue();
        else if (mode == Integer.parseInt(appContext.getString(R.string.pref_enc_mode_require_value)))
            return settings_pack.enc_policy.pe_forced.swigValue();
        else
            return settings_pack.enc_policy.pe_disabled.swigValue();
    }

    private void startWatchDir()
    {
        String dir = pref.getString(appContext.getString(R.string.pref_key_dir_to_watch),
                                    SettingsManager.Default.dirToWatch);
        Uri uri = Uri.parse(FileUtils.normalizeFilesystemPath(dir));
        /* TODO: SAF support */
        if (FileUtils.isSAFPath(uri))
            throw new IllegalArgumentException("SAF is not supported:" + uri);
        dir = uri.getPath();

        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir()
    {
        if (fileObserver == null)
            return;

        fileObserver.stopWatching();
        fileObserver = null;
    }

    private TorrentFileObserver makeTorrentFileObserver(String pathToDir)
    {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name)
            {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;

                addTorrent(Uri.fromFile(f));
            }
        };
    }

    private void scanTorrentsInDir(String pathToDir)
    {
        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : org.apache.commons.io.FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(Uri.fromFile(file));
        }
    }

    private Torrent addTorrentSync(Uri file, TorrentInfo ti)
            throws IOException, FreeSpaceException,
            FileAlreadyExistsException, DecodeException
    {
        ArrayList<Priority> priorities = new ArrayList<>(Collections.nCopies(ti.files().numFiles(), Priority.DEFAULT));
        String downloadPath = pref.getString(appContext.getString(R.string.pref_key_save_torrents_in),
                                             SettingsManager.Default.saveTorrentsIn);
        Torrent torrent = new Torrent(ti.infoHash().toHex(),
                file.toString(),
                Uri.parse(FileUtils.normalizeFilesystemPath(downloadPath)),
                ti.name(),
                priorities,
                System.currentTimeMillis());

        if (FileUtils.getDirAvailableBytes(appContext, Uri.parse(downloadPath)) < ti.totalSize())
            throw new FreeSpaceException();

        return addTorrentSync(torrent, file.toString(), false, false);
    }

    private void handleAddTorrentError(String name, Throwable e)
    {
        if (e instanceof FileAlreadyExistsException) {
            notifier.makeTorrentInfoNotify(name, appContext.getString(R.string.torrent_exist));
            return;
        }
        Log.e(TAG, Log.getStackTraceString(e));
        String message;
        if (e instanceof FileNotFoundException)
            message = appContext.getString(R.string.error_file_not_found_add_torrent);
        else if (e instanceof IOException)
            message = appContext.getString(R.string.error_io_add_torrent);
        else
            message = appContext.getString(R.string.error_add_torrent);
        notifier.makeTorrentErrorNotify(name, message);
    }

    private void cleanTemp()
    {
        try {
            FileUtils.cleanTempDir(appContext);

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStarted()
        {
            initSession();
        }

        @Override
        public void onTorrentAdded(String id)
        {
            if (checkPauseTorrents() && isRunning()) {
                disposables.add(Completable.fromRunnable(() -> {
                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.pause();

                }).subscribeOn(Schedulers.io())
                  .subscribe());
            }
        }

        @Override
        public void onTorrentFinished(String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter((torrent) -> torrent != null && !torrent.finished)
                    .subscribe((torrent) -> {
                                if (pref.getBoolean(appContext.getString(R.string.pref_key_move_after_download),
                                                    SettingsManager.Default.moveAfterDownload)) {
                                    String curPath = torrent.downloadPath.toString();
                                    String newPath = pref.getString(appContext.getString(R.string.pref_key_move_after_download_in), curPath);
                                    newPath = FileUtils.normalizeFilesystemPath(newPath);

                                    if (!curPath.equals(newPath)) {
                                        ChangeableParams params = new ChangeableParams();
                                        params.dirPath = Uri.parse(newPath);

                                        TorrentDownload task = session.getTask(id);
                                        if (task != null)
                                            task.applyParams(params);
                                    }
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMoving(String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeMovingTorrentNotify(name);
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMoved(String id, boolean success)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                if (success)
                                    notifier.makeTorrentInfoNotify(name,
                                            appContext.getString(R.string.torrent_move_success));
                                else
                                    notifier.makeTorrentErrorNotify(name,
                                            appContext.getString(R.string.torrent_move_fail));
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onIpFilterParsed(boolean success)
        {
            Toast.makeText(appContext,
                    (success ? appContext.getString(R.string.ip_filter_add_success) :
                            appContext.getString(R.string.ip_filter_add_error)),
                    Toast.LENGTH_LONG)
                    .show();
        }

        @Override
        public void onSessionError(String errorMsg)
        {
           notifier.makeSessionErrorNotify(errorMsg);
        }

        @Override
        public void onNatError(String errorMsg)
        {
            Log.e(TAG, "NAT error: " + errorMsg);
            if (pref.getBoolean(appContext.getString(R.string.pref_key_show_nat_errors),
                                SettingsManager.Default.showNatErrors))
                notifier.makeNatErrorNotify(errorMsg);
        }

        @Override
        public void onRestoreSessionError(String id)
        {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeTorrentErrorNotify(name,
                                        appContext.getString(R.string.restore_torrent_error));
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }

        @Override
        public void onTorrentMetadataLoaded(String id, Exception err)
        {
            if (err != null) {
                Log.e(TAG, "Load metadata error: ");
                Log.e(TAG, Log.getStackTraceString(err));
            }

            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter((torrent) -> torrent != null)
                    .subscribe((torrent) -> {
                                if (err == null) {
                                    if (pref.getBoolean(appContext.getString(R.string.pref_key_save_torrent_files),
                                                        SettingsManager.Default.saveTorrentFiles)) {
                                        String path = pref.getString(appContext.getString(R.string.pref_key_save_torrent_files_in),
                                                                     torrent.downloadPath.toString());
                                        saveTorrentFileIn(torrent, Uri.parse(FileUtils.normalizeFilesystemPath(path)));
                                    }

                                } else if (err instanceof FreeSpaceException) {
                                    notifier.makeTorrentErrorNotify(torrent.name, appContext.getString(R.string.error_free_space));
                                }
                            },
                            (Throwable t) -> {
                                Log.e(TAG, "Getting torrent " + id + " error: " +
                                        Log.getStackTraceString(t));
                            })
            );
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener = (sharedPreferences, key) -> {
        boolean reschedule = false;

        if (key.equals(appContext.getString(R.string.pref_key_umnetered_connections_only)) ||
            key.equals(appContext.getString(R.string.pref_key_enable_roaming))) {
            reschedule = true;
            switchConnectionReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                   key.equals(appContext.getString(R.string.pref_key_battery_control))) {
            reschedule = true;
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_custom_battery_control)) ||
                   key.equals(appContext.getString(R.string.pref_key_custom_battery_control_value))) {
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_max_download_speed))) {
            TorrentSession.Settings s = session.getSettings();
            s.downloadRateLimit = pref.getInt(key, SettingsManager.Default.maxDownloadSpeedLimit);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_upload_speed))) {
            TorrentSession.Settings s = session.getSettings();
            s.uploadRateLimit = pref.getInt(key, SettingsManager.Default.maxUploadSpeedLimit);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections))) {
            TorrentSession.Settings s = session.getSettings();
            s.connectionsLimit = pref.getInt(key, SettingsManager.Default.maxConnections);
            s.maxPeerListSize = s.connectionsLimit;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections_per_torrent))) {
            session.setMaxConnectionsPerTorrent(pref.getInt(key, SettingsManager.Default.maxConnectionsPerTorrent));

        } else if (key.equals(appContext.getString(R.string.pref_key_max_uploads_per_torrent))) {
            session.setMaxUploadsPerTorrent(pref.getInt(key, SettingsManager.Default.maxUploadsPerTorrent));

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_downloads))) {
            TorrentSession.Settings s = session.getSettings();
            s.activeDownloads = pref.getInt(key, SettingsManager.Default.maxActiveDownloads);
            session.setSettings(s);
            
        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_uploads))) {
            TorrentSession.Settings s = session.getSettings();
            s.activeSeeds = pref.getInt(key, SettingsManager.Default.maxActiveUploads);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_torrents))) {
            TorrentSession.Settings s = session.getSettings();
            s.activeLimit = pref.getInt(key, SettingsManager.Default.maxActiveTorrents);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_dht))) {
            TorrentSession.Settings s = session.getSettings();
            s.dhtEnabled = pref.getBoolean(appContext.getString(R.string.pref_key_enable_dht),
                                           SettingsManager.Default.enableDht);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_lsd))) {
            TorrentSession.Settings s = session.getSettings();
            s.lsdEnabled = pref.getBoolean(appContext.getString(R.string.pref_key_enable_lsd),
                                           SettingsManager.Default.enableLsd);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_utp))) {
            TorrentSession.Settings s = session.getSettings();
            s.utpEnabled = pref.getBoolean(appContext.getString(R.string.pref_key_enable_utp),
                                           SettingsManager.Default.enableUtp);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_upnp))) {
            TorrentSession.Settings s = session.getSettings();
            s.upnpEnabled = pref.getBoolean(appContext.getString(R.string.pref_key_enable_upnp),
                                            SettingsManager.Default.enableUpnp);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_natpmp))) {
            TorrentSession.Settings s = session.getSettings();
            s.natPmpEnabled = pref.getBoolean(appContext.getString(R.string.pref_key_enable_natpmp),
                                              SettingsManager.Default.enableNatPmp);
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_mode))) {
            TorrentSession.Settings s = session.getSettings();
            s.encryptMode = getEncryptMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_in_connections))) {
            TorrentSession.Settings s = session.getSettings();
            int state = settings_pack.enc_policy.pe_disabled.swigValue();
            s.encryptInConnections = pref.getBoolean(appContext.getString(R.string.pref_key_enc_in_connections),
                                                     SettingsManager.Default.encryptInConnections);
            if (s.encryptInConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_out_connections))) {
            TorrentSession.Settings s = session.getSettings();
            int state = settings_pack.enc_policy.pe_disabled.swigValue();
            s.encryptOutConnections = pref.getBoolean(appContext.getString(R.string.pref_key_enc_out_connections),
                                                      SettingsManager.Default.encryptOutConnections);
            if (s.encryptOutConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_use_random_port))) {
            if (pref.getBoolean(appContext.getString(R.string.pref_key_use_random_port),
                                SettingsManager.Default.useRandomPort)) {
                session.setRandomPortRange();
            } else {
                int portFirst = pref.getInt(appContext.getString(R.string.pref_key_port_range_first),
                                            SettingsManager.Default.portRangeFirst);
                int portSecond = pref.getInt(appContext.getString(R.string.pref_key_port_range_second),
                                             SettingsManager.Default.portRangeSecond);
                session.setPortRange(portFirst, portSecond);
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_port_range_first)) ||
                   key.equals(appContext.getString(R.string.pref_key_port_range_second))) {
            int portFirst = pref.getInt(appContext.getString(R.string.pref_key_port_range_first),
                                        SettingsManager.Default.portRangeFirst);
            int portSecond = pref.getInt(appContext.getString(R.string.pref_key_port_range_second),
                                         SettingsManager.Default.portRangeSecond);
            session.setPortRange(portFirst, portSecond);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_ip_filtering))) {
            if (pref.getBoolean(appContext.getString(R.string.pref_key_enable_ip_filtering),
                                SettingsManager.Default.enableIpFiltering)) {
                String path = pref.getString(appContext.getString(R.string.pref_key_ip_filtering_file),
                                             SettingsManager.Default.ipFilteringFile);
                if (path != null)
                    session.enableIpFilter(Uri.parse(FileUtils.normalizeFilesystemPath(path)));
            } else {
                session.disableIpFilter();
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_ip_filtering_file))) {
            String path = pref.getString(appContext.getString(R.string.pref_key_ip_filtering_file),
                                         SettingsManager.Default.ipFilteringFile);
            if (path != null)
                session.enableIpFilter(Uri.parse(FileUtils.normalizeFilesystemPath(path)));

        } else if (key.equals(appContext.getString(R.string.pref_key_apply_proxy))) {
            pref.edit().putBoolean(appContext.getString(R.string.pref_key_proxy_changed), false).apply();
            setProxy();
            Toast.makeText(appContext,
                    R.string.proxy_settings_applied,
                    Toast.LENGTH_SHORT)
                    .show();

        } else if (key.equals(appContext.getString(R.string.pref_key_auto_manage))) {
            session.setAutoManaged(pref.getBoolean(key, SettingsManager.Default.autoManage));

        } else if (key.equals(appContext.getString(R.string.pref_key_watch_dir))) {
            if (pref.getBoolean(appContext.getString(R.string.pref_key_watch_dir), SettingsManager.Default.watchDir))
                startWatchDir();
            else
                stopWatchDir();

        } else if (key.equals(appContext.getString(R.string.pref_key_dir_to_watch))) {
            stopWatchDir();
            startWatchDir();

        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_enable))) {
            if (pref.getBoolean(key, SettingsManager.Default.enableStreaming))
                startStreamingServer();
            else
                stopStreamingServer();

        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_port)) ||
                key.equals(appContext.getString(R.string.pref_key_streaming_hostname))) {
            startStreamingServer();
        }

        if (reschedule)
            rescheduleTorrents();
    };
}