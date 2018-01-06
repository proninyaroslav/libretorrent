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

package org.proninyaroslav.libretorrent.core.stateparcel;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.peer_info;
import com.frostwire.jlibtorrent.swig.piece_index_bitfield;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/*
 * The class provides a package model with information
 * about the state of the peer, sent from the service.
 */

public class PeerStateParcel extends AbstractStateParcel<PeerStateParcel>
{
    public String ip;
    public String client;
    public long totalDownload;
    public long totalUpload;
    public double relevance;
    public int connectionType;
    public int port;
    public int progress;
    public int payloadDownSpeed;
    public int payloadUpSpeed;

    public class ConnectionType
    {
        public static final int BITTORRENT = 0;
        public static final int WEB = 1;
        public static final int UTP = 2;
    }

    public PeerStateParcel(peer_info peer, TorrentStatus torrentStatus)
    {
        super(new Address(peer.getIp().address()).toString());

        ip = new Address(peer.getIp().address()).toString();
        byte[] clientBytes = Vectors.byte_vector2bytes(peer.get_client());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            client = new String(clientBytes, Charset.forName("UTF-8"));
        } else {
            client = new String(clientBytes, StandardCharsets.UTF_8);
        }
        totalDownload = peer.getTotal_download();
        totalUpload = peer.getTotal_upload();
        relevance = calcRelevance(peer, torrentStatus);
        connectionType = getConnectionType(peer);
        port = peer.getIp().port();
        progress = (int) (peer.getProgress() * 100);
        payloadDownSpeed = peer.getPayload_down_speed();
        payloadUpSpeed = peer.getPayload_up_speed();
    }

    public PeerStateParcel(String ip, String client,
                           long totalDownload, long totalUpload,
                           double relevance, int connectionType,
                           int port, int progress,
                           int payloadDownSpeed, int payloadUpSpeed)
    {
        super(ip);

        this.ip = ip;
        this.client = client;
        this.totalDownload = totalDownload;
        this.totalUpload = totalUpload;
        this.relevance = relevance;
        this.connectionType = connectionType;
        this.port = port;
        this.progress = progress;
        this.payloadDownSpeed = payloadDownSpeed;
        this.payloadUpSpeed = payloadUpSpeed;
    }

    public PeerStateParcel(Parcel source)
    {
        super(source);

        ip = source.readString();
        client = source.readString();
        totalDownload = source.readLong();
        totalUpload = source.readLong();
        relevance = source.readDouble();
        connectionType = source.readInt();
        port = source.readInt();
        progress = source.readInt();
        payloadDownSpeed = source.readInt();
        payloadUpSpeed = source.readInt();
    }

    private int getConnectionType(peer_info peer)
    {
        if (peer.getFlags().and_(peer_info.utp_socket).nonZero()) {
            return ConnectionType.UTP;
        }

        int connection;
        int type = peer.getConnection_type();

        if (type == peer_info.connection_type_t.standard_bittorrent.swigValue()) {
            connection = ConnectionType.BITTORRENT;
        } else {
            connection = ConnectionType.WEB;
        }

        return connection;
    }

    private double calcRelevance(peer_info peer, TorrentStatus torrentStatus)
    {
        double relevance = 0.0;
        piece_index_bitfield allPieces = torrentStatus.pieces().swig();
        piece_index_bitfield peerPieces = peer.getPieces();
        if (peerPieces == null)
            return relevance;

        int remoteHaves = 0;
        int localMissing = 0;
        for (int i = 0; i < allPieces.size(); i++) {
            if (!allPieces.get_bit(i)) {
                ++localMissing;
                if (peerPieces.get_bit(i))
                    ++remoteHaves;
            }
        }
        if (localMissing != 0)
            relevance = (double) remoteHaves / (double) localMissing;

        return relevance;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        super.writeToParcel(dest, flags);

        dest.writeString(ip);
        dest.writeString(client);
        dest.writeLong(totalDownload);
        dest.writeLong(totalUpload);
        dest.writeDouble(relevance);
        dest.writeInt(connectionType);
        dest.writeInt(port);
        dest.writeInt(progress);
        dest.writeInt(payloadDownSpeed);
        dest.writeInt(payloadUpSpeed);
    }

    public static final Parcelable.Creator<PeerStateParcel> CREATOR =
            new Parcelable.Creator<PeerStateParcel>()
            {
                @Override
                public PeerStateParcel createFromParcel(Parcel source)
                {
                    return new PeerStateParcel(source);
                }

                @Override
                public PeerStateParcel[] newArray(int size)
                {
                    return new PeerStateParcel[size];
                }
            };

    @Override
    public int compareTo(PeerStateParcel another)
    {
        return ip.compareTo(another.ip);
    }

    @Override
    public int hashCode()
    {
        int prime = 31, result = 1;

        result = prime * result + ((ip == null) ? 0 : ip.hashCode());
        result = prime * result + ((client == null) ? 0 : client.hashCode());
        result = prime * result + (int) (totalDownload ^ (totalDownload >>> 32));
        result = prime * result + (int) (totalUpload ^ (totalUpload >>> 32));
        long relevanceBits = Double.doubleToLongBits(relevance);
        result = prime * result + (int) (relevanceBits ^ (relevanceBits >>> 32));
        result = prime * result + connectionType;
        result = prime * result + port;
        result = prime * result + progress;
        result = prime * result + payloadDownSpeed;
        result = prime * result + payloadUpSpeed;

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PeerStateParcel)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        PeerStateParcel state = (PeerStateParcel) o;

        return (ip == null || ip.equals(state.ip)) &&
                (client == null || client.equals(state.client)) &&
                totalDownload == state.totalDownload &&
                totalUpload == state.totalUpload &&
                relevance == state.relevance &&
                connectionType == state.connectionType &&
                port == state.port &&
                progress == state.progress &&
                payloadDownSpeed == state.payloadDownSpeed &&
                payloadUpSpeed == state.payloadUpSpeed;
    }

    @Override
    public String toString()
    {
        return "PeerStateParcel{" +
                "ip='" + ip + '\'' +
                ", client='" + client + '\'' +
                ", totalDownload=" + totalDownload +
                ", totalUpload=" + totalUpload +
                ", relevance=" + relevance +
                ", connectionType='" + connectionType + '\'' +
                ", port=" + port +
                ", progress=" + progress +
                ", payloadDownSpeed=" + payloadDownSpeed +
                ", payloadUpSpeed=" + payloadUpSpeed +
                '}';
    }
}
