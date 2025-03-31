/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.peers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsPeersBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsViewModel;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying bittorrent peer list. Part of DetailTorrentFragment.
 */

public class TorrentDetailsPeersFragment extends Fragment
        implements PeerListAdapter.ClickListener {
    private static final String TAG_LIST_TRACKER_STATE = "list_tracker_state";

    private AppCompatActivity activity;
    private FragmentTorrentDetailsPeersBinding binding;
    private TorrentDetailsViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private PeerListAdapter adapter;
    /* Save state scrolling */
    private Parcelable listPeerState;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static TorrentDetailsPeersFragment newInstance() {
        var fragment = new TorrentDetailsPeersFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTorrentDetailsPeersBinding.inflate(inflater, container, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.peerList,
                    WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.peerList,
                    WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        viewModel = new ViewModelProvider(requireParentFragment())
                .get(TorrentDetailsViewModel.class);

        layoutManager = new LinearLayoutManager(activity);
        binding.peerList.setLayoutManager(layoutManager);
        binding.peerList.setEmptyView(binding.emptyViewPeerList);
        adapter = new PeerListAdapter(this);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        };
        binding.peerList.setItemAnimator(animator);
        binding.peerList.addItemDecoration(Utils.buildListDivider(activity));
        binding.peerList.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            listPeerState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_LIST_TRACKER_STATE, listPeerState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listPeerState = savedInstanceState.getParcelable(TAG_LIST_TRACKER_STATE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (listPeerState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(listPeerState);
        }
    }

    private void subscribeAdapter() {
        disposables.add(viewModel.observePeers()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((children) ->
                        Flowable.fromIterable(children)
                                .map(PeerItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((children) -> adapter.submitList(children)));
    }

    @Override
    public boolean onItemLongClick(@NonNull PeerItem item) {
        sharePeerIp(item.ip);

        return true;
    }

    private void sharePeerIp(String ip) {
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "ip");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, ip);

        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }
}
