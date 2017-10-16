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

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/*
 * Parser of blacklist IP addresses in DAT and P2P formats.
 */

public class IPFilterParser
{
    @SuppressWarnings("unused")
    private static final String TAG = IPFilterParser.class.getSimpleName();
    private static final String THREAD_NAME = IPFilterParser.class.getSimpleName();

    private String path;
    private Handler handler;
    private OnParsedListener listener;

    public interface OnParsedListener
    {
        void onParsed(ip_filter filter, boolean success);
    }

    public IPFilterParser(String path)
    {
        this.path = path;
    }

    public void parse()
    {
        if (path == null) {
            return;
        }

        final ip_filter filter = new ip_filter();

        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        Runnable r = new Runnable()
        {
            @Override
            public void run() {
                Log.d(TAG, "start parsing IP filter file");
                boolean success = false;
                if (path.contains(".dat")) {
                    success = parseDATFilterFile(path, filter);
                } else if (path.contains(".p2p")) {
                    success = parseP2PFilterFile(path, filter);
                }

                Log.d(TAG, "completed parsing IP filter file, is success = " + success);
                if (listener != null) {
                    listener.onParsed(filter, success);
                }
            }
        };
        handler.post(r);
    }

    public void setOnParsedListener(OnParsedListener listener)
    {
        this.listener = listener;
    }

    /*
     * Emule .DAT files contain leading zeroes in IPv4 addresses eg 001.009.106.186.
     * We need to remove them because Boost.Asio fail to parse them.
     */

    private static String cleanupIPAddress(String ip)
    {
        if (ip == null) {
            return null;
        }

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
     * Parser for eMule ip filter in DAT format.
     */

    public static boolean parseDATFilterFile(String path, ip_filter filter)
    {
        if (path == null || filter == null) {
            return false;
        }

        File file = new File(path);
        if (!file.exists()) {
            return false;
        }

        LineIterator it = null;

        try {
            it = FileUtils.lineIterator(file, "UTF-8");

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        if (it == null) {
            return false;
        }

        long lineNum = 0;
        long badLineNum = 0;
        try {
            while (it.hasNext()) {
                ++lineNum;
                String line = it.nextLine();

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                /* Ignoring commented lines */
                if (line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

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
                if (elementNum > 1) {
                /* There is possibly one */
                    accessNum = Integer.parseInt(parts[1].trim());
                }

                /* Ignoring this rule because access value is too high */
                if (accessNum > 127) {
                    continue;
                }

                try {
                    filter.add_rule(startAddr, endAddr, ip_filter.access_flags.blocked.swigValue());

                } catch (Exception e) {
                    Log.w(TAG, "parseDATFilterFile: line " + lineNum + " is malformed.");
                    Log.w(TAG, "Line was " + line);
                    ++badLineNum;
                }
            }

        } finally {
            it.close();
        }

        return badLineNum < lineNum;
    }

    /*
     * Parser for PeerGuardian ip filter in p2p format.
     */

    public static boolean parseP2PFilterFile(String path, ip_filter filter)
    {
        if (path == null || filter == null) {
            return false;
        }

        File file = new File(path);
        if (!file.exists()) {
            return false;
        }

        LineIterator it = null;

        try {
            it = FileUtils.lineIterator(file, "UTF-8");

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        if (it == null) {
            return false;
        }

        long lineNum = 0;
        long badLineNum = 0;

        try {
            while (it.hasNext()) {
                ++lineNum;
                String line = it.nextLine();

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                /* Ignoring commented lines */
                if (line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

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

        } finally {
            it.close();
        }

        return badLineNum < lineNum;
    }
}
