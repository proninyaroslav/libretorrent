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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionHandle;
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
import com.frostwire.jlibtorrent.swig.torrent_info;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
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
    /* Wait list for non added magnets */
    private ArrayList<String> magnets = new ArrayList<>();
    private ConcurrentHashMap<String, byte[]> loadedMagnets = new ConcurrentHashMap<>();
    private Map<String, Torrent> addTorrentsQueue = new HashMap<>();
    private ReentrantLock syncMagnet = new ReentrantLock();
    private Settings settings = new Settings();

    /* Base unit in KiB. Used for create torrent */
    public static final int[] pieceSize = {0, 16, 32, 64, 128, 256, 512,
            1024, 2048, 4096, 8192, 16384, 32768};

    public static class Settings
    {
        public static final int DEFAULT_CACHE_SIZE = 256;
        public static final int DEFAULT_ACTIVE_DOWNLOADS = 4;
        public static final int DEFAULT_ACTIVE_SEEDS = 4;
        public static final int DEFAULT_MAX_PEER_LIST_SIZE = 200;
        public static final int DEFAULT_TICK_INTERVAL = 1000;
        public static final int DEFAULT_INACTIVITY_TIMEOUT = 60;
        public static final int MIN_CONNECTIONS_LIMIT = 2;
        public static final int DEFAULT_CONNECTIONS_LIMIT = 200;
        public static final int DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT = 40;
        public static final int DEFAULT_UPLOADS_LIMIT_PER_TORRENT = 4;
        public static final int DEFAULT_ACTIVE_LIMIT = 6;
        public static final int DEFAULT_PORT = 6881;
        public static final int MAX_PORT_NUMBER = 65535;
        public static final int MIN_PORT_NUMBER = 49160;
        public static final int DEFAULT_DOWNLOAD_RATE_LIMIT = 0;
        public static final int DEFAULT_UPLOAD_RATE_LIMIT = 0;
        public static final boolean DEFAULT_DHT_ENABLED = true;
        public static final boolean DEFAULT_LSD_ENABLED = true;
        public static final boolean DEFAULT_UTP_ENABLED = true;
        public static final boolean DEFAULT_UPNP_ENABLED = true;
        public static final boolean DEFAULT_NATPMP_ENABLED = true;
        public static final boolean DEFAULT_ENCRYPT_IN_CONNECTIONS = true;
        public static final boolean DEFAULT_ENCRYPT_OUT_CONNECTIONS = true;
        public static final int DEFAULT_ENCRYPT_MODE = settings_pack.enc_policy.pe_enabled.swigValue();
        public static final boolean DEFAULT_AUTO_MANAGED = false;

        public int cacheSize = DEFAULT_CACHE_SIZE;
        public int activeDownloads = DEFAULT_ACTIVE_DOWNLOADS;
        public int activeSeeds = DEFAULT_ACTIVE_SEEDS;
        public int maxPeerListSize = DEFAULT_MAX_PEER_LIST_SIZE;
        public int tickInterval = DEFAULT_TICK_INTERVAL;
        public int inactivityTimeout = DEFAULT_INACTIVITY_TIMEOUT;
        public int connectionsLimit = DEFAULT_CONNECTIONS_LIMIT;
        public int connectionsLimitPerTorrent = DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT;
        public int uploadsLimitPerTorrent = DEFAULT_UPLOADS_LIMIT_PER_TORRENT;
        public int activeLimit = DEFAULT_ACTIVE_LIMIT;
        public int port = DEFAULT_PORT;
        public int downloadRateLimit = DEFAULT_DOWNLOAD_RATE_LIMIT;
        public int uploadRateLimit = DEFAULT_UPLOAD_RATE_LIMIT;
        public boolean dhtEnabled = DEFAULT_DHT_ENABLED;
        public boolean lsdEnabled = DEFAULT_LSD_ENABLED;
        public boolean utpEnabled = DEFAULT_UTP_ENABLED;
        public boolean upnpEnabled = DEFAULT_UPNP_ENABLED;
        public boolean natPmpEnabled = DEFAULT_NATPMP_ENABLED;
        public boolean encryptInConnections = DEFAULT_ENCRYPT_IN_CONNECTIONS;
        public boolean encryptOutConnections = DEFAULT_ENCRYPT_OUT_CONNECTIONS;
        public int encryptMode = DEFAULT_ENCRYPT_MODE;
        public boolean autoManaged = DEFAULT_AUTO_MANAGED;
    }

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

    @Override
    public void start()
    {
        SessionParams params = loadSettings();
        settings_pack sp = params.settings().swig();
        sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());

        super.start(params);
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
        if (callback != null)
            callback.onEngineStarted();
    }

    @Override
    protected void onBeforeStop()
    {
        saveAllResumeData();
        /* Handles must be destructed before the session is destructed */
        torrentTasks.clear();
        magnets.clear();
        loadedMagnets.clear();
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
                    TorrentHandle th = find(torrentAlert.handle().infoHash());
                    if (th == null)
                        break;
                    String hash = th.infoHash().toHex();
                    if (magnets.contains(hash))
                        break;
                    Torrent torrent = addTorrentsQueue.get(hash);
                    if (torrent == null)
                        break;
                    torrentTasks.put(torrent.getId(), newTask(th, torrent));
                    if (callback != null)
                        callback.onTorrentAdded(torrent.getId());
                    runNextLoadTorrentTask();
                    break;
                case METADATA_RECEIVED:
                    handleMetadata(((MetadataReceivedAlert) alert));
                    break;
                case TORRENT_REMOVED:
                    torrentTasks.remove(((TorrentRemovedAlert) alert).infoHash().toHex());
                    break;
            }
        }
    }

    private void handleMetadata(MetadataReceivedAlert metadataAlert)
    {
        TorrentHandle th = metadataAlert.handle();
        String hash = th.infoHash().toHex();
        if (!magnets.contains(hash))
            return;

        int size = metadataAlert.metadataSize();
        int maxSize = 2 * 1024 * 1024;
        byte[] bencode = null;
        if (0 < size && size <= maxSize)
            bencode = metadataAlert.torrentData(true);
        if (bencode != null)
            loadedMagnets.put(hash, bencode);
        remove(th, SessionHandle.DELETE_FILES);

        if (callback != null)
            callback.onMagnetLoaded(hash, bencode);
    }

    public byte[] getLoadedMagnet(String hash)
    {
        return loadedMagnets.get(hash);
    }

    public void removeLoadedMagnet(String hash)
    {
        loadedMagnets.remove(hash);
    }

    public void restoreDownloads(Collection<Torrent> torrents)
    {
        if (torrents == null)
            return;

        for (Torrent torrent : torrents) {
            if (torrent == null)
                continue;

            LoadTorrentTask loadTask = new LoadTorrentTask(torrent.getId());
            if (torrent.isDownloadingMetadata()) {
                loadTask.putMagnet(torrent.getTorrentFilePath(), new File(torrent.getTorrentFilePath()));
            } else {
                TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));
                List<Priority> priorities = torrent.getFilePriorities();
                if (priorities == null || priorities.size() != ti.numFiles()) {
                    if (callback != null)
                        callback.onRestoreSessionError(torrent.getId());
                    continue;
                }

                File saveDir = new File(torrent.getDownloadPath());
                String dataDir = TorrentUtils.findTorrentDataDir(context, torrent.getId());
                File resumeFile = null;
                if (dataDir != null) {
                    File file = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME);
                    if (file.exists())
                        resumeFile = file;
                }
                loadTask.putTorrentFile(new File(torrent.getTorrentFilePath()), saveDir,
                                        resumeFile, priorities.toArray(new Priority[priorities.size()]));
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

    public void download(Torrent torrent)
    {
        if (magnets.contains(torrent.getId()))
            cancelFetchMagnet(torrent.getId());

        File saveDir = new File(torrent.getDownloadPath());
        if (torrent.isDownloadingMetadata()) {
            addTorrentsQueue.put(torrent.getId(), torrent);
            download(torrent.getTorrentFilePath(), saveDir);
            return;
        }

        TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));
        List<Priority> priorities = torrent.getFilePriorities();
        if (priorities == null || priorities.size() != ti.numFiles())
            throw new IllegalArgumentException("File count doesn't match: " + torrent.getName());

        TorrentDownload task = torrentTasks.get(torrent.getId());
        if (task != null)
            task.remove(false);

        String dataDir = TorrentUtils.findTorrentDataDir(context, torrent.getId());
        File resumeFile = null;
        if (dataDir != null) {
            File file = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME);
            if (file.exists())
                resumeFile = file;
        }

        addTorrentsQueue.put(ti.infoHash().toString(), torrent);
        download(ti, saveDir, resumeFile, priorities.toArray(new Priority[priorities.size()]), null);
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
                    session_params params = libtorrent.read_session_params(n);
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

    private SettingsPack defaultSettingsPack()
    {
        SettingsPack sp = new SettingsPack();

        int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
        sp.maxQueuedDiskBytes(maxQueuedDiskBytes / 2);
        int sendBufferWatermark = sp.sendBufferWatermark();
        sp.sendBufferWatermark(sendBufferWatermark / 2);
        sp.seedingOutgoingConnections(false);
        // sp.setGuidedReadCache(true);
        settingsToSettingsPack(settings, sp);

        return sp;
    }

    private void settingsToSettingsPack(Settings settings, SettingsPack sp)
    {
        sp.cacheSize(settings.cacheSize);
        sp.activeDownloads(settings.activeDownloads);
        sp.activeSeeds(settings.activeSeeds);
        sp.activeLimit(settings.activeLimit);
        sp.maxPeerlistSize(settings.maxPeerListSize);
        sp.tickInterval(settings.tickInterval);
        sp.inactivityTimeout(settings.inactivityTimeout);
        sp.connectionsLimit(settings.connectionsLimit);
        sp.setString(settings_pack.string_types.listen_interfaces.swigValue(),
                "0.0.0.0:" + settings.port);
        sp.enableDht(settings.dhtEnabled);
        sp.broadcastLSD(settings.lsdEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), settings.utpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), settings.utpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), settings.upnpEnabled);
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), settings.natPmpEnabled);
        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), settings.encryptMode);
        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), settings.encryptMode);
        sp.uploadRateLimit(settings.uploadRateLimit);
        sp.downloadRateLimit(settings.downloadRateLimit);
    }

    private void applySettingsPack(SettingsPack sp)
    {
        if (sp == null)
            return;

        applySettings(sp);
        saveSettings();
    }

    public void setSettings(Settings settings)
    {
        this.settings = settings;
        applySettings(settings);
    }

    public Settings getSettings()
    {
        return settings;
    }

    private void applySettings(Settings settings)
    {
        if (settings == null || !isRunning())
            return;

        SettingsPack sp = settings();
        settingsToSettingsPack(settings, sp);
        applySettingsPack(sp);
    }

    public long getDownloadRate()
    {
        return stats().downloadRate();
    }

    public long getUploadRate()
    {
        return stats().uploadRate();
    }

    public long getTotalDownload()
    {
        return stats().totalDownload();
    }

    public long getTotalUpload()
    {
        return stats().totalUpload();
    }

    public int getDownloadRateLimit()
    {
        return settings().downloadRateLimit();
    }

    public int getUploadRateLimit()
    {
        return settings().uploadRateLimit();
    }

    public int getPort()
    {
        return swig().listen_port();
    }

    public void setPort(int port)
    {
        if (port == -1)
            return;

        settings.port = port;
        applySettings(settings);
    }

    public void setRandomPort()
    {
        int randomPort = Settings.MIN_PORT_NUMBER + (int)(Math.random()
                * ((Settings.MAX_PORT_NUMBER - Settings.MIN_PORT_NUMBER) + 1));
        setPort(randomPort);
    }

    public void enableIpFilter(String path)
    {
        if (path == null)
            return;

        IPFilterParser parser = new IPFilterParser(path);
        parser.setOnParsedListener(new IPFilterParser.OnParsedListener()
        {
            @Override
            public void onParsed(ip_filter filter, boolean success)
            {
                if (success && swig() != null)
                    swig().set_ip_filter(filter);
                if (callback != null)
                    callback.onIpFilterParsed(success);
            }
        });
        parser.parse();
    }

    public void disableIpFilter()
    {
        swig().set_ip_filter(new ip_filter());
    }

    public void setProxy(Context context, ProxySettingsPack proxy)
    {
        if (context == null || proxy == null || proxy.getType() == ProxySettingsPack.ProxyType.NONE)
            return;

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

        applySettingsPack(sp);
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

        return proxy;
    }

    public void disableProxy(Context context)
    {
        setProxy(context, new ProxySettingsPack());
    }

    @Override
    public boolean isPaused()
    {
        return super.isPaused();
    }

    public boolean isConnected()
    {
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null)
            return false;
        NetworkInfo activeNetwork  = manager.getActiveNetworkInfo();

        return activeNetwork != null && activeNetwork.isConnected();
    }

    public boolean isDHTEnabled()
    {
        return settings().enableDht();
    }

    public boolean isPeXEnabled()
    {
        /* PeX enabled by default in session_handle.session_flags_t::add_default_plugins */
        return true;
    }

    /*
     * Returns sha1hash.
     */

    public AddTorrentParams fetchMagnet(String uri) throws Exception
    {
        if (uri == null)
            throw new IllegalArgumentException("Magnet link is null");

        error_code ec = new error_code();
        add_torrent_params p = add_torrent_params.parse_magnet_uri(uri, ec);

        if (ec.value() != 0)
            throw new IllegalArgumentException(ec.message());

        p.set_disabled_storage();
        sha1_hash hash = p.getInfo_hash();
        String strHash = hash.to_hex();
        torrent_handle th = null;
        boolean add = false;

        try {
            syncMagnet.lock();

            try {
                th = swig().find_torrent(hash);
                if (th != null && th.is_valid()) {
                    add = false;
                    if (callback != null) {
                        torrent_info ti = th.torrent_file_ptr();
                        callback.onMagnetLoaded(strHash, ti != null ? new TorrentInfo(ti).bencode() : null);
                    }
                } else {
                    add = true;
                }

                if (add) {
                    if (p.getName().isEmpty())
                        p.setName(strHash);
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

        if (th.is_valid() && add)
            magnets.add(strHash);

        return new AddTorrentParams(p);
    }

    public void cancelFetchMagnet(String infoHash)
    {
        if (infoHash == null || !magnets.contains(infoHash))
            return;

        magnets.remove(infoHash);
        TorrentHandle th = find(new Sha1Hash(infoHash));
        if (th != null && th.isValid())
            remove(th, SessionHandle.DELETE_FILES);
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
            task.resume();
        }
    }

    public void setMaxConnectionsPerTorrent(int connections)
    {
        settings.connectionsLimitPerTorrent = connections;
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.setMaxConnections(connections);
        }
    }

    public void setMaxUploadsPerTorrent(int uploads)
    {
        settings.uploadsLimitPerTorrent = uploads;
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.setMaxUploads(uploads);
        }
    }

    public void setAutoManaged(boolean autoManaged)
    {
        settings.autoManaged = autoManaged;
        for (TorrentDownload task : torrentTasks.values())
                task.setAutoManaged(autoManaged);
    }

    public TorrentDownload newTask(TorrentHandle th, Torrent torrent)
    {
        TorrentDownload task = new TorrentDownload(context, th, torrent, callback);
        task.setMaxConnections(settings.connectionsLimitPerTorrent);
        task.setMaxUploads(settings.uploadsLimitPerTorrent);
        task.setSequentialDownload(torrent.isSequentialDownload());
        task.setAutoManaged(settings.autoManaged);
        if (torrent.isPaused())
            task.pause();
        else
            task.resume();

        return task;
    }

    public void saveAllResumeData()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null)
                continue;
            task.saveResumeData(true);
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
                    download(uri, saveDir);
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
