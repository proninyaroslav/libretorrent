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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.DownloadableFilesAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.utils.FileTreeDepthFirstSearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * The fragment for list files of torrent. Part of AddTorrentFragment.
 */

public class AddTorrentFilesFragment extends Fragment
        implements
        DownloadableFilesAdapter.ViewHolder.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFilesFragment.class.getSimpleName();

    private static final String HEAVY_STATE_TAG = TAG + "_" + HeavyInstanceStorage.class.getSimpleName();
    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String TAG_FILE_TREE = "file_tree";
    private static final String TAG_CUR_DIR = "cur_dir";

    private AppCompatActivity activity;
    private RecyclerView fileList;
    private LinearLayoutManager layoutManager;
    private DownloadableFilesAdapter adapter;
    /* Save state scrolling */
    private Parcelable listFilesState;
    private TextView filesSize;

    private ArrayList<BencodeFileItem> files;
    private List<Priority> priorities;
    private BencodeFileTree fileTree;
    /* Current directory */
    private BencodeFileTree curDir;

    public static AddTorrentFilesFragment newInstance()
    {
        AddTorrentFilesFragment fragment = new AddTorrentFilesFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_add_torrent_files, container, false);

        filesSize = v.findViewById(R.id.files_size);
        fileList = v.findViewById(R.id.file_list);

        return v;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(getFragmentManager());
        if (storage != null) {
            Bundle heavyInstance = storage.popData(HEAVY_STATE_TAG);
            if (heavyInstance != null) {
                fileTree = (BencodeFileTree)heavyInstance.getSerializable(TAG_FILE_TREE);
                curDir = (BencodeFileTree)heavyInstance.getSerializable(TAG_CUR_DIR);
            }
        }

        updateFileSize();

        layoutManager = new LinearLayoutManager(activity);
        fileList.setLayoutManager(layoutManager);
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
        fileList.setItemAnimator(animator);
        adapter = new DownloadableFilesAdapter(getChildren(curDir), activity,
                R.layout.item_torrent_downloadable_file, this);
        fileList.setAdapter(adapter);
    }

    private void makeFileTree()
    {
        if (files == null)
            return;

        fileTree = BencodeFileTreeUtils.buildFileTree(files);

        if (priorities == null || priorities.size() == 0) {
            fileTree.select(true);

        } else {
            FileTreeDepthFirstSearch<BencodeFileTree> search = new FileTreeDepthFirstSearch<>();
            /* Select files that have non-IGNORE priority (see BEP35 standard) */
            long n = (priorities.size() > files.size() ? files.size() : priorities.size());
            for (int i = 0; i < n; i++) {
                if (priorities.get(i) == Priority.IGNORE)
                    continue;
                BencodeFileTree file = search.find(fileTree, i);
                if (file != null)
                    file.select(true);
            }
        }

        /* Is assigned the root dir of the file tree */
        curDir = fileTree;
    }

    private void updateFileSize()
    {
        if (fileTree == null)
            return;

        filesSize.setText(String.format(getString(R.string.files_size),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        fileTree.selectedFileSize()),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        fileTree.size())));
    }

    public void setFilesAndPriorities(ArrayList<BencodeFileItem> files,
                                      List<Priority> priorities)
    {
        if (files == null)
            return;

        this.files = files;
        this.priorities = priorities;

        makeFileTree();
        updateFileSize();
        reloadData();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        listFilesState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);

        Bundle b = new Bundle();
        b.putSerializable(TAG_FILE_TREE, fileTree);
        b.putSerializable(TAG_CUR_DIR, curDir);
        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(getFragmentManager());
        if (storage != null)
            storage.pushData(HEAVY_STATE_TAG, b);
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
            backToParent();

            return;
        }

        if (node.getType() == FileNode.Type.DIR) {
            chooseDirectory(node);
            reloadData();
        }
    }

    @Override
    public void onItemCheckedChanged(BencodeFileTree node, boolean selected)
    {
        node.select(selected);
        updateFileSize();
    }

    private List<BencodeFileTree> getChildren(BencodeFileTree node)
    {
        List<BencodeFileTree> children = new ArrayList<>();
        if (node == null || node.isFile())
            return children;

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null)
            children.add(0, new BencodeFileTree(BencodeFileTree.PARENT_DIR, 0L, FileNode.Type.DIR, curDir.getParent()));

        children.addAll(curDir.getChildren());

        return children;
    }

    private void chooseDirectory(BencodeFileTree node)
    {
        if (node.isFile())
            node = fileTree;

        curDir = node;
    }

    /*
     * Navigate back to an upper directory.
     */

    private void backToParent()
    {
        curDir = curDir.getParent();
        reloadData();
    }

    final synchronized void reloadData()
    {
        adapter.clearFiles();

        List<BencodeFileTree> children = getChildren(curDir);
        if (children.size() == 0)
            adapter.notifyDataSetChanged();
        else
            adapter.addFiles(children);
    }

    public Set<Integer> getSelectedFileIndexes()
    {
        if (fileTree == null)
            return new HashSet<Integer>();

        List<BencodeFileTree> files = BencodeFileTreeUtils.getFiles(fileTree);
        Set<Integer> indexes = new ArraySet<>();
        for (BencodeFileTree file : files)
            if (file.isSelected())
                indexes.add(file.getIndex());

        return indexes;
    }

    public long getSelectedFileSize()
    {
        return (fileTree != null ? fileTree.selectedFileSize() : 0);
    }
}
