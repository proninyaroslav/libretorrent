package org.proninyaroslav.libretorrent.core;

import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorrentInfoProviderTest extends AbstractTest
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentInfoProviderTest.class.getSimpleName();

    private AddTorrentParams params;
    private String torrentUrl = "https://webtorrent.io/torrents/wired-cd.torrent";
    private String torrentName = "The WIRED CD - Rip. Sample. Mash. Share";
    private String torrentHash = "a88fda5954e89178c372716a6a78b8180ed4dad3";
    private int filesCount = 18;
    private Uri dir;

    @Before
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
        var p = new Priority[filesCount];
        Arrays.fill(p, Priority.DEFAULT);
        params = new AddTorrentParams(downloadTorrent(torrentUrl), false,
                torrentHash, torrentName,
                p, dir,
                false, false, new ArrayList<>(), false);
    }

    @Test
    public void observeStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeInfo(params.sha1hash)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((info) -> {
                    Log.d(TAG, "info=" + info);
                    assertEquals(params.sha1hash, info.torrentId);
                    if (info.stateCode == TorrentStateCode.FINISHED ||
                        info.stateCode == TorrentStateCode.SEEDING) {
                        c.countDown();
                        assertEquals(100, info.progress);
                    }
                });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(params.sha1hash), true);
        }
    }

    @Test
    public void observeAdvancedStateTest()
    {
        CountDownLatch c = new CountDownLatch(3);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeAdvancedInfo(params.sha1hash)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((info) -> {
                    Log.d(TAG, "info=" + info);
                    assertEquals(params.sha1hash, info.torrentId);
                    c.countDown();
                });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(params.sha1hash), true);
        }
    }

    @Test
    public void observeTrackersStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeTrackersInfo(params.sha1hash)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((infoList) -> {
                    if (!infoList.isEmpty()) {
                        c.countDown();
                        for (TrackerInfo info : infoList) {
                            Log.d(TAG, "info=" + info);
                            assertNotNull(info);
                            assertNotNull(info.url);
                        }
                    }
                });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(params.sha1hash), true);
        }
    }

    @Test
    public void observePeersStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observePeersInfo(params.sha1hash)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((infoList) -> {
                    if (!infoList.isEmpty()) {
                        c.countDown();
                        for (PeerInfo info : infoList) {
                            Log.d(TAG, "info=" + info);
                            assertNotNull(info);
                            assertNotNull(info.ip);
                        }
                    }
                });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(params.sha1hash), true);
        }
    }

    @Test
    public void observePiecesStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observePiecesInfo(params.sha1hash)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((pieces) -> {
                    c.countDown();
                    assertNotEquals(0, pieces.length);
                    boolean[] expectedPieces = engine.getPieces(params.sha1hash);
                    assertEquals(expectedPieces.length, pieces.length);
                });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(params.sha1hash), true);
        }
    }

    private String downloadTorrent(String url)
    {
        File tmp = fs.makeTempFile(".torrent");
        try {
            byte[] response = Utils.fetchHttpUrl(context, url);
            org.apache.commons.io.FileUtils.writeByteArrayToFile(tmp, response);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        return "file://" + tmp.getAbsolutePath();
    }
}