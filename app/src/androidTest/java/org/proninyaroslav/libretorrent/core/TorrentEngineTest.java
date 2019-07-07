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

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.*;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exceptions.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

@RunWith(AndroidJUnit4.class)
public class TorrentEngineTest extends AbstractTest
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentEngineTest.class.getSimpleName();

    private AddTorrentParams params, params2;
    private String torrentUrl = "http://www.pcds.fi/downloads/applications/internet/browsers/midori/current/debian-ubuntu/midori_0.5.11-0_amd64_.deb.torrent";
    private String torrentName = "midori_0.5.11-0_amd64_.deb";
    private String torrentHash = "3fe5f1a11c51cd01fd09a79621e074dda8eb36b6";
    private String torrentUrl2 = "https://antixlinux.com/?smd_process_download=1&download_id=3034";
    private String torrentName2 = "antiX-17.4.1_x64-net.iso";
    private String torrentHash2 = "320ef354343c24543998a26d3da00f3a77101b21";
    private String magnetHash = "85922fbee6dce5e2f5491e16bcdd9e6e427ba5aa";
    private String magnet = "magnet:?xt=urn:btih:QWJC7PXG3TS6F5KJDYLLZXM6NZBHXJNK";
    private Uri dir;

    @Before
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + FileUtils.getDefaultDownloadPath());
        params = new AddTorrentParams(downloadTorrent(torrentUrl), false,
                torrentHash, torrentName,
                Collections.singletonList(Priority.DEFAULT), dir,
                false, false);

        params = new AddTorrentParams(downloadTorrent(torrentUrl2), false,
                torrentHash2, torrentName2,
                Collections.singletonList(Priority.DEFAULT), dir,
                false, false);
    }

    @Test
    public void downloadTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        engine.addListener(new TorrentEngineListener() {
            @Override
            public void onTorrentFinished(@NonNull String id)
            {
                if (!params.sha1hash.equals(id))
                    return;

                c.countDown();
                try {
                    Torrent t = torrentRepo.getTorrentById(id);
                    assertNotNull(t);

                    /* Check if file exists */
                    File file = new File(dir.getPath(), torrentName);
                    assertTrue(file.exists());

                } finally {
                    engine.deleteTorrents(Collections.singletonList(id), true);
                }
            }
        });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void downloadTest_duplicateTorrent()
    {
        CountDownLatch c = new CountDownLatch(1);
        assertTrue(engine.isRunning());

        engine.addListener(new TorrentEngineListener() {
            @Override
            public void onTorrentFinished(@NonNull String id)
            {
                if (!params.sha1hash.equals(id))
                    return;

                c.countDown();
                try {
                    Torrent t = torrentRepo.getTorrentById(id);
                    assertNotNull(t);

                    try {
                        engine.addTorrentSync(params, true);

                    } catch (TorrentAlreadyExistsException e) {
                        engine.deleteTorrents(Collections.singletonList(id), true);
                        return;
                    }
                    fail();

                } catch (Exception e) {
                    engine.deleteTorrents(Collections.singletonList(id), true);
                    fail(Log.getStackTraceString(e));
                }
            }
        });

        try {
            engine.addTorrentSync(params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void applyParamsTest()
    {
        CountDownLatch c = new CountDownLatch(1);
        AtomicBoolean applying = new AtomicBoolean();
        ChangeableParams params = new ChangeableParams();
        params.sequentialDownload = true;
        params.name = "test";

        assertTrue(engine.isRunning());

        engine.addListener(new TorrentEngineListener() {
            @Override
            public void onTorrentAdded(@NonNull String id)
            {
                if (TorrentEngineTest.this.params.sha1hash.equals(id))
                    engine.changeParams(TorrentEngineTest.this.params.sha1hash, params);
            }

            @Override
            public void onApplyingParams(@NotNull String id)
            {
                applying.set(true);
            }

            @Override
            public void onParamsApplied(@NotNull String id, Throwable e)
            {
                if (!TorrentEngineTest.this.params.sha1hash.equals(id))
                    return;

                c.countDown();

                if (e != null)
                    fail(Log.getStackTraceString(e));

                assertTrue(applying.get());

                try {
                    Torrent t = torrentRepo.getTorrentById(id);
                    assertNotNull(t);
                    assertEquals(params.name, t.name);

                } finally {
                    engine.deleteTorrents(Collections.singletonList(id), true);
                }
            }
        });

        try {
            engine.addTorrentSync(this.params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void moveTorrentTest()
    {
        CountDownLatch c = new CountDownLatch(2);
        AtomicBoolean applying = new AtomicBoolean();
        ChangeableParams params = new ChangeableParams();
        params.dirPath = Uri.parse("file://" + FileUtils.getUserDirPath());

        assertTrue(engine.isRunning());

        engine.addListener(new TorrentEngineListener() {
            @Override
            public void onTorrentAdded(@NonNull String id)
            {
                if (TorrentEngineTest.this.params.sha1hash.equals(id))
                    engine.changeParams(TorrentEngineTest.this.params.sha1hash, params);
            }

            @Override
            public void onApplyingParams(@NotNull String id)
            {
                applying.set(true);
            }

            @Override
            public void onTorrentMoved(@NotNull String id, boolean success)
            {
                if (!TorrentEngineTest.this.params.sha1hash.equals(id))
                    return;

                c.countDown();

                assertTrue(success);
            }

            @Override
            public void onParamsApplied(@NotNull String id, Throwable e)
            {
                if (!TorrentEngineTest.this.params.sha1hash.equals(id))
                    return;

                c.countDown();

                if (e != null)
                    fail(Log.getStackTraceString(e));

                assertTrue(applying.get());

                try {
                    Torrent t = torrentRepo.getTorrentById(id);
                    assertNotNull(t);
                    assertEquals(params.dirPath, t.downloadPath);
                    /* Check if file exists */
                    File file = new File(dir.getPath(), torrentName);
                    assertTrue(file.exists());

                } finally {
                    engine.deleteTorrents(Collections.singletonList(id), true);
                }
            }
        });

        try {
            engine.addTorrentSync(this.params, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void fetchMagnetTest()
    {
        CountDownLatch c = new CountDownLatch(1);
        Disposable d = null;

        assertTrue(engine.isRunning());

        try {
            Pair<MagnetInfo, Single<TorrentMetaInfo>> res = engine.fetchMagnet(magnet);
            MagnetInfo magnetInfo = res.first;

            assertEquals(magnet, magnetInfo.getUri());
            assertEquals(magnetHash, magnetInfo.getSha1hash());

            d = res.second.subscribe((info) -> {
                        c.countDown();
                        assertNotNull(info);
                        assertEquals(magnetInfo.getSha1hash(), info.sha1Hash);
                    });

            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            if (d != null)
                d.dispose();
        }
    }

    private String downloadTorrent(String url)
    {
        File tmp = FileUtils.makeTempFile(context, ".torrent");
        try {
            byte[] response = Utils.fetchHttpUrl(context, url);
            org.apache.commons.io.FileUtils.writeByteArrayToFile(tmp, response);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        return "file://" + tmp.getAbsolutePath();
    }
}