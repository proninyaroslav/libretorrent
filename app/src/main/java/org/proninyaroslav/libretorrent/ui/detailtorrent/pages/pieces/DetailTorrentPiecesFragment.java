/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.pieces;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.FragmentDetailTorrentPiecesBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentViewModel;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying torrent pieces. Part of DetailTorrentFragment.
 */

public class DetailTorrentPiecesFragment extends Fragment
{
    private static final String TAG = DetailTorrentPiecesFragment.class.getSimpleName();

    private static final String TAG_SCROLL_POSITION = "scroll_position";

    private AppCompatActivity activity;
    private FragmentDetailTorrentPiecesBinding binding;
    private DetailTorrentViewModel viewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private int[] scrollPosition = new int[]{0, 0};

    public static DetailTorrentPiecesFragment newInstance()
    {
        DetailTorrentPiecesFragment fragment = new DetailTorrentPiecesFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail_torrent_pieces, container, false);

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        viewModel = new ViewModelProvider(activity).get(DetailTorrentViewModel.class);
        binding.setViewModel(viewModel);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        if (scrollPosition != null) {
            scrollPosition[0] = binding.pieceMapScrollView.getScrollX();
            scrollPosition[1] = binding.pieceMapScrollView.getScrollY();
            outState.putIntArray(TAG_SCROLL_POSITION, scrollPosition);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            scrollPosition = savedInstanceState.getIntArray(TAG_SCROLL_POSITION);
            if (scrollPosition != null && scrollPosition.length == 2)
                binding.pieceMapScrollView.scrollTo(scrollPosition[0], scrollPosition[1]);
        }
    }

    private void subscribeAdapter()
    {
        disposables.add(viewModel.observePieces()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((pieces) -> binding.pieceMap.setPieces(pieces)));
    }
}
