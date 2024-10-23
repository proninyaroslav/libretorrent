/*
 * Copyright (C) 2016-2024 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.io.FileUtils;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.BDecodeNode;
import com.frostwire.jlibtorrent.ErrorCode;
import com.frostwire.jlibtorrent.Pair;
import com.frostwire.jlibtorrent.SessionHandle;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.ListenFailedAlert;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.frostwire.jlibtorrent.alerts.PortmapErrorAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.SessionErrorAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.alert;
import com.frostwire.jlibtorrent.swig.alert_category_t;
import com.frostwire.jlibtorrent.swig.announce_entry;
import com.frostwire.jlibtorrent.swig.bdecode_node;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.session_params;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.string_vector;
import com.frostwire.jlibtorrent.swig.tcp_endpoint_vector;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.model.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.model.data.MagnetInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileDescriptorWrapper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacade;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class TorrentSessionImpl extends SessionManager
        implements TorrentSession
{
    private static final String TAG = TorrentSession.class.getSimpleName();

    private static final int[] INNER_LISTENER_TYPES = new int[] {
            AlertType.ADD_TORRENT.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.SESSION_ERROR.swig(),
            AlertType.PORTMAP_ERROR.swig(),
            AlertType.LISTEN_FAILED.swig(),
            AlertType.LOG.swig(),
            AlertType.DHT_LOG.swig(),
            AlertType.PEER_LOG.swig(),
            AlertType.PORTMAP_LOG.swig(),
            AlertType.TORRENT_LOG.swig(),
            AlertType.SESSION_STATS.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
    };

    /* Base unit in KiB. Used for create torrent */
    private static final int[] pieceSize = {0, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
    /* Possible torrent versions to create. Zero is hybrid */
    private static final int[] torrentVersions = {0, 1, 2};
    private static final String PEER_FINGERPRINT = "Lr"; /* called peer id */
    private static final String USER_AGENT = "LibreTorrent %s";

    private InnerListener innerListener;
    private ConcurrentLinkedQueue<TorrentEngineListener> listeners = new ConcurrentLinkedQueue<>();
    private SessionSettings settings = new SessionSettings();
    private ReentrantLock settingsLock = new ReentrantLock();
    private Queue<LoadTorrentTask> restoreTorrentsQueue = new LinkedList<>();
    private ExecutorService loadTorrentsExec;
    private ConcurrentHashMap<String, TorrentDownload> torrentTasks = new ConcurrentHashMap<>();
    /* Wait list for non added magnets */
    private HashSet<String> magnets = new HashSet<>();
    private ConcurrentHashMap<String, byte[]> loadedMagnets = new ConcurrentHashMap<>();
    private ArrayList<String> addTorrentsList = new ArrayList<>();
    private ReentrantLock syncMagnet = new ReentrantLock();
    private CompositeDisposable disposables = new CompositeDisposable();
    private TorrentRepository repo;
    private FileSystemFacade fs;
    private SystemFacade system;
    private SessionLogger sessionLogger;
    private boolean started;
    private AtomicBoolean stopRequested;
    private Thread parseIpFilterThread;

    public TorrentSessionImpl(@NonNull TorrentRepository repo,
                              @NonNull FileSystemFacade fs,
                              @NonNull SystemFacade system)
    {
        super(false);

        this.stopRequested = new AtomicBoolean(false);
        this.started = false;
        this.sessionLogger = new SessionLogger();
        this.repo = repo;
        this.fs = fs;
        this.system = system;
        innerListener = new InnerListener();
        loadTorrentsExec = Executors.newCachedThreadPool();
    }

    @Override
    public SessionLogger getLogger()
    {
        return sessionLogger;
    }

    @Override
    public void addListener(TorrentEngineListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeListener(TorrentEngineListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public TorrentDownload getTask(String id)
    {
        return torrentTasks.get(id);
    }

    public void setSettings(@NonNull SessionSettings settings) {
        setSettings(settings, true);
    }

    @Override
    public void setSettings(@NonNull SessionSettings settings, boolean keepPort)
    {
        settingsLock.lock();

        try {
            this.settings = settings;
            applySettings(settings, keepPort);

        } finally {
            settingsLock.unlock();
        }
    }

    @Override
    public SessionSettings getSettings()
    {
        settingsLock.lock();

        try {
            return new SessionSettings(settings);

        } finally {
            settingsLock.unlock();
        }
    }

    @Override
    public byte[] getLoadedMagnet(String hash)
    {
        return loadedMagnets.get(hash);
    }

    @Override
    public void removeLoadedMagnet(String hash)
    {
        loadedMagnets.remove(hash);
    }

    private boolean operationNotAllowed()
    {
        return swig() == null || stopRequested.get();
    }

    @Override
    public Torrent addTorrent(
            @NonNull AddTorrentParams params,
            boolean removeFile
    ) throws
            IOException,
            TorrentAlreadyExistsException,
            DecodeException,
            UnknownUriException
    {
        if (operationNotAllowed())
            return null;

        Torrent torrent = new Torrent(
                params.sha1hash,
                params.downloadPath,
                params.name,
                params.addPaused, System.currentTimeMillis(),
                params.sequentialDownload,
                params.firstLastPiecePriority
        );

        byte[] bencode = null;
        if (params.fromMagnet) {
            bencode = getLoadedMagnet(params.sha1hash);
            removeLoadedMagnet(params.sha1hash);
            if (bencode == null)
                torrent.setMagnetUri(params.source);
        }

        if (repo.getTorrentById(torrent.id) != null) {
            mergeTorrent(torrent.id, params, bencode);
            throw new TorrentAlreadyExistsException();
        }

        repo.addTorrent(torrent);
        if (!params.tags.isEmpty()) {
            repo.replaceTags(torrent.id, params.tags);
        }

        if (!torrent.isDownloadingMetadata()) {
            /*
             * This is possible if the magnet data came after Torrent object
             * has already been created and nothing is known about the received data
             */
            if (params.filePriorities.length == 0) {
                try (FileDescriptorWrapper w = fs.getFD(Uri.parse(params.source))) {
                    FileDescriptor outFd = w.open("r");
                    try (FileInputStream is = new FileInputStream(outFd)) {
                        TorrentMetaInfo info = new TorrentMetaInfo(is);
                        params.filePriorities = new Priority[info.fileCount];
                        Arrays.fill(params.filePriorities, Priority.DEFAULT);
                    }
                } catch (FileNotFoundException e) {
                    /* Ignore */
                }
            }
        }

        try {
            download(torrent.id, params, bencode);

        } catch (Exception e) {
            repo.deleteTorrent(torrent);
            throw e;
        } finally {
            if (removeFile && !params.fromMagnet) {
                try {
                    fs.deleteFile(Uri.parse(params.source));
                } catch (UnknownUriException e) {
                    // Ignore
                }
            }
        }

        return torrent;
    }

    private void download(String id, AddTorrentParams params, byte[] bencode) throws IOException, UnknownUriException
    {
        if (operationNotAllowed())
            return;

        cancelFetchMagnet(id);

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            throw new IOException("Torrent " + id + " is null");

        addTorrentsList.add(params.sha1hash);

        String path = fs.makeFileSystemPath(params.downloadPath);
        File saveDir = new File(path);
        if (torrent.isDownloadingMetadata()) {
            download(params.source, saveDir, params.addPaused, params.sequentialDownload);
            return;
        }

        TorrentDownload task = torrentTasks.get(torrent.id);
        if (task != null)
            task.remove(false);

        if (params.fromMagnet) {
            download(bencode,
                    saveDir,
                    params.filePriorities,
                    params.sequentialDownload,
                    params.addPaused,
                    null);
        } else {
            try (FileDescriptorWrapper w = fs.getFD(Uri.parse(params.source))) {
                FileDescriptor fd = w.open("r");
                try (FileInputStream fin = new FileInputStream(fd)) {
                    FileChannel chan = fin.getChannel();

                    download(new TorrentInfo(chan.map(FileChannel.MapMode.READ_ONLY, 0, chan.size())),
                            saveDir,
                            params.filePriorities,
                            params.sequentialDownload,
                            params.addPaused,
                            null);
                }
            }
        }
    }

    @Override
    public void deleteTorrent(@NonNull String id, boolean withFiles)
    {
        if (operationNotAllowed())
            return;

        TorrentDownload task = getTask(id);
        if (task == null) {
            Torrent torrent = repo.getTorrentById(id);
            if (torrent != null)
                repo.deleteTorrent(torrent);

            notifyListeners((listener) ->
                    listener.onTorrentRemoved(id));
        } else {
            task.remove(withFiles);
        }
    }

    @Override
    public void restoreTorrents()
    {
        if (operationNotAllowed())
            return;

        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null || isTorrentAlreadyRunning(torrent.id))
                continue;

            String path = null;
            try {
                path = fs.makeFileSystemPath(torrent.downloadPath);
            } catch (UnknownUriException e) {
                Log.e(TAG, "Unable to restore torrent:");
                Log.e(TAG, Log.getStackTraceString(e));
            }
            LoadTorrentTask loadTask = new LoadTorrentTask(torrent.id);
            if (path != null && torrent.isDownloadingMetadata()) {
                loadTask.putMagnet(
                        torrent.getMagnet(),
                        new File(path),
                        torrent.manuallyPaused,
                        torrent.sequentialDownload
                );
            }
            restoreTorrentsQueue.add(loadTask);
        }
        runNextLoadTorrentTask();
    }

    @Override
    public MagnetInfo fetchMagnet(@NonNull String uri) throws Exception
    {
        if (operationNotAllowed())
            return null;

        com.frostwire.jlibtorrent.AddTorrentParams params = parseMagnetUri(uri);
        com.frostwire.jlibtorrent.AddTorrentParams resParams = fetchMagnet(params);
        if (resParams == null)
            return null;

        List<Priority> priorities = Arrays.asList(PriorityConverter.convert(
                resParams.swig().get_file_priorities()));

        return new MagnetInfo(uri, resParams.getInfoHashes().getBest().toHex(),
                resParams.name(), priorities);
    }

    @Override
    public MagnetInfo parseMagnet(@NonNull String uri)
    {
        com.frostwire.jlibtorrent.AddTorrentParams p = com.frostwire.jlibtorrent.AddTorrentParams.parseMagnetUri(uri);
        String sha1hash = p.getInfoHashes().getBest().toHex();
        String name = (TextUtils.isEmpty(p.name()) ? sha1hash : p.name());

        return new MagnetInfo(uri, sha1hash, name,
                Arrays.asList(PriorityConverter.convert(p.swig().get_file_priorities())));
    }

    private com.frostwire.jlibtorrent.AddTorrentParams fetchMagnet(com.frostwire.jlibtorrent.AddTorrentParams params) throws Exception
    {
        if (operationNotAllowed())
            return null;

        add_torrent_params p = params.swig();

        sha1_hash hash = p.getInfo_hashes().get_best();
        if (hash == null)
            return null;
        String strHash = hash.to_hex();
        torrent_handle th = null;
        boolean add = false;

        try {
            syncMagnet.lock();

            try {
                th = swig().find_torrent(hash);
                if (th != null && th.is_valid()) {
                    // Fetch bencode asynchronously in AlertListener
                    th.save_resume_data(torrent_handle.save_info_dict);
                } else {
                    add = true;
                }

                if (add) {
                    magnets.add(strHash);

                    if (TextUtils.isEmpty(p.getName()))
                        p.setName(strHash);
                    torrent_flags_t flags = p.getFlags();
                    flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());
                    flags = flags.or_(TorrentFlags.UPLOAD_MODE);
                    flags = flags.or_(TorrentFlags.STOP_WHEN_READY);
                    p.setFlags(flags);

                    addDefaultTrackers(p);

                    error_code ec = new error_code();
                    th = swig().add_torrent(p, ec);
                    if (!th.is_valid() || ec.failed())
                        magnets.remove(strHash);
                    th.resume();
                }

            } finally {
                syncMagnet.unlock();
            }

        } catch (Exception e) {
            if (add && th != null && th.is_valid())
                swig().remove_torrent(th);

            throw new Exception(e);
        }

        return new com.frostwire.jlibtorrent.AddTorrentParams(p);
    }

    private com.frostwire.jlibtorrent.AddTorrentParams parseMagnetUri(String uri)
    {
        error_code ec = new error_code();
        add_torrent_params p = libtorrent.parse_magnet_uri(uri, ec);

        if (ec.value() != 0)
            throw new IllegalArgumentException(ec.message());

        return new com.frostwire.jlibtorrent.AddTorrentParams(p);
    }

    @Override
    public void cancelFetchMagnet(@NonNull String infoHash)
    {
        if (operationNotAllowed() || !magnets.contains(infoHash))
            return;

        magnets.remove(infoHash);
        TorrentHandle th = find(new Sha1Hash(infoHash));
        if (th != null && th.isValid())
            remove(th, SessionHandle.DELETE_FILES);
    }

    private void mergeTorrent(String id, AddTorrentParams params, byte[] bencode) {
        if (operationNotAllowed()) {
            return;
        }

        var task = torrentTasks.get(id);
        if (task == null) {
            return;
        }

        task.setSequentialDownload(params.sequentialDownload);
        if (params.filePriorities != null) {
            task.prioritizeFiles(params.filePriorities);
        }
        task.setFirstLastPiecePriority(params.firstLastPiecePriority);

        try {
            mergeTrackersAndSeeds(id, params, bencode);
            task.saveResumeData(true);
        } catch (Exception e) {
            Log.e(TAG, "Unable to merge trackers and seeds:", e);
        }

        if (params.addPaused) {
            task.pauseManually();
        } else {
            task.resumeManually();
        }
    }

    private void mergeTrackersAndSeeds(String id, AddTorrentParams params, byte[] bencode) throws IOException {
        var th = find(new Sha1Hash(id));
        if (th != null) {
            byte[] b;
            if (bencode == null) {
                b = FileUtils.readFileToByteArray(new File(Uri.parse(params.source).getPath()));
            } else {
                b = bencode;
            }
            bdecode_node n = BDecodeNode.bdecode(b).swig();

            for (var tracker : extractTrackers(n, params.fromMagnet)) {
                th.addTracker(tracker);
            }
            for (var webSeed : extractUrlSeeds(n)) {
                th.addUrlSeed(webSeed);
            }
        }
    }

    private List<AnnounceEntry> extractTrackers(bdecode_node n, boolean fromMagnet) {
        var announceNode = n.dict_find_list_s("announce-list");
        if (!announceNode.type().equals(bdecode_node.type_t.list_t)) {
            var urls = new ArrayList<AnnounceEntry>(1);
            AnnounceEntry e = new AnnounceEntry(n.dict_find_string_value_s("announce"));
            e.failLimit((short) 0);
            if (fromMagnet) {
                e.swig().setSource((short) announce_entry.tracker_source.source_magnet_link.swigValue());
            } else {
                e.swig().setSource((short) announce_entry.tracker_source.source_torrent.swigValue());
            }
            if (!e.url().isEmpty()) {
                urls.add(e);
            }
            return urls;
        } else {
            var urls = new ArrayList<AnnounceEntry>(announceNode.list_size());
            for (var i = 0; i < announceNode.list_size(); i++) {
                var tier = announceNode.list_at(i);
                if (!tier.type().equals(bdecode_node.type_t.list_t)) {
                    continue;
                }
                for (var j = 0; j < tier.list_size(); j++) {
                    AnnounceEntry e = new AnnounceEntry(tier.list_string_value_at_s(j));
                    if (e.url().isEmpty()) {
                        continue;
                    }
                    e.tier((short) j);
                    e.failLimit((short) 0);
                    if (fromMagnet) {
                        e.swig().setSource((short) announce_entry.tracker_source.source_magnet_link.swigValue());
                    } else {
                        e.swig().setSource((short) announce_entry.tracker_source.source_torrent.swigValue());
                    }
                    urls.add(e);
                }
            }

            // Shuffle each tier
            Collections.shuffle(urls);
            urls.sort(Comparator.comparingInt(AnnounceEntry::tier));

            return urls;
        }
    }

    private List<String> extractUrlSeeds(bdecode_node n) {
        var urls = new ArrayList<String>();

        var urlSeeds = n.dict_find_list_s("url-list");
        if (urlSeeds.type().equals(bdecode_node.type_t.string_t) && urlSeeds.string_length() > 0) {
            urls.add(urlSeeds.string_value_ex());
        } else if (urlSeeds.type().equals(bdecode_node.type_t.list_t)) {
            // Only add a URL once
            var unique = new HashSet<String>();
            for (var i = 0; i < urlSeeds.list_size(); i++) {
                var url = urlSeeds.list_at(i);
                if (!url.type().equals(bdecode_node.type_t.string_t) || url.string_length() == 0) {
                    continue;
                }
                var urlStr = url.string_value_ex();
                if (!unique.add(urlStr)) {
                    continue;
                }
                urls.add(urlStr);
            }
        }

        return urls;
    }

    @Override
    public long getDownloadSpeed()
    {
        return stats().downloadRate();
    }

    @Override
    public long getUploadSpeed()
    {
        return stats().uploadRate();
    }

    @Override
    public long getTotalDownload()
    {
        return stats().totalDownload();
    }

    @Override
    public long getTotalUpload()
    {
        return stats().totalUpload();
    }

    @Override
    public int getDownloadSpeedLimit()
    {
        SettingsPack settingsPack = settings();

        return (settingsPack == null ? -1 : settingsPack.downloadRateLimit());
    }

    @Override
    public int getUploadSpeedLimit()
    {
        SettingsPack settingsPack = settings();

        return (settingsPack == null ? -1 : settingsPack.uploadRateLimit());
    }

    @Override
    public int getListenPort()
    {
        return (swig() == null ? -1 : swig().listen_port());
    }

    @Override
    public long getDhtNodes()
    {
        return stats().dhtNodes();
    }

    @Override
    public void enableIpFilter(@NonNull Uri path)
    {
        if (operationNotAllowed())
            return;

        if (parseIpFilterThread != null && !parseIpFilterThread.isInterrupted())
            parseIpFilterThread.interrupt();

        parseIpFilterThread = new Thread(() -> {
            if (operationNotAllowed() || Thread.interrupted())
                return;

            IPFilterImpl filter = new IPFilterImpl();
            int ruleCount = new IPFilterParser().parseFile(path, fs, filter);
            if (Thread.interrupted())
                return;
            if (ruleCount != 0 && swig() != null && !operationNotAllowed())
                swig().set_ip_filter(filter.getFilter());

            notifyListeners((listener) ->
                    listener.onIpFilterParsed(ruleCount));
        });
        parseIpFilterThread.start();
    }

    @Override
    public void disableIpFilter()
    {
        if (operationNotAllowed())
            return;

        if (parseIpFilterThread != null && !parseIpFilterThread.isInterrupted())
            parseIpFilterThread.interrupt();

        swig().set_ip_filter(new ip_filter());
    }

    @Override
    public void pauseAll()
    {
        if (operationNotAllowed())
            return;

        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.pause();
        }
    }

    @Override
    public void resumeAll()
    {
        if (operationNotAllowed())
            return;

        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.resume();
        }
    }

    @Override
    public void pauseAllManually()
    {
        if (operationNotAllowed())
            return;

        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.pauseManually();
        }
    }

    @Override
    public void resumeAllManually()
    {
        if (operationNotAllowed())
            return;

        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.resumeManually();
        }
    }

    @Override
    public void setMaxConnectionsPerTorrent(int connections)
    {
        if (operationNotAllowed())
            return;

        settings.connectionsLimitPerTorrent = connections;
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.setMaxConnections(connections);
        }
    }

    @Override
    public void setMaxUploadsPerTorrent(int uploads)
    {
        if (operationNotAllowed())
            return;

        settings.uploadsLimitPerTorrent = uploads;
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.setMaxUploads(uploads);
        }
    }

    @Override
    public void setAutoManaged(boolean autoManaged)
    {
        if (operationNotAllowed())
            return;

        settings.autoManaged = autoManaged;
        for (TorrentDownload task : torrentTasks.values())
            task.setAutoManaged(autoManaged);
    }

    @Override
    public boolean isDHTEnabled()
    {
        SettingsPack sp = settings();

        return sp != null && sp.isEnableDht();
    }

    @Override
    public boolean isPeXEnabled()
    {
        /* PeX enabled by default in session_handle.session_flags_t::add_default_plugins */
        return true;
    }

    @Override
    public void start()
    {
        if (isRunning())
            return;

        SessionParams params = loadSettings();
        SettingsPack settingsPack = params.getSettings();
        settings_pack sp = settingsPack.swig();

        if (settings.posixDiskIo) {
            // Internally set the session to use a simple posix disk I/O back-end, used
            // for systems that don't have a 64-bit virtual address space or don't support
            // memory mapped files. This option only to use in particular situations, like
            // Android devices with faulty drivers.
            params.setPosixDiskIO();
        } else {
            params.setDefaultDiskIO();
        }

        sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());
        sp.set_bool(settings_pack.bool_types.enable_ip_notifier.swigValue(), false);
        sp.set_int(settings_pack.int_types.alert_queue_size.swigValue(), 5000);
        sp.set_bool(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true);
        sp.set_bool(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true);

        String versionName = system.getAppVersionName();
        if (versionName != null) {
            int[] version = Utils.getVersionComponents(versionName);

            String fingerprint = libtorrent.generate_fingerprint(PEER_FINGERPRINT,
                    version[0], version[1], version[2], 0);
            sp.set_str(settings_pack.string_types.peer_fingerprint.swigValue(), fingerprint);

            String userAgent = String.format(USER_AGENT, Utils.getAppVersionNumber(versionName));
            sp.set_str(settings_pack.string_types.user_agent.swigValue(), userAgent);

            Log.i(TAG, "Peer fingerprint: " + sp.get_str(settings_pack.string_types.peer_fingerprint.swigValue()));
            Log.i(TAG, "User agent: " + sp.get_str(settings_pack.string_types.user_agent.swigValue()));
        }

        if (settings.useRandomPort) {
            setRandomPort(settings);
        }
        settingsToSettingsPack(settings, params.getSettings());

        super.start(params);
    }

    @Override
    public void requestStop()
    {
        if (stopRequested.getAndSet(true))
            return;

        saveAllResumeData();
        stopTasks();
    }

    private void stopTasks()
    {
        disposables.add(Observable.fromIterable(torrentTasks.values())
                .filter(Objects::nonNull)
                .map(TorrentDownload::requestStop)
                .toList()
                .flatMapCompletable(Completable::merge) /* Wait for all torrents */
                .subscribe(
                        this::handleStoppingTasks,
                        (err) -> {
                            Log.e(TAG, "Error stopping torrents: " +
                                    Log.getStackTraceString(err));
                            handleStoppingTasks();
                        }
                ));
    }

    private void handleStoppingTasks()
    {
        /* Handles must be destructed before the session is destructed */
        torrentTasks.clear();
        checkStop();
    }

    private void checkStop()
    {
        if (stopRequested.get() && torrentTasks.isEmpty() && addTorrentsList.isEmpty())
            super.stop();
    }

    private void saveAllResumeData()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null || task.hasMissingFiles())
                continue;
            task.saveResumeData(false);
        }
    }

    @Override
    public boolean isRunning()
    {
        return super.isRunning() && started;
    }

    @Override
    public long dhtNodes()
    {
        return super.dhtNodes();
    }

    @Override
    public int[] getPieceSizeList()
    {
        return pieceSize;
    }

    @Override
    public int[] getTorrentVersionList()
    {
        return torrentVersions;
    }

    @Override
    protected void onBeforeStart()
    {
        addListener(torrentTaskListener);
        addListener(innerListener);
    }

    @Override
    protected void onAfterStart()
    {
        /*
         * Overwrite default behaviour of super.start()
         * and enable logging in onAfterStart()
         */
        if (settings.logging) {
            SettingsPack sp = settings();
            if (sp == null)
                return;
            sp.setInteger(settings_pack.int_types.alert_mask.swigValue(), getAlertMask(settings).to_int());
            applySettingsPack(sp);

            enableSessionLogger(true);
        }

        saveSettings();
        started = true;
        disposables.add(Completable.fromRunnable(() ->
                notifyListeners(TorrentEngineListener::onSessionStarted))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    private void enableSessionLogger(boolean enable)
    {
        if (enable) {
            sessionLogger.resume();

        } else {
            sessionLogger.stopRecording();
            sessionLogger.pause();
            sessionLogger.clean();
        }
    }

    @Override
    protected void onBeforeStop()
    {
        disposables.clear();
        started = false;
        enableSessionLogger(false);
        parseIpFilterThread = null;
        magnets.clear();
        loadedMagnets.clear();
        removeListener(torrentTaskListener);
        removeListener(innerListener);
    }

    @Override
    protected void onAfterStop()
    {
        notifyListeners(TorrentEngineListener::onSessionStopped);
        stopRequested.set(false);
    }

    @Override
    protected void onApplySettings(SettingsPack sp)
    {
        saveSettings();
    }

    private final TorrentEngineListener torrentTaskListener = new TorrentEngineListener() {
        @Override
        public void onTorrentRemoved(@NonNull String id)
        {
            torrentTasks.remove(id);
        }
    };

    private final class InnerListener implements AlertListener
    {
        @Override
        public int[] types()
        {
            return INNER_LISTENER_TYPES;
        }

        @Override
        public void alert(Alert<?> alert)
        {
            switch (alert.type()) {
                case ADD_TORRENT:
                    TorrentAlert<?> torrentAlert = (TorrentAlert<?>)alert;
                    TorrentHandle th = find(torrentAlert.handle().infoHash());
                    if (th == null)
                        break;
                    String hash = th.infoHash().toHex();
                    if (magnets.contains(hash))
                        break;
                    torrentTasks.put(hash, newTask(th, hash));
                    if (addTorrentsList.contains(hash))
                        notifyListeners((listener) ->
                                listener.onTorrentAdded(hash));
                    else
                        notifyListeners((listener) ->
                                listener.onTorrentLoaded(hash));
                    addTorrentsList.remove(hash);
                    checkStop();
                    runNextLoadTorrentTask();
                    break;
                case METADATA_RECEIVED:
                    handleMetadataReceived((MetadataReceivedAlert) alert);
                    break;
                case SESSION_STATS:
                    handleStats();
                    break;
                case SAVE_RESUME_DATA:
                    handleSaveMetadata((SaveResumeDataAlert) alert);
                default:
                    checkError(alert);
                    if (settings.logging)
                        sessionLogger.send(alert);
                    break;
            }
        }
    }

    private void checkError(Alert<?> alert)
    {
        notifyListeners((listener) -> {
            String msg = null;
            switch (alert.type()) {
                case SESSION_ERROR: {
                    SessionErrorAlert sessionErrorAlert = (SessionErrorAlert)alert;
                    ErrorCode error = sessionErrorAlert.error();
                    msg = SessionErrors.getErrorMsg(error);
                    if (!SessionErrors.isNonCritical(error))
                        listener.onSessionError(msg);
                    break;
                }
                case LISTEN_FAILED: {
                    ListenFailedAlert listenFailedAlert = (ListenFailedAlert)alert;
                    msg = SessionErrors.getErrorMsg(listenFailedAlert.error());
                    ErrorCode error = listenFailedAlert.error();
                    if (!SessionErrors.isNonCritical(error))
                        listener.onSessionError(msg);
                    break;
                }
                case PORTMAP_ERROR: {
                    PortmapErrorAlert portmapErrorAlert = (PortmapErrorAlert)alert;
                    ErrorCode error = portmapErrorAlert.error();
                    msg = SessionErrors.getErrorMsg(error);
                    if (!SessionErrors.isNonCritical(error))
                        listener.onNatError(msg);
                    break;
                }
            }

            if (msg != null)
                Log.e(TAG, "Session error: " + msg);
        });
    }

    private void handleMetadataReceived(MetadataReceivedAlert alert) {
        TorrentHandle th = alert.handle();
        String hash = th.infoHash().toHex();
        if (magnets.contains(hash)) {
            th.saveResumeData(TorrentHandle.SAVE_INFO_DICT);
        }
    }

    private void handleSaveMetadata(SaveResumeDataAlert alert) {
        TorrentHandle th = alert.handle();
        String hash = th.infoHash().toHex();
        if (!magnets.contains(hash)) {
            return;
        }

        byte_vector bytes = libtorrent.write_torrent_file_buf_ex(alert.params().swig());
        loadedMagnets.put(hash, Vectors.byte_vector2bytes(bytes));
        remove(th, SessionHandle.DELETE_FILES);

        notifyListeners((listener) ->
                listener.onMagnetLoaded(hash, loadedMagnets.get(hash)));
    }

    private void handleStats()
    {
        if (operationNotAllowed())
            return;

        notifyListeners((listener) -> listener.onSessionStats(
                new SessionStats(dhtNodes(),
                        getTotalDownload(),
                        getTotalUpload(),
                        getDownloadSpeed(),
                        getUploadSpeed(),
                        getListenPort()))
        );
    }

    private static String dhtBootstrapNodes()
    {
       return "dht.libtorrent.org:25401" + "," +
                "router.bittorrent.com:6881" + "," +
                "dht.transmissionbt.com:6881" + "," +
                /* For IPv6 DHT */
                "outer.silotis.us:6881";
    }

    private SessionParams loadSettings()
    {
        try {
            String sessionPath = repo.getSessionFile();
            if (sessionPath == null)
                return new SessionParams(defaultSettingsPack());

            File sessionFile = new File(sessionPath);
            if (sessionFile.exists()) {
                byte[] data = FileUtils.readFileToByteArray(sessionFile);
                byte_vector buffer = Vectors.bytes2byte_vector(data);
                bdecode_node n = new bdecode_node();
                error_code ec = new error_code();
                int ret = bdecode_node.bdecode(buffer, n, ec);
                if (ret == 0) {
                    session_params params = session_params.read_session_params(n);
                    /* Prevents GC */
                    buffer.clear();

                    return new SessionParams(params);
                } else {
                    throw new IllegalArgumentException("Can't decode data: " + ec.message());
                }

            } else {
                return new SessionParams(defaultSettingsPack());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading session state: ");
            Log.e(TAG, Log.getStackTraceString(e));

            return new SessionParams(defaultSettingsPack());
        }
    }

    private void saveSettings()
    {
        try {
            session_params params = swig().session_state();
            entry e = session_params.write_session_params(params);
            byte[] b = Vectors.byte_vector2bytes(e.bencode());
            repo.saveSession(b);

        } catch (Exception e) {
            Log.e(TAG, "Error saving session state: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private SettingsPack defaultSettingsPack()
    {
        SettingsPack sp = new SettingsPack();
        settingsToSettingsPack(settings, sp);

        return sp;
    }

    private void settingsToSettingsPack(SessionSettings settings, SettingsPack sp)
    {
        sp.activeDownloads(settings.activeDownloads);
        sp.activeSeeds(settings.activeSeeds);
        sp.activeLimit(settings.activeLimit);
        sp.maxPeerlistSize(settings.maxPeerListSize);
        sp.tickInterval(settings.tickInterval);
        sp.inactivityTimeout(settings.inactivityTimeout);
        sp.connectionsLimit(settings.connectionsLimit);
        sp.listenInterfaces(getIface(settings.inetAddress, settings.portRangeFirst));
        sp.setInteger(settings_pack.int_types.max_retry_port_bind.swigValue(),
                settings.portRangeSecond - settings.portRangeFirst);
        sp.setEnableDht(settings.dhtEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), settings.lsdEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), settings.utpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), settings.utpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), settings.upnpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), settings.natPmpEnabled);
        var encryptModeOutcoming = convertEncryptMode(settings.encryptModeOutcoming);
        var encryptModeIncoming = convertEncryptMode(settings.encryptModeIncoming);
        var encLevel = getAllowedEncryptLevel(settings.encryptModeOutcoming, settings.encryptModeIncoming);
        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), encryptModeIncoming);
        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), encryptModeOutcoming);
        sp.setInteger(settings_pack.int_types.allowed_enc_level.swigValue(), encLevel);
        sp.uploadRateLimit(settings.uploadRateLimit);
        sp.downloadRateLimit(settings.downloadRateLimit);
        sp.anonymousMode(settings.anonymousMode);
        sp.seedingOutgoingConnections(settings.seedingOutgoingConnections);
        sp.setInteger(settings_pack.int_types.alert_mask.swigValue(), getAlertMask(settings).to_int());
        sp.setBoolean(settings_pack.bool_types.validate_https_trackers.swigValue(), settings.validateHttpsTrackers);

        applyProxy(settings, sp);
    }

    private void applyProxy(SessionSettings settings, SettingsPack sp)
    {
        int proxyType = convertProxyType(settings.proxyType, settings.proxyRequiresAuth);
        sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), proxyType);
        if (settings.proxyType != SessionSettings.ProxyType.NONE) {
            sp.setInteger(settings_pack.int_types.proxy_port.swigValue(), settings.proxyPort);
            sp.setString(settings_pack.string_types.proxy_hostname.swigValue(), settings.proxyAddress);
            if (settings.proxyRequiresAuth) {
                sp.setString(settings_pack.string_types.proxy_username.swigValue(), settings.proxyLogin);
                sp.setString(settings_pack.string_types.proxy_password.swigValue(), settings.proxyPassword);
            }
            sp.setBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue(), settings.proxyPeersToo);
            sp.setBoolean(settings_pack.bool_types.proxy_tracker_connections.swigValue(), true);
            sp.setBoolean(settings_pack.bool_types.proxy_hostnames.swigValue(), true);
        }
    }

    private alert_category_t getAlertMask(SessionSettings settings)
    {
        alert_category_t mask = alert.all_categories;
        if (!settings.logging) {
            alert_category_t log_mask = alert.session_log_notification;
            log_mask = log_mask.or_(alert.torrent_log_notification);
            log_mask = log_mask.or_(alert.peer_log_notification);
            log_mask = log_mask.or_(alert.dht_log_notification);
            log_mask = log_mask.or_(alert.port_mapping_log_notification);
            log_mask = log_mask.or_(alert.picker_log_notification);

            mask = mask.and_(log_mask.inv());
        }

        return mask;
    }

    private int convertEncryptMode(SessionSettings.EncryptMode mode)
    {
        switch (mode) {
            case ENABLED:
                return settings_pack.enc_policy.pe_enabled.swigValue();
            case FORCED:
                return settings_pack.enc_policy.pe_forced.swigValue();
            default:
                return settings_pack.enc_policy.pe_disabled.swigValue();
        }
    }

    private int getAllowedEncryptLevel(
            SessionSettings.EncryptMode modeOutcoming,
            SessionSettings.EncryptMode modeIncoming
    ) {
        if (modeOutcoming == SessionSettings.EncryptMode.FORCED
                || modeIncoming == SessionSettings.EncryptMode.FORCED) {
            return settings_pack.enc_level.pe_rc4.swigValue();
        } else {
            return settings_pack.enc_level.pe_both.swigValue();
        }
    }

    private int convertProxyType(SessionSettings.ProxyType mode, boolean authRequired)
    {
        switch (mode) {
            case SOCKS4:
                return settings_pack.proxy_type_t.socks4.swigValue();
            case SOCKS5:
                return (authRequired ?
                        settings_pack.proxy_type_t.socks5_pw.swigValue() :
                        settings_pack.proxy_type_t.socks5.swigValue());
            case HTTP:
                return (authRequired ?
                        settings_pack.proxy_type_t.http_pw.swigValue() :
                        settings_pack.proxy_type_t.http.swigValue());
            default:
                return settings_pack.proxy_type_t.none.swigValue();
        }
    }

    private String getIface(String inetAddress, int portRangeFirst)
    {
        String iface;
        if (inetAddress.equals(SessionSettings.DEFAULT_INETADDRESS)) {
            iface = "0.0.0.0:%1$d,[::]:%1$d";

        } else {
            /* IPv6 test */
            if (inetAddress.contains(":"))
                iface = "[" + inetAddress + "]";
            else
                iface = inetAddress;

            iface = iface + ":%1$d";
        }

        return String.format(iface, portRangeFirst);
    }

    private void applySettingsPack(SettingsPack sp)
    {
        applySettings(sp);
        saveSettings();
    }

    private void applySettings(SessionSettings settings, boolean keepPort)
    {
        applyMaxStoredLogs(settings);
        applySessionLoggerFilters(settings);
        enableSessionLogger(settings.logging);

        if (!keepPort && settings.useRandomPort) {
            setRandomPort(settings);
        }

        SettingsPack sp = settings();
        if (sp != null) {
            settingsToSettingsPack(settings, sp);
            applySettingsPack(sp);
        }
    }

    private void setRandomPort(SessionSettings settings) {
        Pair<Integer, Integer> range = SessionSettings.getRandomRangePort();
        settings.portRangeFirst = range.first;
        settings.portRangeSecond = range.second;
    }

    private void applyMaxStoredLogs(SessionSettings settings)
    {
        if (settings.maxLogSize == sessionLogger.getMaxStoredLogs())
            return;

        sessionLogger.setMaxStoredLogs(settings.maxLogSize);
    }

    private void applySessionLoggerFilters(SessionSettings settings)
    {
        disposables.add(Completable.fromRunnable(() ->
                sessionLogger.applyFilterParams(new SessionLogger.SessionFilterParams(
                        settings.logSessionFilter,
                        settings.logDhtFilter,
                        settings.logPeerFilter,
                        settings.logPortmapFilter,
                        settings.logTorrentFilter
        )))
        .subscribeOn(Schedulers.computation())
        .subscribe());
    }

    private TorrentDownload newTask(TorrentHandle th, String id)
    {
        TorrentDownload task = new TorrentDownloadImpl(this, repo, fs, listeners,
                id, th, settings.autoManaged);
        task.setMaxConnections(settings.connectionsLimitPerTorrent);
        task.setMaxUploads(settings.uploadsLimitPerTorrent);

        return task;
    }

    private interface CallListener
    {
        void apply(TorrentEngineListener listener);
    }

    private void notifyListeners(@NonNull CallListener l)
    {
        for (TorrentEngineListener listener : listeners) {
            if (listener != null)
                l.apply(listener);
        }
    }

    private void runNextLoadTorrentTask()
    {
        if (operationNotAllowed()) {
            restoreTorrentsQueue.clear();

            return;
        }

        LoadTorrentTask task;
        try {
            task = restoreTorrentsQueue.poll();

        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return;
        }

        if (task != null)
            loadTorrentsExec.execute(task);
    }

    private boolean isTorrentAlreadyRunning(String torrentId)
    {
        return torrentTasks.containsKey(torrentId) || addTorrentsList.contains(torrentId);
    }

    private final class LoadTorrentTask implements Runnable
    {
        private String torrentId;
        private File saveDir = null;
        private String magnetUri = null;
        private boolean isMagnet = false;
        private boolean magnetPaused = false;
        private boolean magnetSequentialDownload = false;

        LoadTorrentTask(String torrentId)
        {
            this.torrentId = torrentId;
        }

        public void putMagnet(
                String magnetUri,
                File saveDir,
                boolean magnetPaused,
                boolean magnetSequentialDownload
        ) {
            this.magnetUri = magnetUri;
            this.saveDir = saveDir;
            this.magnetPaused = magnetPaused;
            this.magnetSequentialDownload = magnetSequentialDownload;
            isMagnet = true;
        }

        @Override
        public void run()
        {
            try {
                if (isTorrentAlreadyRunning(torrentId))
                    return;

                if (isMagnet)
                    download(magnetUri, saveDir, magnetPaused, magnetSequentialDownload);
                else
                    restoreDownload(torrentId);

            } catch (Exception e) {
                Log.e(TAG, "Unable to restore torrent from previous session: " + torrentId, e);
                Torrent torrent = repo.getTorrentById(torrentId);
                if (torrent != null) {
                    torrent.error = e.toString();
                    repo.updateTorrent(torrent);
                }

                notifyListeners((listener) ->
                        listener.onRestoreSessionError(torrentId));
            }
        }
    }

    private void download(byte[] bencode, File saveDir,
                          Priority[] priorities, boolean sequentialDownload,
                          boolean paused, List<TcpEndpoint> peers)
    {
        download((bencode == null ? null : new TorrentInfo(bencode)),
                saveDir, priorities, sequentialDownload, paused, peers);
    }

    private void download(TorrentInfo ti, File saveDir,
                          Priority[] priorities, boolean sequentialDownload,
                          boolean paused, List<TcpEndpoint> peers)
    {
        if (operationNotAllowed())
            return;

        if (ti == null)
            throw new IllegalArgumentException("Torrent info is null");
        if (!ti.isValid())
            throw new IllegalArgumentException("Torrent info not valid");

        torrent_handle th = swig().find_torrent(ti.swig().info_hash());
        if (th != null && th.is_valid()) {
            /* Found a download with the same hash */
            return;
        }

        var p = new com.frostwire.jlibtorrent.AddTorrentParams();

        p.torrentInfo(ti);
        if (saveDir != null)
            p.savePath(saveDir.getAbsolutePath());

        if (priorities != null) {
            if (ti.files().numFiles() != priorities.length)
                throw new IllegalArgumentException("Priorities count should be equals to the number of files");

            var list = new com.frostwire.jlibtorrent.Priority[priorities.length];
            for (int i = 0; i < priorities.length; i++) {
                if (priorities[i] == null) {
                    list[i] = (com.frostwire.jlibtorrent.Priority.IGNORE);
                } else {
                    list[i] = (PriorityConverter.convert(priorities[i]));
                }
            }
            p.filePriorities(list);
        }

        if (peers != null && !peers.isEmpty()) {
            p.peers(peers);
        }

        torrent_flags_t flags = p.flags();
        /* Force saving resume data */
        flags = flags.or_(TorrentFlags.NEED_SAVE_RESUME);

        if (settings.autoManaged)
            flags = flags.or_(TorrentFlags.AUTO_MANAGED);
        else
            flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());

        if (sequentialDownload)
            flags = flags.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD);
        else
            flags = flags.and_(TorrentFlags.SEQUENTIAL_DOWNLOAD.inv());

        if (paused)
            flags = flags.or_(TorrentFlags.PAUSED);
        else
            flags = flags.and_(TorrentFlags.PAUSED.inv());

        p.flags(flags);

        addDefaultTrackers(p.swig());

        swig().async_add_torrent(p.swig());
    }

    @Override
    public void download(
            @NonNull String magnetUri,
            File saveDir,
            boolean paused,
            boolean sequentialDownload
    ) {
        if (operationNotAllowed())
            return;

        error_code ec = new error_code();
        add_torrent_params p = libtorrent.parse_magnet_uri(magnetUri, ec);

        if (ec.value() != 0)
            throw new IllegalArgumentException(ec.message());

        sha1_hash info_hash = p.getInfo_hashes().get_best();
        if (info_hash == null)
            return;
        torrent_handle th = swig().find_torrent(info_hash);
        if (th != null && th.is_valid()) {
            /* Found a download with the same hash */
            return;
        }

        if (saveDir != null)
            p.setSave_path(saveDir.getAbsolutePath());

        if (TextUtils.isEmpty(p.getName()))
            p.setName(info_hash.to_hex());

        torrent_flags_t flags = p.getFlags();

        flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());
        if (paused)
            flags = flags.or_(TorrentFlags.PAUSED);
        else
            flags = flags.and_(TorrentFlags.PAUSED.inv());
        if (sequentialDownload)
            flags = flags.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD);
        else
            flags = flags.and_(TorrentFlags.SEQUENTIAL_DOWNLOAD.inv());

        p.setFlags(flags);

        addDefaultTrackers(p);

        swig().async_add_torrent(p);
    }

    private void addDefaultTrackers(add_torrent_params p) {
        String[] defaultTrackers = getSettings().defaultTrackersList;
        if (defaultTrackers != null && defaultTrackers.length > 0) {
            string_vector v = p.get_trackers();
            if (v == null) {
                v = new string_vector();
            }
            v.addAll(Arrays.asList(defaultTrackers));
            p.set_trackers(v);
        }
    }

    @Override
    public void setDefaultTrackersList(@NonNull String[] trackersList) {
        settings.defaultTrackersList = trackersList;
    }

    private void restoreDownload(String id) throws IOException
    {
        if (operationNotAllowed())
            return;

        FastResume fastResume = repo.getFastResumeById(id);
        if (fastResume == null)
            throw new IOException("Fast resume data not found");

        error_code ec = new error_code();
        byte_vector buffer = Vectors.bytes2byte_vector(fastResume.data);

        bdecode_node n = new bdecode_node();
        int ret = bdecode_node.bdecode(buffer, n, ec);
        if (ret != 0)
            throw new IllegalArgumentException("Can't decode data: " + ec.message());
        ec.clear();

        add_torrent_params p = libtorrent.read_resume_data(n, ec);
        if (ec.value() != 0)
            throw new IllegalArgumentException("Unable to read the resume data: " + ec.message());

        torrent_flags_t flags = p.getFlags();
        /* Disable force saving resume data, because they already have */
        flags = flags.and_(TorrentFlags.NEED_SAVE_RESUME.inv());

        if (settings.autoManaged)
            flags = flags.or_(TorrentFlags.AUTO_MANAGED);
        else
            flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());

        p.setFlags(flags);

        /* After reading the metadata some time may have passed */
        if (operationNotAllowed())
            return;

        swig().async_add_torrent(p);
    }
}
