/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.server;

import android.util.Log;

import org.proninyaroslav.libretorrent.core.TorrentDownload;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentInputStream;
import org.proninyaroslav.libretorrent.core.TorrentStream;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class TorrentStreamServer extends NanoHTTPD
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentStreamServer.class.getSimpleName();

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    public TorrentStreamServer(String host, int port)
    {
        super(host, port);
    }

    @Override
    public void start() throws IOException
    {
        Log.i(TAG, "Start " + TAG);

        super.start();
    }

    @Override
    public void stop()
    {
        super.stop();

        Log.i(TAG, "Stop " + TAG);
    }

    /*
     * URL format: http://'hostname':'port'/stream/?file='file_index'&torrent='torrent_hash'
     */

    public static String makeStreamUrl(String hostname, int port,
                                       String torrentId, int fileIndex)
    {
        try {
            return new URI("http", null, hostname, port,
                           "/stream/",
                           String.format(Locale.getDefault(),
                                         "file=%d&torrent=%s",
                                         fileIndex,
                                         torrentId),
                           null)
                          .toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    @Override
    public Response serve(IHTTPSession session)
    {
        if (!session.getUri().equals("/stream/"))
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "", "");

        Map<String, List<String>> params = session.getParameters();
        if (params.size() < 2)
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "", "");

        String torrentId = params.get("torrent").get(0);

        TorrentDownload task = TorrentEngine.getInstance().getTask(torrentId);
        if (task == null)
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "", "");

        int fileIndex;
        TorrentStream stream;
        try {
            fileIndex = Integer.parseInt(params.get("file").get(0));
            stream = task.getStream(fileIndex);
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "", "");
        }

        Map<String, String> header = session.getHeaders();
        try {
            Response res;
            String etag = stream.id;
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");

            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range.substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {

                    }
                }
            }

            // get if-range header. If present, it must match etag or else we
            // should ignore the range request
            String ifRange = header.get("if-range");
            boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));

            String ifNoneMatch = header.get("if-none-match");
            boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));

            if (headerIfRangeMissingOrMatching && range != null &&
                startFrom >= 0 && startFrom < stream.fileSize) {
                // range request that matches current etag
                // and the startFrom of the range is satisfiable
                if (headerIfNoneMatchPresentAndMatching) {
                    // range request that matches current etag
                    // and the startFrom of the range is satisfiable
                    // would return range from file
                    // respond with not-modified
                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);
                } else {
                    if (endAt < 0)
                        endAt = stream.fileSize - 1;
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0)
                        newLen = 0;

                    TorrentInputStream inputStream = new TorrentInputStream(stream);
                    inputStream.skip(startFrom);

                    res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, MIME_OCTET_STREAM,
                                                 inputStream, newLen);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + newLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + stream.fileSize);
                    res.addHeader("ETag", etag);
                    res.addHeader("Content-Disposition", "inline; filename=" + stream.id);
                }
            } else {
                if (headerIfRangeMissingOrMatching && range != null && startFrom >= stream.fileSize) {
                    // return the size of the file
                    // 4xx responses are not trumped by if-none-match
                    res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                    res.addHeader("Content-Range", "bytes */" + stream.fileSize);
                    res.addHeader("ETag", etag);
                } else if (range == null && headerIfNoneMatchPresentAndMatching) {
                    // full-file-fetch request
                    // would return entire file
                    // respond with not-modified
                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);
                } else if (!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
                    // range request that doesn't match current etag
                    // would return entire (different) file
                    // respond with not-modified

                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);
                } else {
                    TorrentInputStream inputStream = new TorrentInputStream(stream);
                    res = newFixedLengthResponse(Response.Status.OK, MIME_OCTET_STREAM,
                                                 inputStream, stream.fileSize);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + stream.fileSize);
                    res.addHeader("ETag", etag);
                    res.addHeader("Content-Disposition", "inline; filename=" + stream.id);
                }
            }

            return res;
        } catch (Throwable e) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden");
        }
    }
}
