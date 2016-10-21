/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.jlibtorrent.swig.settings_pack;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/*
 * This class is designed for seeding, downloading and management of torrents.
 */

public class TorrentEngine implements TorrentEngineInterface
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
    public static final int DEFAULT_TOR_PORT = 9050;
    public static final boolean DEFAULT_DHT_ENABLED = true;
    public static final boolean DEFAULT_LSD_ENABLED = true;
    public static final boolean DEFAULT_UTP_ENABLED = true;
    public static final boolean DEFAULT_UPNP_ENABLED = true;
    public static final boolean DEFAULT_NATPMP_ENABLED = true;
    public static final boolean DEFAULT_ENCRYPT_IN_CONNECTIONS = true;
    public static final boolean DEFAULT_ENCRYPT_OUT_CONNECTIONS = true;

    private Context context;
    private Session session;
    private TorrentEngineCallback callback;
    private Queue<LoadTorrentTask> loadTorrentsQueue = new LinkedList<>();
    private ExecutorService loadTorrentsExec;
    private Map<String, Torrent> addTorrentsQueue = new HashMap<>();
    private ReentrantLock sync;

    public TorrentEngine(Context context, TorrentEngineCallback callback) throws Exception
    {
        this.context = context;
        sync = new ReentrantLock();
        loadTorrentsExec = Executors.newCachedThreadPool();
        this.callback = callback;
    }

    @Override
    public void start()
    {
        sync.lock();

        try {
            if (isStarted()) {
                return;
            }

            session = new Session();
            loadSettings();
            setListener();

        } finally {
            if (callback != null) {
                callback.onEngineStarted();
            }
            sync.unlock();
        }
    }

    @Override
    public void stop()
    {
        sync.lock();

        try {
            if (session == null) {
                return;
            }

            saveSettings();
            session.abort();
            session = null;

        } finally {
            sync.unlock();
        }
    }

    @Override
    public void restart()
    {
        sync.lock();

        try {
            stop();
            /* Allow some time to release native resources */
            Thread.sleep(1000);
            start();

        } catch (InterruptedException e) {
            /* Ignore */
        } finally {
            sync.unlock();
        }
    }

    @Override
    public void pause()
    {
        if (session != null && !session.isPaused()) {
            session.pause();
        }
    }

    @Override
    public void resume()
    {
        if (session != null) {
            session.resume();
        }
    }

    private void setListener()
    {
        if (session == null) {
            return;
        }

        session.addListener(new AlertListener()
        {
            @Override
            public int[] types()
            {
                return new int[]{AlertType.ADD_TORRENT.swig()};
            }

            @Override
            public void alert(Alert<?> alert)
            {
                switch (alert.type()) {
                    case ADD_TORRENT:
                        TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;

                        Sha1Hash sha1hash = torrentAlert.handle().getInfoHash();
                        Torrent torrent = addTorrentsQueue.get(sha1hash.toString());

                        if (torrent != null) {
                            TorrentHandle handle = session.findTorrent(sha1hash);
                            handle.setSequentialDownload(torrent.isSequentialDownload());

                            if (torrent.isPaused()) {
                                handle.pause();
                            } else {
                                handle.resume();
                            }

                            TorrentDownload task = new TorrentDownload(context,
                                    TorrentEngine.this, handle, torrent, callback);

                            if (callback != null) {
                                callback.onTorrentAdded(torrent.getId(), task);
                            }
                        }
                        runNextLoadTorrentTask();
                        break;
                }
            }
        });
    }

    @Override
    public void asyncDownload(Collection<Torrent> torrents)
    {
        if (session == null || torrents == null) {
            return;
        }

        for (Torrent torrent : torrents) {
            TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));

            Priority[] priorities = new Priority[ti.numFiles()];
            List<Integer> torrentPriorities = torrent.getFilePriorities();

            if (torrentPriorities.size() != ti.numFiles()) {
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

            addTorrentsQueue.put(ti.infoHash().toString(), torrent);
            loadTorrentsQueue.add(
                    new LoadTorrentTask(new File(torrent.getTorrentFilePath()),
                            saveDir, resumeFile, priorities));
        }

        runNextLoadTorrentTask();
    }

    private void runNextLoadTorrentTask() {
        LoadTorrentTask task;

        try {
            task = loadTorrentsQueue.poll();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return;
        }

        if (task != null) {
            loadTorrentsExec.execute(task);
        }
    }

    @Override
    public Session getSession()
    {
        return session;
    }

    private void prioritizeFiles(TorrentHandle handle, Priority[] priorities)
    {
        if (handle == null) {
            return;
        }

        if (priorities != null) {
            /* Priorities for all files, priorities list for some selected files not supported */
            if (handle.getTorrentInfo().numFiles() != priorities.length) {
                return;
            }

            handle.prioritizeFiles(priorities);

        } else {
            /* Did they just add the entire torrent (therefore not selecting any priorities) */
            final Priority[] wholeTorrentPriorities =
                    Priority.array(Priority.NORMAL, handle.getTorrentInfo().numFiles());

            handle.prioritizeFiles(wholeTorrentPriorities);
        }
    }

    @Override
    public TorrentDownload download(Torrent torrent)
    {
        TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));

        Priority[] priorities = new Priority[ti.numFiles()];
        List<Integer> torrentPriorities = torrent.getFilePriorities();

        if (torrentPriorities.size() != ti.numFiles()) {
            return null;
        }

        for (int i = 0; i < torrentPriorities.size(); i++) {
            priorities[i] = Priority.fromSwig(torrentPriorities.get(i));
        }

        TorrentHandle handle = session.findTorrent(ti.infoHash());

        if (handle == null) {
            File saveDir = new File(torrent.getDownloadPath());

            String dataDir = TorrentUtils.findTorrentDataDir(context, torrent.getId());
            File resumeFile = null;

            if (dataDir != null) {
                File file = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME);
                if (file.exists()) {
                    resumeFile = file;
                }
            }

            /* New download */
            handle = session.addTorrent(ti, saveDir, priorities, resumeFile);
            handle.setSequentialDownload(torrent.isSequentialDownload());

            if (torrent.isPaused()) {
                handle.pause();
            } else {
                handle.resume();
            }

            return new TorrentDownload(context, this, handle, torrent, callback);

        } else {
            /* Found a download with the same hash, just adjust the priorities if needed */
            prioritizeFiles(handle, priorities);
        }

        return null;
    }

    @Override
    public void saveSettings()
    {
        if (session == null) {
            return;
        }

        try {
            TorrentUtils.saveSession(context, session.saveState());

        } catch (Exception e) {
            Log.e(TAG, "Error saving session state: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Override
    public void loadSettings()
    {
        if (session == null) {
            return;
        }

        try {
            String sessionPath = TorrentUtils.findSessionFile(context);

            if (sessionPath == null) {
                return;
            }

            File sessionFile = new File(sessionPath);

            if (sessionFile.exists()) {
                byte[] data = FileUtils.readFileToByteArray(sessionFile);
                session.loadState(data);
            } else {
                revertToDefaultConfiguration();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading session state: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Override
    public void revertToDefaultConfiguration()
    {
        if (session == null) {
            return;
        }

        SettingsPack sp = session.getSettingsPack();

        sp.broadcastLSD(true);

        int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
        sp.setMaxQueuedDiskBytes(maxQueuedDiskBytes / 2);
        int sendBufferWatermark = sp.sendBufferWatermark();
        sp.setSendBufferWatermark(sendBufferWatermark / 2);
        sp.setCacheSize(DEFAULT_CACHE_SIZE);
        sp.activeDownloads(DEFAULT_ACTIVE_DOWNLOADS);
        sp.activeSeeds(DEFAULT_ACTIVE_SEEDS);
        sp.activeLimit(DEFAULT_ACTIVE_LIMIT);
        sp.setMaxPeerlistSize(DEFAULT_MAX_PEER_LIST_SIZE);
        sp.setGuidedReadCache(true);
        sp.setTickInterval(DEFAULT_TICK_INTERVAL);
        sp.setInactivityTimeout(DEFAULT_INACTIVITY_TIMEOUT);
        sp.setSeedingOutgoingConnections(false);
        sp.setConnectionsLimit(DEFAULT_CONNECTIONS_LIMIT);

        setSettings(sp);
    }

    @Override
    public void setSettings(SettingsPack sp)
    {
        if (sp == null || session == null) {
            return;
        }

        session.applySettings(sp);
        saveSettings();
    }

    @Override
    public SettingsPack getSettings()
    {
        if (session == null) {
            return null;
        }

        return session.getSettingsPack();
    }

    @Override
    public void setDownloadSpeedLimit(int limit)
    {
        if (session == null) {
            return;
        }

        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setDownloadRateLimit(limit);
        setSettings(settingsPack);
    }

    @Override
    public int getDownloadSpeedLimit()
    {
        if (session == null) {
            return 0;
        }

        return session.getSettingsPack().downloadRateLimit();
    }

    @Override
    public void setUploadSpeedLimit(int limit)
    {
        if (session == null) {
            return;
        }

        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setUploadRateLimit(limit);
        session.applySettings(settingsPack);
        setSettings(settingsPack);
    }

    @Override
    public int getUploadSpeedLimit()
    {
        if (session == null) {
            return 0;
        }

        return session.getSettingsPack().uploadRateLimit();
    }

    @Override
    public long getDownloadRate()
    {
        if (session == null) {
            return 0;
        }

        return session.getStats().downloadRate();
    }

    @Override
    public long getUploadRate()
    {
        if (session == null) {
            return 0;
        }

        return session.getStats().uploadRate();
    }

    @Override
    public long getTotalDownload()
    {
        if (session == null) {
            return 0;
        }

        return session.getStats().download();
    }

    @Override
    public long getTotalUpload()
    {
        if (session == null) {
            return 0;
        }

        return session.getStats().upload();
    }

    @Override
    public int getDownloadRateLimit()
    {
        if (session == null) {
            return 0;
        }

        return session.getSettingsPack().downloadRateLimit();
    }

    @Override
    public int getUploadRateLimit()
    {
        if (session == null) {
            return 0;
        }

        return session.getSettingsPack().uploadRateLimit();
    }

    @Override
    public int getPort()
    {
        if (session == null) {
            return -1;
        }

        return session.getListenPort();
    }

    @Override
    public void setPort(int port)
    {
        if (session == null || port == -1) {
            return;
        }

        SettingsPack sp = session.getSettingsPack();
        sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:" + port);
        setSettings(sp);
    }

    @Override
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
                if (success && session != null) {
                    session.swig().set_ip_filter(filter);
                }

                if (callback != null) {
                    callback.onIpFilterParsed(success);
                }
            }
        });
        parser.parse();
    }

    @Override
    public void disableIpFilter()
    {
        if (session == null) {
            return;
        }

        session.swig().set_ip_filter(new ip_filter());
    }

    @Override
    public void setProxy(Context context, ProxySettingsPack proxy)
    {
        if (context == null || proxy == null || session == null) {
            return;
        }

        SettingsPack sp = session.getSettingsPack();

        settings_pack.proxy_type_t type = settings_pack.proxy_type_t.none;
        switch (proxy.getType()) {
            case TOR:
                enableTor(context, sp, proxy.isForceProxy());

                return;
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

    private void enableTor(Context context, SettingsPack sp, boolean forceProxy)
    {
        if (!OrbotHelper.isOrbotInstalled(context)) {
            return;
        }

        OrbotHelper.requestStartTor(context);

        sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks5.swigValue());
        sp.setInteger(settings_pack.int_types.proxy_port.swigValue(), DEFAULT_TOR_PORT);
        sp.setString(settings_pack.string_types.proxy_hostname.swigValue(), "localhost");
        sp.setBoolean(settings_pack.bool_types.force_proxy.swigValue(), forceProxy);

        setSettings(sp);
    }

    @Override
    public ProxySettingsPack getProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();
        SettingsPack sp = session.getSettingsPack();

        ProxySettingsPack.ProxyType type;
        String swigType = sp.getString(settings_pack.int_types.proxy_type.swigValue());

        if (swigType.equals(settings_pack.proxy_type_t.none.toString())) {
            type = ProxySettingsPack.ProxyType.NONE;
        } else if (swigType.equals(settings_pack.proxy_type_t.socks4.toString())) {
            type = ProxySettingsPack.ProxyType.SOCKS4;
        } else if (swigType.equals(settings_pack.proxy_type_t.socks5.toString())) {
            type = ProxySettingsPack.ProxyType.SOCKS5;
        } else if (swigType.equals(settings_pack.proxy_type_t.http.toString())) {
            type = ProxySettingsPack.ProxyType.HTTP;
        } else {
            type = ProxySettingsPack.ProxyType.TOR;
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

    @Override
    public void disableProxy(Context context)
    {
        setProxy(context, new ProxySettingsPack());
    }

    @Override
    public boolean isListening()
    {
        return session != null && session.isListening();
    }

    @Override
    public boolean isStarted()
    {
        return session != null;
    }

    public boolean isPaused()
    {
        return session != null && session.isPaused();
    }

    @Override
    public boolean isDHTEnabled()
    {
        return session != null && session.getSettingsPack().enableDht();
    }

    @Override
    public boolean isPeXEnabled()
    {
        /* PeX enabled by default in session_handle.session_flags_t::add_default_plugins */
        return session != null;
    }

    @Override
    public boolean isLSDEnabled()
    {
        return session != null && session.getSettingsPack().broadcastLSD();
    }

    private final class LoadTorrentTask implements Runnable
    {
        private final File torrent;
        private final File saveDir;
        private final File resume;
        private final Priority[] priorities;

        LoadTorrentTask(File torrent, File saveDir, File resume, Priority[] priorities)
        {
            this.torrent = torrent;
            this.saveDir = saveDir;
            this.resume = resume;
            this.priorities = priorities;
        }

        @Override
        public void run()
        {
            try {
                session.asyncAddTorrent(new TorrentInfo(torrent), saveDir, priorities, resume);

            } catch (Throwable e) {
                Log.e(TAG, "Unable to restore torrent from previous session. (" + torrent.getAbsolutePath() + "): ", e);
            }
        }
    }
}
