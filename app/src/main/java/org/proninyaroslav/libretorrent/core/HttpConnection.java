/*
 * Copyright (C) 2018 Tachibana General Laboratories, LLC
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of Download Navi.
 *
 * Download Navi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Download Navi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Download Navi.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core;

import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

/*
 * The wrapper for HttpUrlConnection.
 */

public class HttpConnection implements Runnable
{
    /* Can't be more than 7 */
    private static final int MAX_REDIRECTS = 5;
    private static final int DEFAULT_TIMEOUT = (int)(20 * SECOND_IN_MILLIS);

    private URL url;
    private SSLContext sslContext;
    private Listener listener;

    public interface Listener
    {
        void onConnectionCreated(HttpURLConnection conn);

        void onResponseHandle(HttpURLConnection conn, int code, String message);

        void onMovedPermanently(String newUrl);

        void onIOException(IOException e);

        void onTooManyRedirects();
    }

    public HttpConnection(String url) throws MalformedURLException, GeneralSecurityException
    {
        this.url = new URL(url);
        this.sslContext = Utils.getSSLContext();
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    @Override
    public void run()
    {
        int redirectionCount = 0;
        while (redirectionCount++ < MAX_REDIRECTS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection)url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(DEFAULT_TIMEOUT);
                conn.setReadTimeout(DEFAULT_TIMEOUT);

                if (conn instanceof HttpsURLConnection)
                    ((HttpsURLConnection)conn).setSSLSocketFactory(sslContext.getSocketFactory());

                if (listener != null)
                    listener.onConnectionCreated(conn);

                int responseCode = conn.getResponseCode();
                switch (responseCode) {
                    case HTTP_MOVED_PERM:
                    case HTTP_MOVED_TEMP:
                    case HTTP_SEE_OTHER:
                        String location = conn.getHeaderField("Location");
                        url = new URL(url, location);
                        if (responseCode == HTTP_MOVED_PERM && listener != null)
                            listener.onMovedPermanently(url.toString());
                        continue;
                    default:
                        if (listener != null)
                            listener.onResponseHandle(conn, responseCode, conn.getResponseMessage());
                        return;
                }

            } catch (IOException e) {
                if (listener != null)
                    listener.onIOException(e);
                return;

            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        }

        if (listener != null)
            listener.onTooManyRedirects();
    }
}
