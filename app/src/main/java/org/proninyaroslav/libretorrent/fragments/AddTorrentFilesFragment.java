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
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.DownloadableFilesAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;

import java.util.ArrayList;
import java.util.List;

/*
 * The fragment for list files of torrent. Part of AddTorrentFragment.
 */

public class AddTorrentFilesFragment extends Fragment
        implements
        DownloadableFilesAdapter.ViewHolder.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFilesFragment.class.getSimpleName();

    private static final String TAG_FILES = "files";
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

    private BencodeFileTree fileTree;
    /* Current directory */
    private BencodeFileTree curDir;

    public static AddTorrentFilesFragment newInstance(ArrayList<BencodeFileItem> files) {
        AddTorrentFilesFragment fragment = new AddTorrentFilesFragment();

        Bundle args = new Bundle();
        args.putParcelableArrayList(TAG_FILES, files);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_add_torrent_files, container, false);
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
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        if (savedInstanceState != null) {
            fileTree = (BencodeFileTree) savedInstanceState.getSerializable(TAG_FILE_TREE);
            curDir = (BencodeFileTree) savedInstanceState.getSerializable(TAG_CUR_DIR);
        } else {
            ArrayList<BencodeFileItem> files = getArguments().getParcelableArrayList(TAG_FILES);
            fileTree = BencodeFileTreeUtils.buildFileTree(files);
            fileTree.select(true);
            /* Is assigned the root dir of the file tree */
            curDir = fileTree;
        }

        filesSize = (TextView) activity.findViewById(R.id.files_size);
        filesSize.setText(
                String.format(getString(R.string.files_size),
                        Formatter.formatFileSize(activity.getApplicationContext(),
                                fileTree.selectedFileSize()),
                        Formatter.formatFileSize(activity.getApplicationContext(),
                                fileTree.size())));

        fileList = (RecyclerView) activity.findViewById(R.id.file_list);
        layoutManager = new LinearLayoutManager(activity);
        fileList.setLayoutManager(layoutManager);
        fileList.setItemAnimator(new DefaultItemAnimator());
        adapter = new DownloadableFilesAdapter(getChildren(curDir), activity,
                R.layout.item_torrent_downloadable_file, this);
        fileList.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putSerializable(TAG_FILE_TREE, fileTree);
        outState.putSerializable(TAG_CUR_DIR, curDir);
        listFilesState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listFilesState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (listFilesState != null) {
            layoutManager.onRestoreInstanceState(listFilesState);
        }
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

        filesSize.setText(
                String.format(getString(R.string.files_size),
                        Formatter.formatFileSize(activity.getApplicationContext(),
                                fileTree.selectedFileSize()),
                        Formatter.formatFileSize(activity.getApplicationContext(),
                                fileTree.size())));
    }

    private List<BencodeFileTree> getChildren(BencodeFileTree node)
    {
        List<BencodeFileTree> children = new ArrayList<BencodeFileTree>();

        if (node.isFile()) {
            return children;
        }

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null) {
            children.add(0, new BencodeFileTree(BencodeFileTree.PARENT_DIR, 0L, FileNode.Type.DIR, curDir.getParent()));
        }

        children.addAll(curDir.getChildren());

        return children;
    }

    private void chooseDirectory(BencodeFileTree node)
    {
        if (node.isFile()) {
            node = fileTree;
        }

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
        if (children.size() == 0) {
            adapter.notifyDataSetChanged();
        } else {
            adapter.addFiles(children);
        }
    }

    public ArrayList<Integer> getSelectedFileIndexes()
    {
        List<BencodeFileTree> files = BencodeFileTreeUtils.getFiles(fileTree);
        ArrayList<Integer> indexes = new ArrayList<>();
        for (BencodeFileTree file : files) {
            if (file.isSelected()) {
                indexes.add(file.getIndex());
            }
        }

        return indexes;
    }

    public long getSelectedFileSize()
    {
        return fileTree.selectedFileSize();
    }
}
