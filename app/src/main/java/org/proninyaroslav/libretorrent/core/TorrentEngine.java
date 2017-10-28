/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.alerts.TorrentRemovedAlert;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.bdecode_node;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.session_params;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_handle;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/*
 * This class is designed for seeding, downloading and management of torrents.
 */

public class TorrentEngine extends SessionManager
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentEngine.class.getSimpleName();

    public static final int DEFAULT_CACHE_SIZE = 256;
    public static final int DEFAULT_ACTIVE_DOWNLOADS = 4;
    public static final int DEFAULT_ACTIVE_SEEDS = 4;
    public static final int DEFAULT_MAX_PEER_LIST_SIZE = 200;
    public static final int DEFAULT_TICK_INTERVAL = 1000;
    public static final int DEFAULT_INACTIVITY_TIMEOUT = 60;
    public static final int MIN_CONNECTIONS_LIMIT = 2;
    public static final int DEFAULT_CONNECTIONS_LIMIT = 200;
    public static final int DEFAULT_ACTIVE_LIMIT = 6;
    public static final int DEFAULT_PORT = 6881;
    public static final int DEFAULT_PROXY_PORT = 8080;
    public static final int MAX_PORT_NUMBER = 65534;
    public static final int MIN_PORT_NUMBER = 49160;
    public static final boolean DEFAULT_DHT_ENABLED = true;
    public static final boolean DEFAULT_LSD_ENABLED = true;
    public static final boolean DEFAULT_UTP_ENABLED = true;
    public static final boolean DEFAULT_UPNP_ENABLED = true;
    public static final boolean DEFAULT_NATPMP_ENABLED = true;
    public static final boolean DEFAULT_ENCRYPT_IN_CONNECTIONS = true;
    public static final boolean DEFAULT_ENCRYPT_OUT_CONNECTIONS = true;

    private static final int[] INNER_LISTENER_TYPES = new int[] {
            AlertType.ADD_TORRENT.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.TORRENT_REMOVED.swig()
    };

    private Context context;
    private InnerListener innerListener;
    private TorrentEngineCallback callback;
    private Queue<LoadTorrentTask> loadTorrentsQueue = new LinkedList<>();
    private ExecutorService loadTorrentsExec;
    /* Tasks list */
    private ConcurrentHashMap<String, TorrentDownload> torrentTasks = new ConcurrentHashMap<>();
    /* Fetch magnets (non added magnets) */
    private CopyOnWriteArrayList<String> magnetList = new CopyOnWriteArrayList<>();
    private Map<String, Torrent> addTorrentsQueue = new HashMap<>();
    private ReentrantLock syncMagnet = new ReentrantLock();

    private TorrentEngine()
    {
        innerListener = new InnerListener();
        loadTorrentsExec = Executors.newCachedThreadPool();
    }

    public static TorrentEngine getInstance()
    {
        return Loader.INSTANCE;
    }

    private static class Loader
    {
        static final TorrentEngine INSTANCE = new TorrentEngine();
    }

    public void setContext(Context context)
    {
        this.context = context;
    }

    public void setCallback(TorrentEngineCallback callback)
    {
        this.callback = callback;
    }

    public TorrentDownload getTask(String id)
    {
        return torrentTasks.get(id);
    }

    public Collection<TorrentDownload> getTasks()
    {
        return torrentTasks.values();
    }

    public boolean hasTasks()
    {
        return torrentTasks.isEmpty();
    }

    public int tasksCount()
    {
        return torrentTasks.size();
    }

    public boolean isMagnet(String id)
    {
        return magnetList.contains(id);
    }

    @Override
    public void start()
    {
        settings_pack sp = new settings_pack();
        sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());

        super.start(loadSettings());
    }

    @Override
    public void stop()
    {
        /* Handles must be destructed before the session is destructed */
        torrentTasks.clear();
        magnetList.clear();

        super.stop();
    }

    private static String dhtBootstrapNodes()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("dht.libtorrent.org:25401").append(",");
        sb.append("router.bittorrent.com:6881").append(",");
        sb.append("dht.transmissionbt.com:6881").append(",");
        /* For IPv6 DHT */
        sb.append("outer.silotis.us:6881");

        return sb.toString();
    }

    @Override
    protected void onBeforeStart()
    {
        addListener(innerListener);
    }

    @Override
    protected void onAfterStart()
    {
        if (callback != null) {
            callback.onEngineStarted();
        }
    }

    @Override
    protected void onBeforeStop()
    {
        removeListener(innerListener);
        saveSettings();
    }

    @Override
    protected void onApplySettings(SettingsPack sp)
    {
        saveSettings();
    }

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
                    TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;
                    Sha1Hash sha1hash = torrentAlert.handle().infoHash();
                    Torrent torrent = addTorrentsQueue.get(sha1hash.toString());
                    if (torrent != null) {
                        TorrentHandle handle = find(sha1hash);
                        if (torrent.isSequentialDownload())
                            handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
                        else
                            handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);

                        if (torrent.isPaused())
                            handle.pause();
                        else
                            handle.resume();
                        torrentTasks.put(torrent.getId(), new TorrentDownload(context, handle, torrent, callback));

                        if (callback != null)
                            callback.onTorrentAdded(torrent.getId());
                    }
                    runNextLoadTorrentTask();
                    break;
                case METADATA_RECEIVED:
                    MetadataReceivedAlert metadataAlert = ((MetadataReceivedAlert) alert);
                    int size = metadataAlert.metadataSize();
                    int maxSize = 2 * 1024 * 1024;
                    byte[] data = null;
                    String hash = metadataAlert.handle().infoHash().toHex();
                    Exception err = null;

                    if (0 < size && size <= maxSize)
                        data = metadataAlert.torrentData(true);

                    String pathToTorrent = null;
                    try {
                        pathToTorrent = handleMetadata(data, hash);

                    } catch (Exception e) {
                        err = e;
                        TorrentDownload task = torrentTasks.get(hash);
                        if (task != null) {
                            task.remove(false);
                        }
                        magnetList.remove(hash);
                    }

                    if (callback != null)
                        callback.onMetadataReceived(hash, pathToTorrent, err);
                    break;
                case TORRENT_REMOVED:
                    torrentTasks.remove(((TorrentRemovedAlert) alert).infoHash().toHex());
                    break;
            }
        }
    }

    private String handleMetadata(byte[] data, String hash) throws Exception
    {
        File torrentFile;
        String pathToTorrent;
        File dir;
        String name;
        boolean nonAddedMagnet = isMagnet(hash);

        if (nonAddedMagnet) {
            dir = FileIOUtils.getTempDir(context);
            name = hash;
        } else {
            String pathToDir = TorrentUtils.findTorrentDataDir(context, hash);
            if (pathToDir == null)
                throw new FileNotFoundException("Data dir not found");
            dir = new File(pathToDir);
            name = TorrentStorage.Model.DATA_TORRENT_FILE_NAME;
        }

        torrentFile = TorrentUtils.createTorrentFile(name, data, dir);
        if (torrentFile != null && torrentFile.exists())
            pathToTorrent = torrentFile.getAbsolutePath();
        else
            throw new FetchLinkException("Metadata is null");

        TorrentDownload task = TorrentEngine.getInstance().getTask(hash);
        TorrentMetaInfo info = new TorrentMetaInfo(pathToTorrent);
        ArrayList<Integer> priorities = new ArrayList<>(
                Collections.nCopies(info.fileList.size(), Priority.NORMAL.swig()));

        if (task != null) {
            Torrent torrent = task.getTorrent();
            if (!nonAddedMagnet) {
                long freeSpace = FileIOUtils.getFreeSpace(torrent.getDownloadPath());
                if (freeSpace < info.torrentSize)
                    throw new FreeSpaceException("Not enough free space: "
                            + freeSpace + " free, but torrent size is " + info.torrentSize);

                torrent.setTorrentFilePath(pathToTorrent);
                torrent.setFilePriorities(priorities);
                torrent.setDownloadingMetadata(false);
                task.setSequentialDownload(torrent.isSequentialDownload());
                if (torrent.isPaused())
                    task.pause();
                else
                    task.resume(true);
                task.setDownloadPath(torrent.getDownloadPath());
            }
        }

        return pathToTorrent;
    }

    public void restoreDownloads(Collection<Torrent> torrents)
    {
        if (torrents == null) {
            return;
        }

        for (Torrent torrent : torrents) {
            if (torrent == null) {
                continue;
            }

            LoadTorrentTask loadTask = new LoadTorrentTask(torrent.getId());

            if (torrent.isDownloadingMetadata()) {
                loadTask.putMagnet(torrent.getTorrentFilePath(), new File(torrent.getTorrentFilePath()));

            } else {
                TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));

                Priority[] priorities = new Priority[ti.numFiles()];
                List<Integer> torrentPriorities = torrent.getFilePriorities();

                if (torrentPriorities.size() != ti.numFiles()) {
                    if (callback != null) {
                        callback.onRestoreSessionError(torrent.getId());
                    }

                    continue;
                }

                for (int i = 0; i < torrentPriorities.size(); i++) {
                    priorities[i] = Priority.fromSwig(torrentPriorities.get(i));
                }

                File saveDir = new File(torrent.getDownloadPath());

                String dataDir = TorrentUtils.findTorrentDataDir(context, torrent.getId());
                File resumeFile = null;

                if (dataDir != null) {
                    File file = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME);
                    if (file.exists()) {
                        resumeFile = file;
                    }
                }

                loadTask.putTorrentFile(new File(torrent.getTorrentFilePath()),
                        saveDir, resumeFile, priorities);
            }

            addTorrentsQueue.put(torrent.getId(), torrent);
            loadTorrentsQueue.add(loadTask);
        }

        runNextLoadTorrentTask();
    }

    private void runNextLoadTorrentTask() {
        LoadTorrentTask task = null;

        try {
            if (!loadTorrentsQueue.isEmpty()) {
                task = loadTorrentsQueue.poll();
            }
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return;
        }

        if (task != null) {
            loadTorrentsExec.execute(task);
        }
    }

    private void prioritizeFiles(TorrentHandle handle, Priority[] priorities)
    {
        if (handle == null) {
            return;
        }

        if (priorities != null) {
            /* Priorities for all files, priorities list for some selected files not supported */
            if (handle.torrentFile().numFiles() != priorities.length) {
                return;
            }

            handle.prioritizeFiles(priorities);

        } else {
            /* Did they just add the entire torrent (therefore not selecting any priorities) */
            final Priority[] wholeTorrentPriorities =
                    Priority.array(Priority.NORMAL, handle.torrentFile().numFiles());

            handle.prioritizeFiles(wholeTorrentPriorities);
        }
    }

    public void download(Torrent torrent)
    {
        if (magnetList.contains(torrent.getId())) {
            TorrentDownload task = torrentTasks.get(torrent.getId());
            magnetList.remove(torrent.getId());
            task.setTorrent(torrent);

            if (!torrent.isDownloadingMetadata()) {
                task.setSequentialDownload(torrent.isSequentialDownload());
                if (torrent.isPaused())
                    task.pause();
                else
                    task.resume(true);
                task.setDownloadPath(torrent.getDownloadPath());
            }

            return;
        }

        if (torrentTasks.containsKey(torrent.getId())) {
            TorrentDownload task = torrentTasks.get(torrent.getId());
            if (task != null)
                task.remove(false);
            torrentTasks.remove(torrent.getId());
        }

        TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));
        Priority[] priorities = new Priority[ti.numFiles()];
        List<Integer> torrentPriorities = torrent.getFilePriorities();

        if (torrentPriorities.size() != ti.numFiles()) {
            Log.e(TAG, "File count doesn't match: " + torrent.getName());

            return;
        }

        for (int i = 0; i < torrentPriorities.size(); i++)
            priorities[i] = Priority.fromSwig(torrentPriorities.get(i));

        TorrentHandle handle = find(ti.infoHash());
        if (handle == null) {
            File saveDir = new File(torrent.getDownloadPath());
            String dataDir = TorrentUtils.findTorrentDataDir(context, torrent.getId());
            File resumeFile = null;

            if (dataDir != null) {
                File file = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME);
                if (file.exists())
                    resumeFile = file;
            }

            /* New download */
            addTorrentsQueue.put(ti.infoHash().toString(), torrent);
            download(ti, saveDir, resumeFile, priorities, null);

        } else {
            /* Found a download with the same hash, just adjust the priorities if needed */
            prioritizeFiles(handle, priorities);
        }
    }

    public void saveSettings()
    {
        if (swig() == null) {
            return;
        }

        try {
            TorrentUtils.saveSession(context, saveState());

        } catch (Exception e) {
            Log.e(TAG, "Error saving session state: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public SessionParams loadSettings()
    {
        try {
            String sessionPath = TorrentUtils.findSessionFile(context);

            if (sessionPath == null) {
                return new SessionParams(defaultSettings());
            }

            File sessionFile = new File(sessionPath);

            if (sessionFile.exists()) {
                byte[] data = FileUtils.readFileToByteArray(sessionFile);
                byte_vector buffer = Vectors.bytes2byte_vector(data);
                bdecode_node n = new bdecode_node();
                error_code ec = new error_code();

                int ret = bdecode_node.bdecode(buffer, n, ec);
                if (ret == 0) {
                    session_params params = libtorrent.read_session_params(n);
                    /* Prevents GC */
                    buffer.clear();

                    return new SessionParams(params);
                } else {
                    throw new IllegalArgumentException("Can't decode data: " + ec.message());
                }

            } else {
                return new SessionParams(defaultSettings());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading session state: ");
            Log.e(TAG, Log.getStackTraceString(e));

            return new SessionParams(defaultSettings());
        }
    }

    private static SettingsPack defaultSettings()
    {
        SettingsPack sp = new SettingsPack();

        sp.broadcastLSD(true);

        int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
        sp.maxQueuedDiskBytes(maxQueuedDiskBytes / 2);
        int sendBufferWatermark = sp.sendBufferWatermark();
        sp.sendBufferWatermark(sendBufferWatermark / 2);
        sp.cacheSize(DEFAULT_CACHE_SIZE);
        sp.activeDownloads(DEFAULT_ACTIVE_DOWNLOADS);
        sp.activeSeeds(DEFAULT_ACTIVE_SEEDS);
        sp.activeLimit(DEFAULT_ACTIVE_LIMIT);
        sp.maxPeerlistSize(DEFAULT_MAX_PEER_LIST_SIZE);
        // sp.setGuidedReadCache(true);
        sp.tickInterval(DEFAULT_TICK_INTERVAL);
        sp.inactivityTimeout(DEFAULT_INACTIVITY_TIMEOUT);
        sp.seedingOutgoingConnections(false);
        sp.connectionsLimit(DEFAULT_CONNECTIONS_LIMIT);

        return sp;
    }

    public void setSettings(SettingsPack sp)
    {
        if (sp == null || swig() == null) {
            return;
        }

        applySettings(sp);
        saveSettings();
    }

    public SettingsPack getSettings()
    {
        if (swig() == null) {
            return null;
        }

        return settings();
    }

    public void setDownloadSpeedLimit(int limit)
    {
        if (swig() == null) {
            return;
        }

        SettingsPack settingsPack = settings();
        settingsPack.downloadRateLimit(limit);
        setSettings(settingsPack);
    }

    public int getDownloadSpeedLimit()
    {
        if (swig() == null) {
            return 0;
        }

        return settings().downloadRateLimit();
    }

    public void setUploadSpeedLimit(int limit)
    {
        if (swig() == null) {
            return;
        }

        SettingsPack settingsPack = settings();
        settingsPack.uploadRateLimit(limit);
        applySettings(settingsPack);
        setSettings(settingsPack);
    }

    public int getUploadSpeedLimit()
    {
        if (swig() == null) {
            return 0;
        }

        return settings().uploadRateLimit();
    }

    public long getDownloadRate()
    {
        if (swig() == null) {
            return 0;
        }

        return stats().downloadRate();
    }

    public long getUploadRate()
    {
        if (swig() == null) {
            return 0;
        }

        return stats().uploadRate();
    }

    public long getTotalDownload()
    {
        if (swig() == null) {
            return 0;
        }

        return stats().totalDownload();
    }

    public long getTotalUpload()
    {
        if (swig() == null) {
            return 0;
        }

        return stats().totalUpload();
    }

    public int getDownloadRateLimit()
    {
        if (swig() == null) {
            return 0;
        }

        return settings().downloadRateLimit();
    }

    public int getUploadRateLimit()
    {
        if (swig() == null) {
            return 0;
        }

        return settings().uploadRateLimit();
    }

    public int getPort()
    {
        if (swig() == null) {
            return -1;
        }

        return swig().listen_port();
    }

    public void setPort(int port)
    {
        if (swig() == null || port == -1) {
            return;
        }

        SettingsPack sp = settings();
        sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:" + port);
        setSettings(sp);
    }

    public void setRandomPort()
    {
        int randomPort = MIN_PORT_NUMBER + (int)(Math.random()
                * ((MAX_PORT_NUMBER - MIN_PORT_NUMBER) + 1));
        setPort(randomPort);
    }

    public void enableIpFilter(String path)
    {
        if (path == null) {
            return;
        }

        IPFilterParser parser = new IPFilterParser(path);
        parser.setOnParsedListener(new IPFilterParser.OnParsedListener()
        {
            @Override
            public void onParsed(ip_filter filter, boolean success)
            {
                if (success && swig() != null) {
                    swig().set_ip_filter(filter);
                }

                if (callback != null) {
                    callback.onIpFilterParsed(success);
                }
            }
        });
        parser.parse();
    }

    public void disableIpFilter()
    {
        if (swig() == null) {
            return;
        }

        swig().set_ip_filter(new ip_filter());
    }

    public void setProxy(Context context, ProxySettingsPack proxy)
    {
        if (context == null || proxy == null || swig() == null) {
            return;
        }

        SettingsPack sp = settings();

        settings_pack.proxy_type_t type = settings_pack.proxy_type_t.none;
        switch (proxy.getType()) {
            case SOCKS4:
                type = settings_pack.proxy_type_t.socks4;
                break;
            case SOCKS5:
                type = (TextUtils.isEmpty(proxy.getAddress()) ? settings_pack.proxy_type_t.socks5 :
                        settings_pack.proxy_type_t.socks5_pw);
                break;
            case HTTP:
                type = (TextUtils.isEmpty(proxy.getAddress()) ? settings_pack.proxy_type_t.http :
                        settings_pack.proxy_type_t.http_pw);
                break;
        }

        sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), type.swigValue());
        sp.setInteger(settings_pack.int_types.proxy_port.swigValue(), proxy.getPort());

        sp.setString(settings_pack.string_types.proxy_hostname.swigValue(), proxy.getAddress());
        sp.setString(settings_pack.string_types.proxy_username.swigValue(), proxy.getLogin());
        sp.setString(settings_pack.string_types.proxy_password.swigValue(), proxy.getPassword());

        sp.setBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue(), proxy.isProxyPeersToo());

        if (proxy.getType() != ProxySettingsPack.ProxyType.NONE) {
            sp.setBoolean(settings_pack.bool_types.force_proxy.swigValue(), proxy.isForceProxy());
        } else {
            sp.setBoolean(settings_pack.bool_types.force_proxy.swigValue(), false);
        }

        setSettings(sp);
    }

    public ProxySettingsPack getProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();
        SettingsPack sp = settings();

        ProxySettingsPack.ProxyType type;
        String swigType = sp.getString(settings_pack.int_types.proxy_type.swigValue());

        type = ProxySettingsPack.ProxyType.NONE;

        if (swigType.equals(settings_pack.proxy_type_t.socks4.toString())) {
            type = ProxySettingsPack.ProxyType.SOCKS4;
        } else if (swigType.equals(settings_pack.proxy_type_t.socks5.toString())) {
            type = ProxySettingsPack.ProxyType.SOCKS5;
        } else if (swigType.equals(settings_pack.proxy_type_t.http.toString())) {
            type = ProxySettingsPack.ProxyType.HTTP;
        }

        proxy.setType(type);
        proxy.setPort(sp.getInteger(settings_pack.int_types.proxy_port.swigValue()));
        proxy.setAddress(sp.getString(settings_pack.string_types.proxy_hostname.swigValue()));
        proxy.setLogin(sp.getString(settings_pack.string_types.proxy_username.swigValue()));
        proxy.setPassword(sp.getString(settings_pack.string_types.proxy_password.swigValue()));
        proxy.setProxyPeersToo(sp.getBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue()));
        proxy.setForceProxy(sp.getBoolean(settings_pack.bool_types.force_proxy.swigValue()));

        return proxy;
    }

    public void disableProxy(Context context)
    {
        setProxy(context, new ProxySettingsPack());
    }

    public boolean isStarted()
    {
        return swig() != null;
    }

    @Override
    public boolean isPaused()
    {
        return swig() != null && super.isPaused();
    }

    public boolean isListening()
    {
        return swig() != null && swig().is_listening();
    }

    public boolean isDHTEnabled()
    {
        return swig() != null && settings().enableDht();
    }

    public boolean isPeXEnabled()
    {
        /* PeX enabled by default in session_handle.session_flags_t::add_default_plugins */
        return true;
    }

    /*
     * Returns sha1hash.
     */

    public String fetchMagnet(String uri) throws Exception
    {
        if (uri == null)
            throw new IllegalArgumentException("Magnet link is null");

        error_code ec = new error_code();
        add_torrent_params p = add_torrent_params.parse_magnet_uri(uri, ec);

        if (ec.value() != 0)
            throw new IllegalArgumentException(ec.message());

        sha1_hash hash = p.getInfo_hash();
        torrent_handle th = null;
        boolean add = false;

        try {
            syncMagnet.lock();

            try {
                th = swig().find_torrent(hash);
                if (th != null && th.is_valid()) {
                    add = false;
                    if (callback != null)
                        callback.onMetadataExist(hash.to_hex());

                } else {
                    add = true;
                }

                if (add) {
                    p.setName(uri);
                    p.setSave_path(uri);

                    torrent_flags_t flags = p.getFlags();
                    flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());
                    flags = flags.or_(TorrentFlags.UPLOAD_MODE);
                    flags = flags.or_(TorrentFlags.STOP_WHEN_READY);
                    p.setFlags(flags);

                    ec.clear();
                    th = swig().add_torrent(p, ec);
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

        if (th.is_valid() && add) {
            String infoHash = th.info_hash().to_hex();
            Torrent torrent = new Torrent(
                    infoHash,
                    uri,
                    infoHash,
                    new ArrayList<Integer>(),
                    TorrentUtils.getTorrentDownloadPath(context),
                    System.currentTimeMillis());
            torrent.setDownloadingMetadata(true);

            magnetList.add(infoHash);
            torrentTasks.put(infoHash, new TorrentDownload(context, new TorrentHandle(th), torrent, callback));

            return infoHash;
        }

        return null;
    }

    private void restoreMagnet(String uri, String downloadPath)
    {
        if (uri == null)
            return;

        error_code ec = new error_code();
        add_torrent_params p = add_torrent_params.parse_magnet_uri(uri, ec);

        if (ec.value() != 0)
            return;

        syncMagnet.lock();

        try {
            p.setName(uri);
            p.setSave_path(downloadPath != null ? downloadPath : uri);

            torrent_flags_t flags = p.getFlags();
            flags = flags.and_(TorrentFlags.AUTO_MANAGED.inv());
            flags = flags.or_(TorrentFlags.UPLOAD_MODE);
            flags = flags.or_(TorrentFlags.STOP_WHEN_READY);
            p.setFlags(flags);

            swig().async_add_torrent(p);

        } finally {
            syncMagnet.unlock();
        }
    }

    public void removeMagnet(String infoHash)
    {
        if (infoHash == null || !magnetList.contains(infoHash))
            return;

        magnetList.remove(infoHash);
        torrentTasks.remove(infoHash);

        TorrentHandle th = find(new Sha1Hash(infoHash));
        if (th != null && th.isValid())
            remove(th);
    }

    public boolean isLSDEnabled()
    {
        return swig() != null && settings().broadcastLSD();
    }

    public void pauseAll()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.pause();
        }
    }

    public void resumeAll()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.resume(false);
        }
    }

    private final class LoadTorrentTask implements Runnable
    {
        private String torrentId;
        private File torrentFile = null;
        private File saveDir = null;
        private File resume = null;
        private Priority[] priorities = null;
        private String uri = null;
        private boolean isMagnet;

        LoadTorrentTask(String torrentId)
        {
            this.torrentId = torrentId;
        }

        public void putTorrentFile(File torrentFile, File saveDir, File resume, Priority[] priorities)
        {
            this.torrentFile = torrentFile;
            this.saveDir = saveDir;
            this.resume = resume;
            this.priorities = priorities;
            isMagnet = false;
        }

        public void putMagnet(String uri, File saveDir)
        {
            this.uri = uri;
            this.saveDir = saveDir;
            isMagnet = true;
        }

        @Override
        public void run()
        {
            try {
                if (isMagnet) {
                    restoreMagnet(uri, saveDir.getAbsolutePath());

                } else {
                    download(new TorrentInfo(torrentFile), saveDir, resume, priorities, null);
                }

            } catch (Exception e) {
                Log.e(TAG, "Unable to restore torrent from previous session: " + torrentId, e);
                if (callback != null) {
                    callback.onRestoreSessionError(torrentId);
                }
            }
        }
    }
}
