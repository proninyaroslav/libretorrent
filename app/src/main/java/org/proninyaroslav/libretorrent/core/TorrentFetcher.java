package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.HttpStatus;

/*
 * A class for downloading metadata from magnet, http and https links and
 * then saving it in the .torrent file.
 */

public class TorrentFetcher
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentFetcher.class.getSimpleName();

    private static final int FETCH_MAGNET_SECONDS = 30;
    private static final String[] allowedContentTypes = new String[]{"application/x-bittorrent"};

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

        try {
            if (uri == null || uri.getScheme() == null) {
                throw new IllegalArgumentException("Can't decode link");
            }

            if (!Utils.checkNetworkConnection(context)) {
                throw new FetchLinkException("No network connection");
            }

            byte[] data;
            switch (uri.getScheme()) {
                case Utils.MAGNET_PREFIX:
                    data = fetchMagnet(uri);
                    break;
                case Utils.HTTP_PREFIX:
                case Utils.HTTPS_PREFIX:
                    data = fetchHTTP(uri);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown link type: " + uri.getScheme());
            }

            tempTorrent = TorrentUtils.createTempTorrentFile(data, saveDir);

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

        final Session s = new Session();
        final CountDownLatch signal = new CountDownLatch(1);

        /* The session stats are posted about once per second */
        AlertListener l = new AlertListener()
        {
            @Override
            public int[] types()
            {
                return new int[]{AlertType.SESSION_STATS.swig(), AlertType.DHT_STATS.swig()};
            }

            @Override
            public void alert(Alert<?> alert) {
                if (alert.type().equals(AlertType.SESSION_STATS)) {
                    s.postDHTStats();
                }

                if (alert.type().equals(AlertType.DHT_STATS)) {
                    long nodes = s.getStats().dhtNodes();
                    /* Wait for at least 10 nodes in the DHT */
                    if (nodes >= 10) {
                        Log.i(TAG, "DHT contains " + nodes + " nodes");
                        signal.countDown();
                    }
                }
            }
        };

        s.addListener(l);
        s.postDHTStats();

        Log.i(TAG, "Waiting for nodes in DHT (10 seconds)...");
        try {
            boolean success = signal.await(10, TimeUnit.SECONDS);

            if (!success) {
                throw new FetchLinkException("DHT bootstrap timeout");
            }

        } catch (InterruptedException e) {
            /* Ignore */
        }

        s.removeListener(l);

        Log.i(TAG, "Fetching the magnet link...");

        return s.fetchMagnet(uri.toString(), FETCH_MAGNET_SECONDS);
    }

    public byte[] fetchHTTP(Uri uri) throws FetchLinkException, HttpException
    {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Can't decode link");
        }

        final ArrayList<Throwable> errorArray = new ArrayList<Throwable>(1);
        final ArrayList<Byte> data = new ArrayList<Byte>();

        final CountDownLatch signal = new CountDownLatch(1);

        SyncHttpClient client = new SyncHttpClient();

        client.get(uri.toString(), new BinaryHttpResponseHandler(allowedContentTypes) {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] binaryData)
            {
                if (statusCode == HttpStatus.SC_OK) {
                    for (byte b : binaryData) {
                        data.add(b);
                    }
                }
                signal.countDown();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers,
                                  byte[] binaryData, Throwable error)
            {
                error.printStackTrace();
                errorArray.add(error);
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

        return (data.isEmpty() ? null : Utils.toPrimitive(data));
    }
}
