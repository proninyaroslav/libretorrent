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

package org.proninyaroslav.libretorrent.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.DownloadableFilesAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.FileTreeDepthFirstSearch;
import org.proninyaroslav.libretorrent.databinding.FragmentAddTorrentFilesBinding;
import org.proninyaroslav.libretorrent.viewmodel.AddTorrentViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import io.reactivex.disposables.CompositeDisposable;

/*
 * The fragment for list files of torrent. Part of AddTorrentFragment.
 */

public class AddTorrentFilesFragment extends Fragment
        implements
        DownloadableFilesAdapter.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFilesFragment.class.getSimpleName();

    private static final String TAG_LIST_FILES_STATE = "list_files_state";

    private AppCompatActivity activity;
    private FragmentAddTorrentFilesBinding binding;
    private AddTorrentViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private DownloadableFilesAdapter adapter;
    /* Save state scrolling */
    private Parcelable listFilesState;
    private CompositeDisposable disposable = new CompositeDisposable();

    public static AddTorrentFilesFragment newInstance()
    {
        AddTorrentFilesFragment fragment = new AddTorrentFilesFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(activity).get(AddTorrentViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_torrent_files, container, false);

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

        disposable.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
    }

    private void subscribeAdapter()
    {
        disposable.add(viewModel.children
                .subscribe((children) -> {
                    adapter.submitList(children);
                    updateFileSize();
                }));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        layoutManager = new LinearLayoutManager(activity);
        binding.fileList.setLayoutManager(layoutManager);
        adapter = new DownloadableFilesAdapter(this);
        binding.fileList.setAdapter(adapter);
    }

    private void updateFileSize()
    {
        if (viewModel.fileTree == null)
            return;

        binding.filesSize.setText(String.format(getString(R.string.files_size),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.selectedFileSize()),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.size())));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        listFilesState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            listFilesState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listFilesState != null)
            layoutManager.onRestoreInstanceState(listFilesState);
    }

    @Override
    public void onItemClicked(BencodeFileTree node)
    {
        if (node.getName().equals(BencodeFileTree.PARENT_DIR)) {
            viewModel.upToParentDirectory();
        } else if (node.getType() == FileNode.Type.DIR)
            viewModel.chooseDirectory(node);
    }

    @Override
    public void onItemCheckedChanged(BencodeFileTree node, boolean selected)
    {
        node.select(selected);
        updateFileSize();
    }
}
