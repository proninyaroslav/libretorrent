/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.libtorrent4j.swig.address;
import org.libtorrent4j.swig.error_code;
import org.libtorrent4j.swig.ip_filter;
import org.proninyaroslav.libretorrent.core.filesystem.FileDescriptorWrapper;
import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacade;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;

import io.reactivex.Single;

/*
 * Parser of blacklist IP addresses in DAT and P2P formats.
 */

public class IPFilterParser
{
    @SuppressWarnings("unused")
    private static final String TAG = IPFilterParser.class.getSimpleName();

    private Uri path;

    public IPFilterParser(@NonNull Uri path)
    {
        this.path = path;
    }

    public Single<ip_filter> parse(@NonNull Context context)
    {
        return Single.create((emitter) -> {
            Log.d(TAG, "Start parsing IP filter file");
            ip_filter filter = new ip_filter();
            boolean success = false;

            String pathStr = path.toString();
            if (pathStr.contains(".dat"))
                success = parseDATFilterFile(context, path, filter);
            else if (pathStr.contains(".p2p"))
                success = parseP2PFilterFile(context, path, filter);

            Log.d(TAG, "Completed parsing IP filter file, is success = " + success);
            if (!emitter.isDisposed()) {
                if (success)
                    emitter.onSuccess(filter);
                else
                    emitter.onError(new IllegalStateException());
            }
        });
    }

    /*
     * Emule .DAT files contain leading zeroes in IPv4 addresses eg 001.009.106.186.
     * We need to remove them because Boost.Asio fail to parse them.
     */

    private static String cleanupIPAddress(String ip)
    {
        if (ip == null)
            return null;

        String cleanupIp = null;

        try {
            InetAddress address = InetAddress.getByName(ip);
            cleanupIp = address.getHostAddress();

        } catch (Exception e) {
            Log.e(TAG, "IP cleanup exception: " + Log.getStackTraceString(e));
        }

        return cleanupIp;
    }

    /*
     * Parser for eMule ip filter in DAT format
     */

    public static boolean parseDATFilterFile(Context context, Uri file, ip_filter filter)
    {
        if (!FileSystemFacade.fileExists(context, file))
            return false;

        long lineNum = 0;
        long badLineNum = 0;
        FileInputStream is = null;
        LineIterator it = null;
        try (FileDescriptorWrapper w = new FileDescriptorWrapper(file)) {
            FileDescriptor outFd = w.open(context, "r");
            is = new FileInputStream(outFd);
            it = IOUtils.lineIterator(is, "UTF-8");

            while (it.hasNext()) {
                ++lineNum;
                String line = it.nextLine();

                line = line.trim();
                if (line.isEmpty())
                    continue;

                /* Ignoring commented lines */
                if (line.startsWith("#") || line.startsWith("//"))
                    continue;

                /* Line should be split by commas */
                String[] parts = line.split(",");
                long elementNum = parts.length;

                /* IP Range should be split by a dash */
                String[] ips = parts[0].split("-");
                if (ips.length != 2) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Line was " + line);
                    ++badLineNum;
                    continue;
                }

                String startIp = cleanupIPAddress(ips[0]);
                if (startIp == null || startIp.isEmpty()) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Start IP of the range is malformated: " + ips[0]);
                    ++badLineNum;
                    continue;
                }

                error_code error = new error_code();
                address startAddr = address.from_string(startIp, error);
                if (error.value() > 0) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Start IP of the range is malformated:" + ips[0]);
                    ++badLineNum;
                    continue;
                }

                String endIp = cleanupIPAddress(ips[1]);
                if (endIp == null || endIp.isEmpty()) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "End IP of the range is malformated: " + ips[1]);
                    ++badLineNum;
                    continue;
                }

                address endAddr = address.from_string(endIp, error);
                if (error.value() > 0) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "End IP of the range is malformated:" + ips[1]);
                    ++badLineNum;
                    continue;
                }

                if (startAddr.is_v4() != endAddr.is_v4()) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "One IP is IPv4 and the other is IPv6!");
                    ++badLineNum;
                    continue;
                }

                /* Check if there is an access value (apparently not mandatory) */
                int accessNum = 0;
                if (elementNum > 1)
                    /* There is possibly one */
                    accessNum = Integer.parseInt(parts[1].trim());

                /* Ignoring this rule because access value is too high */
                if (accessNum > 127)
                    continue;

                try {
                    filter.add_rule(startAddr, endAddr, ip_filter.access_flags.blocked.swigValue());

                } catch (Exception e) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Line was " + line);
                    ++badLineNum;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return false;

        } finally {
            if (it != null)
                it.close();
            IOUtils.closeQuietly(is);
        }

        return badLineNum < lineNum;
    }

    /*
     * Parser for PeerGuardian ip filter in p2p format
     */

    public static boolean parseP2PFilterFile(Context context, Uri file, ip_filter filter)
    {
        if (!FileSystemFacade.fileExists(context, file))
            return false;

        long lineNum = 0;
        long badLineNum = 0;
        FileInputStream is = null;
        LineIterator it = null;
        try (FileDescriptorWrapper w = new FileDescriptorWrapper(file)) {
            FileDescriptor outFd = w.open(context, "r");
            is = new FileInputStream(outFd);
            it = IOUtils.lineIterator(is, "UTF-8");

            while (it.hasNext()) {
                ++lineNum;
                String line = it.nextLine();
                line = line.trim();
                if (line.isEmpty())
                    continue;
                /* Ignoring commented lines */
                if (line.startsWith("#") || line.startsWith("//"))
                    continue;

                /* Line should be split by ':' */
                String[] parts = line.split(":");
                if (parts.length < 2) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    ++badLineNum;
                    continue;
                }

                /* IP Range should be split by a dash */
                String[] ips = parts[1].split("-");
                if (ips.length != 2) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Line was " + line);
                    ++badLineNum;
                    continue;
                }

                String startIp = cleanupIPAddress(ips[0]);
                if (startIp == null || startIp.isEmpty()) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Start IP of the range is malformated: " + ips[0]);
                    ++badLineNum;
                    continue;
                }

                error_code error = new error_code();
                address startAddr = address.from_string(startIp, error);
                if (error.value() > 0) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Start IP of the range is malformated:" + ips[0]);
                    ++badLineNum;
                    continue;
                }

                String endIp = cleanupIPAddress(ips[1]);
                if (endIp == null || endIp.isEmpty()) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "End IP of the range is malformated: " + ips[1]);
                    ++badLineNum;
                    continue;
                }

                address endAddr = address.from_string(endIp, error);
                if (error.value() > 0) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "End IP of the range is malformated:" + ips[1]);
                    ++badLineNum;
                    continue;
                }

                if (startAddr.is_v4() != endAddr.is_v4()) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "One IP is IPv4 and the other is IPv6!");
                    ++badLineNum;
                    continue;
                }

                try {
                    filter.add_rule(startAddr, endAddr, ip_filter.access_flags.blocked.swigValue());

                } catch (Exception e) {
                    Log.w(TAG, "parseP2PFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Line was " + line);
                    ++badLineNum;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));

            return false;

        } finally {
            if (it != null)
                it.close();
            IOUtils.closeQuietly(is);
        }

        return badLineNum < lineNum;
    }
}
