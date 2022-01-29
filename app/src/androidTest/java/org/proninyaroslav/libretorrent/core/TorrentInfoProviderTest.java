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
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorrentInfoProviderTest extends AbstractTest
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentInfoProviderTest.class.getSimpleName();

    private AddTorrentParams params;
    private String torrentUrl = "http://www.pcds.fi/downloads/applications/internet/browsers/midori/current/debian-ubuntu/midori_0.5.11-0_amd64_.deb.torrent";
    private String torrentName = "midori_0.5.11-0_amd64_.deb";
    private String torrentHash = "3fe5f1a11c51cd01fd09a79621e074dda8eb36b6";
    private Uri dir;

    @Before
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
        params = new AddTorrentParams(downloadTorrent(torrentUrl), false,
                torrentHash, torrentName,
                new Priority[]{Priority.DEFAULT}, dir,
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