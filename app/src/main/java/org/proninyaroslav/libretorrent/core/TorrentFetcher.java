package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.frostwire.jlibtorrent.SessionManager;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpException;

/*
 * A class for downloading metadata from magnet, http and https links and
 * then saving it in the .torrent file.
 */

public class TorrentFetcher
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentFetcher.class.getSimpleName();

    private static final int FETCH_MAGNET_SECONDS = 30;

    private Context context;
    private Uri uri;

    public TorrentFetcher(Context context, Uri uri)
    {
        this.uri = uri;
        this.context = context;
    }

    /*
     * Returns a temporary torrent file.
     */

    public File fetch(File saveDir) throws FetchLinkException
    {
        File tempTorrent;

        if (saveDir == null) {
            throw new FetchLinkException("Temp dir not found");
        }

        try {
            if (uri == null || uri.getScheme() == null) {
                throw new IllegalArgumentException("Can't decode link");
            }

            if (!Utils.checkNetworkConnection(context)) {
                throw new FetchLinkException("No network connection");
            }

            switch (uri.getScheme()) {
                case Utils.MAGNET_PREFIX:
                    tempTorrent = TorrentUtils.createTempTorrentFile(fetchMagnet(uri), saveDir);
                    break;
                case Utils.HTTP_PREFIX:
                case Utils.HTTPS_PREFIX:
                    tempTorrent = TorrentUtils.createTempTorrentFile(saveDir);
                    fetchHTTP(uri, tempTorrent);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown link type: " + uri.getScheme());
            }

        } catch (Exception e) {
            throw new FetchLinkException(e);
        }

        return tempTorrent;
    }

    public byte[] fetchMagnet(Uri uri) throws FetchLinkException
    {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Can't decode link");
        }

        final SessionManager s = new SessionManager();
        final CountDownLatch signal = new CountDownLatch(1);

        s.start();

        final Timer timer = new Timer();
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                long nodes = s.stats().dhtNodes();
                /* Wait for at least 10 nodes in the DHT */
                if (nodes >= 10) {
                    Log.i(TAG, "DHT contains " + nodes + " nodes");

                    signal.countDown();
                    timer.cancel();
                }
            }
        }, 0, 1000);

        Log.i(TAG, "Waiting for nodes in DHT (10 seconds)...");
        try {
            boolean success = signal.await(10, TimeUnit.SECONDS);

            if (!success) {
                throw new FetchLinkException("DHT bootstrap timeout");
            }

        } catch (InterruptedException e) {
            /* Ignore */
        }

        Log.i(TAG, "Fetching the magnet link...");
        byte[] data = s.fetchMagnet(uri.toString(), FETCH_MAGNET_SECONDS);

        s.stop();

        return data;
    }

    public void fetchHTTP(Uri uri, final File targetFile) throws FetchLinkException, HttpException
    {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Can't decode link");
        }

        final ArrayList<Throwable> errorArray = new ArrayList<>(1);

        final CountDownLatch signal = new CountDownLatch(1);

        SyncHttpClient client = new SyncHttpClient();

        client.get(uri.toString(), new FileAsyncHttpResponseHandler(targetFile) {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file)
            {
                throwable.printStackTrace();
                errorArray.add(throwable);
                if (file.exists()) {
                    file.delete();
                }
                signal.countDown();
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, File file)
            {
                signal.countDown();
            }
        });

        try {
            Log.i(TAG, "Fetching link...");
            signal.await();

        } catch (InterruptedException e) {
            /* Ignore */
        }

        if (!errorArray.isEmpty()) {
            StringBuilder s = new StringBuilder();

            for (Throwable e : errorArray) {
                s.append(e.toString().concat("\n"));
            }

            throw new HttpException(s.toString());
        }
    }
}
