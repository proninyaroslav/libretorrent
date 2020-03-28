/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.stream;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse;
import static org.nanohttpd.protocols.http.response.Status.BAD_REQUEST;
import static org.nanohttpd.protocols.http.response.Status.FORBIDDEN;
import static org.nanohttpd.protocols.http.response.Status.NOT_FOUND;
import static org.nanohttpd.protocols.http.response.Status.NOT_MODIFIED;
import static org.nanohttpd.protocols.http.response.Status.OK;
import static org.nanohttpd.protocols.http.response.Status.PARTIAL_CONTENT;
import static org.nanohttpd.protocols.http.response.Status.RANGE_NOT_SATISFIABLE;

/*
 * The server that allows to stream selected file from a torrent and to which a specific address is assigned.
 * Supports partial content and DLNA (for some file formats)
 */

public class TorrentStreamServer extends NanoHTTPD
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentStreamServer.class.getSimpleName();

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private static HashMap<String, DLNAFileType> DLNA_FILE_TYPES;
    static {
        DLNA_FILE_TYPES = new HashMap<>();
        DLNA_FILE_TYPES.put("mp4", new DLNAFileType("mp4", "video/mp4", "DLNA.ORG_PN=AVC_MP4_BL_L3L_SD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000", "Streaming"));
        DLNA_FILE_TYPES.put("avi", new DLNAFileType("avi", "video/x-msvideo", "DLNA.ORG_PN=AVC_MP4_BL_L3L_SD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000", "Streaming"));
        DLNA_FILE_TYPES.put("mkv", new DLNAFileType("mkv", "video/x-matroska", "DLNA.ORG_PN=AVC_MKV_MP_HD_AC3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000", "Streaming"));
    }

    private TorrentEngine engine;

    public TorrentStreamServer(@NonNull String host, int port)
    {
        super(host, port);
    }

    public void start(@NonNull Context appContext) throws IOException
    {
        Log.i(TAG, "Start " + TAG);

        engine = TorrentEngine.getInstance(appContext);

        super.start();
    }

    @Override
    public void stop()
    {
        super.stop();

        Log.i(TAG, "Stop " + TAG);
    }

    /*
     * URL format: http://'hostname':'port'/stream?file='file_index'&torrent='torrent_hash'
     */

    public static String makeStreamUrl(@NonNull String hostname, int port,
                                       @NonNull String torrentId, int fileIndex)
    {
        try {
            return new URI("http", null, hostname, port,
                           "/stream",
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
    public Response handle(IHTTPSession session)
    {
        String uri = session.getUri();
        String extension = uri.substring(uri.lastIndexOf('.') + 1);
        DLNAFileType fileType = DLNA_FILE_TYPES.get(extension);

        Response res = handleTorrent(session);
        if (fileType != null)
            fileType.setHeaders(res);

        return res;
    }

    public Response handleTorrent(IHTTPSession httpSession)
    {
        if (engine == null)
            return newFixedLengthResponse(NOT_FOUND, "", "");

        if (!httpSession.getUri().equals("/stream"))
            return newFixedLengthResponse(BAD_REQUEST, "", "");

        Map<String, List<String>> params = httpSession.getParameters();
        if (params.size() < 2)
            return newFixedLengthResponse(BAD_REQUEST, "", "");

        String torrentId = Objects.requireNonNull(params.get("torrent")).get(0);

        int fileIndex;
        TorrentStream stream;
        try {
            fileIndex = Integer.parseInt(Objects.requireNonNull(params.get("file")).get(0));
            stream = engine.getStream(torrentId, fileIndex);
            if (stream == null)
                return newFixedLengthResponse(NOT_FOUND, "", "");

        } catch (Exception e) {
            return newFixedLengthResponse(NOT_FOUND, "", "");
        }

        Map<String, String> header = httpSession.getHeaders();
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

            /*
             * Get if-range header. If present, it must match etag or else we
             * should ignore the range request
             */
            String ifRange = header.get("if-range");
            boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));

            String ifNoneMatch = header.get("if-none-match");
            boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null &&
                    ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));

            if (headerIfRangeMissingOrMatching && range != null &&
                startFrom >= 0 && startFrom < stream.fileSize) {
                /*
                 * Range request that matches current etag
                 * and the startFrom of the range is satisfiable
                 */
                if (headerIfNoneMatchPresentAndMatching) {
                    /*
                     * Range request that matches current etag
                     * and the startFrom of the range is satisfiable
                     * would return range from file respond with not-modified
                     */
                    res = newFixedLengthResponse(NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);

                } else {
                    if (endAt < 0)
                        endAt = stream.fileSize - 1;
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0)
                        newLen = 0;

                    TorrentInputStream is = engine.getTorrentInputStream(stream);
                    is.skip(startFrom);

                    res = newFixedLengthResponse(PARTIAL_CONTENT, MIME_OCTET_STREAM, is, newLen);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + newLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + stream.fileSize);
                    res.addHeader("ETag", etag);
                    res.addHeader("Content-Disposition", "inline; filename=" + stream.id);
                }
            } else {
                if (headerIfRangeMissingOrMatching && range != null && startFrom >= stream.fileSize) {
                    /*
                     * Return the size of the file
                     * 4xx responses are not trumped by if-none-match
                     */
                    res = newFixedLengthResponse(RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                    res.addHeader("Content-Range", "bytes */" + stream.fileSize);
                    res.addHeader("ETag", etag);

                } else if (range == null && headerIfNoneMatchPresentAndMatching) {
                    /*
                     * Full-file-fetch request would return entire file
                     * respond with not-modified
                     */
                    res = newFixedLengthResponse(NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);

                } else if (!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
                    /*
                     * Range request that doesn't match current etag
                     * would return entire (different) file respond with not-modified
                     */
                    res = newFixedLengthResponse(NOT_MODIFIED, MIME_OCTET_STREAM, "");
                    res.addHeader("ETag", etag);

                } else {
                    TorrentInputStream is = engine.getTorrentInputStream(stream);
                    res = newFixedLengthResponse(OK, MIME_OCTET_STREAM, is, stream.fileSize);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + stream.fileSize);
                    res.addHeader("ETag", etag);
                    res.addHeader("Content-Disposition", "inline; filename=" + stream.id);
                }
            }

            return res;
        } catch (Throwable e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return newFixedLengthResponse(FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden");
        }
    }

    static class DLNAFileType
    {
        public final String dlnaContentFeatures;
        public final String dlnaTransferMode;
        public final String extension;
        public final String mimeType;

        DLNAFileType(String extension, String mimeType, String dlnaContentFeatures, String dlnaTransferMode)
        {
            this.extension = extension;
            this.mimeType = mimeType;
            this.dlnaContentFeatures = dlnaContentFeatures;
            this.dlnaTransferMode = dlnaTransferMode;
        }

        void setHeaders(Response res)
        {
            res.addHeader("contentFeatures.dlna.org", this.dlnaContentFeatures);
            res.addHeader("TransferMode.DLNA.ORG", this.dlnaTransferMode);
            res.addHeader("DAAP-Server", "iTunes/11.0.5 (OS X)");
            res.addHeader("Last-Modified", "2015-01-01T10:00:00Z");
            res.setMimeType(this.mimeType);
        }
    }
}
