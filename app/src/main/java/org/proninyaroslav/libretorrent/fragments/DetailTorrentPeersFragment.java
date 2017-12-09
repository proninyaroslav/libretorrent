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
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.PeerListAdapter;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.EmptyRecyclerView;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;

import java.util.ArrayList;

/*
 * The fragment for displaying bittorrent peer list. Part of DetailTorrentFragment.
 */

public class DetailTorrentPeersFragment extends Fragment
        implements
        PeerListAdapter.ViewHolder.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentPeersFragment.class.getSimpleName();

    private static final String TAG_PEER_LIST = "peer_list";
    private static final String TAG_LIST_PEER_STATE = "list_tracker_state";

    private AppCompatActivity activity;
    private EmptyRecyclerView peerList;
    /* Save state scrolling */
    private Parcelable listPeerState;
    private PeerListAdapter adapter;
    private LinearLayoutManager layoutManager;

    private ArrayList<PeerStateParcel> peers = new ArrayList<>();

    public static DetailTorrentPeersFragment newInstance() {
        DetailTorrentPeersFragment fragment = new DetailTorrentPeersFragment();

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
            peers = savedInstanceState.getParcelableArrayList(TAG_PEER_LIST);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_detail_torrent_peer_list, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        peerList = (EmptyRecyclerView) activity.findViewById(R.id.peer_list);
        if (peerList != null) {
            layoutManager = new LinearLayoutManager(activity);
            peerList.setLayoutManager(layoutManager);
            peerList.setEmptyView(activity.findViewById(R.id.empty_view_peer_list));

            /*
             * A RecyclerView by default creates another copy of the ViewHolder in order to
             * fade the views into each other. This causes the problem because the old ViewHolder gets
             * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
             */
            DefaultItemAnimator animator = new DefaultItemAnimator()
            {
                @Override
                public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder)
                {
                    return true;
                }
            };

            int resId = R.drawable.list_divider;
            if (Utils.isDarkTheme(activity.getApplicationContext()) || Utils.isBlackTheme(activity.getApplicationContext())) {
                resId = R.drawable.list_divider_dark;
            }

            peerList.setItemAnimator(animator);
            peerList.addItemDecoration(
                    new RecyclerViewDividerDecoration(
                            activity.getApplicationContext(), resId));

            adapter = new PeerListAdapter(peers, activity, R.layout.item_peers_list, this);
            peerList.setAdapter(adapter);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        if (layoutManager != null) {
            listPeerState = layoutManager.onSaveInstanceState();
        }
        outState.putParcelable(TAG_LIST_PEER_STATE, listPeerState);
        outState.putParcelableArrayList(TAG_PEER_LIST, peers);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listPeerState = savedInstanceState.getParcelable(TAG_LIST_PEER_STATE);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listPeerState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(listPeerState);
        }
    }

    public void setPeerList(ArrayList<PeerStateParcel> peers)
    {
        if (peers.isEmpty()) {
            adapter.clearAll();
            return;
        }

        this.peers = peers;

        if (adapter.isEmpty()) {
            adapter.addItems(peers);
        } else {
            adapter.updateItems(peers);
        }
    }

    @Override
    public boolean onItemLongClicked(int position, PeerStateParcel state)
    {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "ip");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, state.ip);
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));

        return true;
    }
}
