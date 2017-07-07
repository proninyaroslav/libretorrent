/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.customviews.PiecesView;

/*
 * The fragment for displaying torrent pieces. Part of DetailTorrentFragment.
 */

public class DetailTorrentPiecesFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentPiecesFragment.class.getSimpleName();

    private static final String TAG_PIECES = "pieces";
    private static final String TAG_ALL_PIECES_COUNT = "all_pieces_count";
    private static final String TAG_PIECE_SIZE = "piece_size";
    private static final String TAG_DOWNLOADED_PIECES = "downloaded_pieces";

    private AppCompatActivity activity;
    private PiecesView pieceMap;
    private TextView piecesCounter;

    private boolean[] pieces;
    private int allPiecesCount;
    private int pieceSize;
    private int downloadedPieces;

    public static DetailTorrentPiecesFragment newInstance(int allPiecesCount, int pieceSize) {
        DetailTorrentPiecesFragment fragment = new DetailTorrentPiecesFragment();

        fragment.allPiecesCount = allPiecesCount;
        fragment.pieceSize = pieceSize;

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            this.activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            pieces = savedInstanceState.getBooleanArray(TAG_PIECES);
            allPiecesCount = savedInstanceState.getInt(TAG_ALL_PIECES_COUNT);
            pieceSize = savedInstanceState.getInt(TAG_PIECE_SIZE);
            downloadedPieces = savedInstanceState.getInt(TAG_DOWNLOADED_PIECES);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_detail_torrent_pieces, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        pieceMap = (PiecesView) activity.findViewById(R.id.piece_map);
        if (pieceMap != null) {
            pieceMap.setPieces(pieces);
        }

        piecesCounter = (TextView) activity.findViewById(R.id.pieces_count);
        if (pieceMap != null) {
            updatePieceCounter();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putBooleanArray(TAG_PIECES, pieces);
        outState.putInt(TAG_ALL_PIECES_COUNT, allPiecesCount);
        outState.putInt(TAG_PIECE_SIZE, pieceSize);
        outState.putInt(TAG_DOWNLOADED_PIECES, downloadedPieces);

        super.onSaveInstanceState(outState);
    }

    public void setPieces(boolean[] pieces)
    {
        if (pieces == null) {
            return;
        }

        this.pieces = pieces;

        if (pieceMap != null) {
            pieceMap.setPieces(pieces);
        }
    }

    public void setPiecesCountAndSize(int allPiecesCount, int pieceSize)
    {
        this.allPiecesCount = allPiecesCount;
        this.pieceSize = pieceSize;

        updatePieceCounter();
    }

    public void setDownloadedPiecesCount(int downloadedPieces)
    {
        this.downloadedPieces = downloadedPieces;

        updatePieceCounter();
    }

    private void updatePieceCounter()
    {
        String piecesTemplate = activity.getString(R.string.torrent_pieces_template);
        String pieceLength = Formatter.formatFileSize(activity, pieceSize);
        if (piecesCounter != null) {
            piecesCounter.setText(
                    String.format(piecesTemplate,
                            downloadedPieces,
                            allPiecesCount,
                            pieceLength));
        }
    }
}
