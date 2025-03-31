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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages;

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
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsStateBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsViewModel;

/*
 * The fragment for displaying torrent state. Part of TorrentDetailsFragment.
 */

public class TorrentDetailsStateFragment extends Fragment {
    private AppCompatActivity activity;
    private FragmentTorrentDetailsStateBinding binding;

    public static TorrentDetailsStateFragment newInstance() {
        var fragment = new TorrentDetailsStateFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_torrent_details_state, container, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.nestedScrollView,
                    WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.nestedScrollView,
                    WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        var viewModel = new ViewModelProvider(requireParentFragment())
                .get(TorrentDetailsViewModel.class);
        binding.setViewModel(viewModel);
    }
}