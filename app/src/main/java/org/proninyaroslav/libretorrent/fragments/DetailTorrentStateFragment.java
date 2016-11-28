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

package org.proninyaroslav.libretorrent.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.utils.DateFormatUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

/*
 * The fragment for displaying state of torrent. Part of DetailTorrentFragment.
 */

public class DetailTorrentStateFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentStateFragment.class.getSimpleName();

    private static final String TAG_STATE = "state";
    private static final String TAG_INFO = "info";

    private AppCompatActivity activity;
    private TorrentStateParcel state;
    private TorrentMetaInfo info;

    public static DetailTorrentStateFragment newInstance(TorrentMetaInfo info) {
        DetailTorrentStateFragment fragment = new DetailTorrentStateFragment();

        fragment.setInfo(info);

        Bundle b = new Bundle();
        fragment.setArguments(b);

        return fragment;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            state = savedInstanceState.getParcelable(TAG_STATE);
            info = savedInstanceState.getParcelable(TAG_INFO);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_detail_torrent_state, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        reloadInfoView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putParcelable(TAG_STATE, state);
        outState.putParcelable(TAG_INFO, info);
    }

    public void setState(TorrentStateParcel state)
    {
        this.state = state;
        reloadInfoView();
    }

    public void setInfo(TorrentMetaInfo info)
    {
        this.info = info;
    }

    public void reloadInfoView()
    {
        if (activity == null || state == null || info == null) {
            return;
        }

        TextView downloadUploadSpeed = (TextView) activity.findViewById(R.id.torrent_state_speed);

        if (downloadUploadSpeed != null) {
            String speedTemplate = activity.getString(R.string.download_upload_speed_template);
            String downloadSpeed = Formatter.formatFileSize(activity, state.downloadSpeed);
            String uploadSpeed = Formatter.formatFileSize(activity, state.uploadSpeed);
            downloadUploadSpeed.setText(
                    String.format(speedTemplate, downloadSpeed, uploadSpeed));
        }

        TextView downloadCounter = (TextView) activity.findViewById(R.id.torrent_state_downloading);

        if (downloadCounter != null) {
            String counterTemplate = activity.getString(R.string.download_counter_template);
            String totalBytes = Formatter.formatFileSize(activity, state.totalBytes);
            String receivedBytes;
            if (state.progress == 100) {
                receivedBytes = totalBytes;
            } else {
                receivedBytes = Formatter.formatFileSize(activity, state.receivedBytes);
            }
            downloadCounter.setText(
                    String.format(
                            counterTemplate, receivedBytes,
                            totalBytes, state.progress));
        }

        TextView textViewETA = (TextView) activity.findViewById(R.id.torrent_state_ETA);

        if (textViewETA != null) {
            String ETA;
            if (state.ETA == -1 || state.ETA == 0) {
                ETA = Utils.INFINITY_SYMBOL;
            } else {
                ETA = DateFormatUtils.formatElapsedTime(
                        activity.getApplicationContext(), state.ETA);
            }
            textViewETA.setText(ETA);
        }

        TextView textViewSeeds = (TextView) activity.findViewById(R.id.torrent_state_seeds);

        if (textViewSeeds != null) {
            String seedsTemplate = activity.getString(R.string.torrent_peers_template);
            textViewSeeds.setText(
                    String.format(seedsTemplate, state.seeds, state.totalSeeds));
        }

        TextView textViewLeechers = (TextView) activity.findViewById(R.id.torrent_state_leechers);

        if (textViewLeechers != null) {
            String peersTemplate = activity.getString(R.string.torrent_peers_template);
            int leechers = state.peers - state.seeds;
            int totalLeechers = state.totalPeers - state.totalSeeds;
            textViewLeechers.setText(
                    String.format(peersTemplate, leechers, totalLeechers));
        }

        TextView textViewPieces = (TextView) activity.findViewById(R.id.torrent_state_pieces);

        if (textViewPieces != null) {
            String piecesTemplate = activity.getString(R.string.torrent_pieces_template);
            String pieceLehgtn = Formatter.formatFileSize(activity, info.getPieceLength());
            textViewPieces.setText(
                    String.format(piecesTemplate,
                            state.downloadedPieces,
                            info.getNumPieces(),
                            pieceLehgtn));
        }

        TextView textViewUploaded = (TextView) activity.findViewById(R.id.torrent_state_uploaded);

        if (textViewUploaded != null) {
            String uploaded = Formatter.formatFileSize(activity, state.uploadedBytes);
            textViewUploaded.setText(uploaded);
        }

        TextView shareRatio = (TextView) activity.findViewById(R.id.torrent_state_share_ratio);

        if (shareRatio != null) {
            shareRatio.setText(String.valueOf(state.shareRatio));
        }
    }

    public void setActiveAndSeedingTime(long activeTime, long seedingTime)
    {
        TextView textViewActiveTime = (TextView) activity.findViewById(R.id.torrent_state_active_time);

        if (textViewActiveTime != null) {
            textViewActiveTime.setText(
                    DateFormatUtils.formatElapsedTime(
                            activity.getApplicationContext(), activeTime));
        }

        TextView textViewSeedingTime = (TextView) activity.findViewById(R.id.torrent_state_seeding_time);

        if (textViewSeedingTime != null) {
            textViewSeedingTime.setText(
                    DateFormatUtils.formatElapsedTime(
                            activity.getApplicationContext(), seedingTime));
        }
    }
}