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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.*;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of DetailTorrentFragment.
 */

public class DetailTorrentInfoFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentInfoFragment.class.getSimpleName();

    private static final String TAG_INFO = "info";
    private static final String TAG_TORRENT= "torrent";

    private static final int DIR_CHOOSER_REQUEST = 1;

    private AppCompatActivity activity;
    private DetailTorrentFragment.Callback callback;

    private EditText torrentNameField;
    private TextInputLayout layoutTorrentName;
    private TextView sha1HashView, commentView, createdByView,
            torrentSizeView, creationDateView, fileCountView,
            pathToUploadView, freeSpace;
    private ImageButton folderChooserButton;
    private CheckBox sequentialDownload;

    public static DetailTorrentInfoFragment newInstance(Torrent torrent, TorrentMetaInfo info)
    {
        DetailTorrentInfoFragment fragment = new DetailTorrentInfoFragment();

        Bundle args = new Bundle();
        args.putParcelable(TAG_TORRENT, torrent);
        args.putParcelable(TAG_INFO, info);
        fragment.setArguments(args);

        return fragment;
    }

    /* For API < 23 */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        if (activity instanceof AppCompatActivity) {
            this.activity = (AppCompatActivity) activity;

            if (activity instanceof DetailTorrentFragment.Callback) {
                callback = (DetailTorrentFragment.Callback) activity;
            }
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_detail_torrent_info, container, false);

        torrentNameField = (EditText) v.findViewById(R.id.torrent_name);
        layoutTorrentName = (TextInputLayout) v.findViewById(R.id.layout_torrent_name);
        sha1HashView = (TextView) v.findViewById(R.id.torrent_hash_sum);
        commentView = (TextView) v.findViewById(R.id.torrent_comment);
        createdByView = (TextView) v.findViewById(R.id.torrent_created_in_program);
        torrentSizeView = (TextView) v.findViewById(R.id.torrent_size);
        fileCountView = (TextView) v.findViewById(R.id.torrent_file_count);
        creationDateView = (TextView) v.findViewById(R.id.torrent_create_date);
        pathToUploadView = (TextView) v.findViewById(R.id.upload_torrent_into);
        folderChooserButton = (ImageButton) v.findViewById(R.id.folder_chooser_button);
        sequentialDownload = (CheckBox) v.findViewById(R.id.sequential_download);
        freeSpace = (TextView) v.findViewById(R.id.free_space);
        sequentialDownload.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                if (callback != null) {
                    callback.onTorrentInfoChanged();
                }
            }
        });

        Torrent torrent = getArguments().getParcelable(TAG_TORRENT);
        TorrentMetaInfo info = getArguments().getParcelable(TAG_INFO);

        if (info != null && torrent != null) {
            torrentNameField.setText(torrent.getName());
            sha1HashView.setText(info.getSha1Hash());
            if (TextUtils.isEmpty(info.getComment())) {
                commentView.setVisibility(View.GONE);

                LinearLayout commentViewLayout =
                        (LinearLayout) v.findViewById(R.id.layout_torrent_comment);
                commentViewLayout.setVisibility(View.GONE);

                TextView commentViewHeader = (TextView) v.findViewById(R.id.header_torrent_comment);
                commentViewHeader.setVisibility(View.GONE);
            } else {
                commentView.setText(info.getComment());
            }
            if (TextUtils.isEmpty(info.getCreatedBy())) {
                createdByView.setVisibility(View.GONE);

                LinearLayout createdByViewLayout =
                        (LinearLayout) v.findViewById(R.id.layout_torrent_created_in_program);
                createdByViewLayout.setVisibility(View.GONE);

                TextView createdByViewHeader =
                        (TextView) v.findViewById(R.id.header_torrent_created_in_program);
                createdByViewHeader.setVisibility(View.GONE);
            } else {
                createdByView.setText(info.getCreatedBy());
            }
            torrentSizeView.setText(Formatter.formatFileSize(activity, info.getTorrentSize()));
            creationDateView.setText(
                    SimpleDateFormat.getDateTimeInstance().format(new Date(info.getCreationDate())));
            fileCountView.setText(Integer.toString(info.getFileCount()));
            pathToUploadView.setText(torrent.getDownloadPath());
            sequentialDownload.setChecked(torrent.isSequentialDownload());
            freeSpace.setText(
                    String.format(
                            getString(R.string.free_space),
                            Formatter.formatFileSize(activity.getApplicationContext(),
                                    FileIOUtils.getFreeSpace(torrent.getDownloadPath()))));
        }

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        folderChooserButton.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v) {
                Torrent torrent = getArguments().getParcelable(TAG_TORRENT);
                if (torrent != null) {
                    Intent i = new Intent(activity, FileManagerDialog.class);

                    FileManagerConfig config = new FileManagerConfig(torrent.getDownloadPath(),
                            null,
                            null,
                            FileManagerConfig.DIR_CHOOSER_MODE);

                    i.putExtra(FileManagerDialog.TAG_CONFIG, config);

                    startActivityForResult(i, DIR_CHOOSER_REQUEST);
                }
            }
        });

        torrentNameField.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
                checkEditTextField(s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                /* Nothing */
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                if (torrentNameField.isFocused()) {
                    if (callback != null) {
                        callback.onTorrentInfoChanged();
                    }
                }

                checkEditTextField(s);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIR_CHOOSER_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                    pathToUploadView.setText(data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH));

                    if (callback != null) {
                        callback.onTorrentInfoChanged();
                    }
                }
            }
        }
    }

    private void checkEditTextField(CharSequence s)
    {
        if (TextUtils.isEmpty(s)) {
            layoutTorrentName.setErrorEnabled(true);
            layoutTorrentName.setError(getString(R.string.error_field_required));
            layoutTorrentName.requestFocus();
            if (callback != null) {
                callback.onTorrentInfoChangesUndone();
            }
        } else {
            layoutTorrentName.setErrorEnabled(false);
            layoutTorrentName.setError(null);
        }
    }

    public String getDownloadPath()
    {
        return pathToUploadView.getText().toString();
    }

    public void setDownloadPath(String path)
    {
        if (path == null) {
            return;
        }

        pathToUploadView.setText(path);
        freeSpace.setText(
                String.format(
                        getString(R.string.free_space),
                        Formatter.formatFileSize(activity.getApplicationContext(),
                                FileIOUtils.getFreeSpace(path))));
    }

    public String getTorrentName()
    {
        return torrentNameField.getText().toString();
    }

    public void setTorrentName(String name)
    {
        if (name == null) {
            return;
        }

        torrentNameField.setText(name);
    }

    public boolean isSequentialDownload()
    {
        return sequentialDownload.isChecked();
    }

    public void setSequentialDownload(boolean sequential)
    {
        sequentialDownload.setChecked(sequential);
    }
}