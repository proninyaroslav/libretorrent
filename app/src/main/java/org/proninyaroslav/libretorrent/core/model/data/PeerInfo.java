/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.libtorrent4j.PieceIndexBitfield;
import org.libtorrent4j.TorrentStatus;
import org.proninyaroslav.libretorrent.core.model.session.AdvancedPeerInfo;

/*
 * The class provides a package model with information
 * about the state of the peer, sent from the service.
 */

public class PeerInfo extends AbstractInfoParcel
{
    public String ip;
    public String client;
    public long totalDownload;
    public long totalUpload;
    public double relevance;
    public int connectionType;
    public int port;
    public int progress;
    public int downSpeed;
    public int upSpeed;

    public class ConnectionType
    {
        public static final int BITTORRENT = 0;
        public static final int WEB = 1;
        public static final int UTP = 2;
    }

    public PeerInfo(AdvancedPeerInfo peer, TorrentStatus torrentStatus)
    {
        super(peer.ip());

        ip = peer.ip();
        client = peer.client();
        totalDownload = peer.totalDownload();
        totalUpload = peer.totalUpload();
        relevance = calcRelevance(peer, torrentStatus);
        connectionType = getConnectionType(peer);
        port = peer.port();
        progress = (int) (peer.progress() * 100);
        downSpeed = peer.downSpeed();
        upSpeed = peer.upSpeed();
    }

    public PeerInfo(String ip, String client,
                    long totalDownload, long totalUpload,
                    double relevance, int connectionType,
                    int port, int progress,
                    int downSpeed, int upSpeed)
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
        this.downSpeed = downSpeed;
        this.upSpeed = upSpeed;
    }

    public PeerInfo(Parcel source)
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
        downSpeed = source.readInt();
        upSpeed = source.readInt();
    }

    private int getConnectionType(AdvancedPeerInfo peer)
    {
        if (peer.isUtp())
            return ConnectionType.UTP;

        switch (peer.connectionType()) {
            case WEB_SEED:
            case HTTP_SEED:
                return ConnectionType.WEB;
            default:
                return ConnectionType.BITTORRENT;
        }
    }

    private double calcRelevance(AdvancedPeerInfo peer, TorrentStatus torrentStatus)
    {
        double relevance = 0.0;
        PieceIndexBitfield allPieces = torrentStatus.pieces();
        PieceIndexBitfield peerPieces = peer.pieces();

        int remoteHaves = 0;
        int localMissing = 0;
        for (int i = 0; i < allPieces.size(); i++) {
            if (!allPieces.getBit(i)) {
                ++localMissing;
                if (peerPieces.getBit(i))
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
        dest.writeInt(downSpeed);
        dest.writeInt(upSpeed);
    }

    public static final Parcelable.Creator<PeerInfo> CREATOR =
            new Parcelable.Creator<PeerInfo>()
            {
                @Override
                public PeerInfo createFromParcel(Parcel source)
                {
                    return new PeerInfo(source);
                }

                @Override
                public PeerInfo[] newArray(int size)
                {
                    return new PeerInfo[size];
                }
            };

    @Override
    public int compareTo(@NonNull Object another)
    {
        return ip.compareTo(((PeerInfo)another).ip);
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
        result = prime * result + downSpeed;
        result = prime * result + upSpeed;

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PeerInfo))
            return false;

        if (o == this)
            return true;

        PeerInfo state = (PeerInfo) o;

        return (ip == null || ip.equals(state.ip)) &&
                (client == null || client.equals(state.client)) &&
                totalDownload == state.totalDownload &&
                totalUpload == state.totalUpload &&
                relevance == state.relevance &&
                connectionType == state.connectionType &&
                port == state.port &&
                progress == state.progress &&
                downSpeed == state.downSpeed &&
                upSpeed == state.upSpeed;
    }

    @Override
    public String toString()
    {
        return "PeerInfo{" +
                "ip='" + ip + '\'' +
                ", client='" + client + '\'' +
                ", totalDownload=" + totalDownload +
                ", totalUpload=" + totalUpload +
                ", relevance=" + relevance +
                ", connectionType='" + connectionType + '\'' +
                ", port=" + port +
                ", progress=" + progress +
                ", downSpeed=" + downSpeed +
                ", upSpeed=" + upSpeed +
                '}';
    }
}
