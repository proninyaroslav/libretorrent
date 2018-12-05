/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
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

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of AddTorrentFragment.
 */

public class AddTorrentInfoFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentInfoFragment.class.getSimpleName();

    private static final String HEAVY_STATE_TAG = TAG + "_" + HeavyInstanceStorage.class.getSimpleName();
    private static final String TAG_INFO = "info";
    private static final String TAG_DOWNLOAD_DIR = "download_dir";
    private static final String TAG_CUSTOM_NAME = "custom_name";

    private static final int DIR_CHOOSER_REQUEST = 1;

    private AppCompatActivity activity;
    private String downloadDir = "";
    private TorrentMetaInfo info;
    private String customName;

    private EditText torrentNameField;
    private TextInputLayout layoutTorrentName;
    private TextView sha1HashView, commentView, createdByView,
            torrentSizeView, creationDateView, fileCountView,
            pathToUploadView, freeSpace;
    LinearLayout commentViewLayout, createdByViewLayout,
            sizeAndCountViewLayout, creationDateViewLayout;
    private ImageButton folderChooserButton;
    private CheckBox sequentialDownload, startTorrent;

    public static AddTorrentInfoFragment newInstance()
    {
        AddTorrentInfoFragment fragment = new AddTorrentInfoFragment();

        Bundle b = new Bundle();
        fragment.setArguments(b);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(getFragmentManager());
        if (storage != null) {
            Bundle heavyInstance = storage.popData(HEAVY_STATE_TAG);
            if (heavyInstance != null)
                info = heavyInstance.getParcelable(TAG_INFO);
        }
        if (savedInstanceState != null) {
            downloadDir = savedInstanceState.getString(TAG_DOWNLOAD_DIR);
            customName = savedInstanceState.getString(TAG_CUSTOM_NAME);
        } else {
            downloadDir = TorrentUtils.getTorrentDownloadPath(activity.getApplicationContext());
        }
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_add_torrent_info, container, false);

        torrentNameField = v.findViewById(R.id.torrent_name);
        layoutTorrentName = v.findViewById(R.id.layout_torrent_name);
        sha1HashView = v.findViewById(R.id.torrent_hash_sum);
        commentView = v.findViewById(R.id.torrent_comment);
        createdByView = v.findViewById(R.id.torrent_created_in_program);
        torrentSizeView = v.findViewById(R.id.torrent_size);
        fileCountView = v.findViewById(R.id.torrent_file_count);
        creationDateView = v.findViewById(R.id.torrent_create_date);
        pathToUploadView = v.findViewById(R.id.upload_torrent_into);
        folderChooserButton = v.findViewById(R.id.folder_chooser_button);
        sequentialDownload = v.findViewById(R.id.sequential_download);
        startTorrent = v.findViewById(R.id.start_torrent);
        startTorrent.setChecked(true);
        freeSpace = v.findViewById(R.id.free_space);
        commentViewLayout = v.findViewById(R.id.layout_torrent_comment);
        commentViewLayout.setVisibility(View.GONE);
        createdByViewLayout = v.findViewById(R.id.layout_torrent_created_in_program);
        createdByViewLayout.setVisibility(View.GONE);
        sizeAndCountViewLayout = v.findViewById(R.id.layout_torrent_size_and_count);
        sizeAndCountViewLayout.setVisibility(View.GONE);
        creationDateViewLayout = v.findViewById(R.id.layout_torrent_create_date);
        creationDateViewLayout.setVisibility(View.GONE);

        initFields();

        return v;
    }

    private void initFields()
    {
        if (info == null)
            return;

        torrentNameField.setText(TextUtils.isEmpty(customName) ? info.torrentName : customName);
        sha1HashView.setText(info.sha1Hash);
        pathToUploadView.setText(downloadDir);

        if (TextUtils.isEmpty(info.comment)) {
            commentViewLayout.setVisibility(View.GONE);
        } else {
            commentView.setText(info.comment);
            commentViewLayout.setVisibility(View.VISIBLE);
        }

        if (TextUtils.isEmpty(info.createdBy)) {
            createdByViewLayout.setVisibility(View.GONE);
        } else {
            createdByView.setText(info.createdBy);
            createdByViewLayout.setVisibility(View.VISIBLE);
        }

        if (info.torrentSize == 0 || info.fileCount == 0) {
            sizeAndCountViewLayout.setVisibility(View.GONE);
        } else {
            torrentSizeView.setText(Formatter.formatFileSize(activity, info.torrentSize));
            fileCountView.setText(String.format(Locale.getDefault(), "%d", info.fileCount));
            freeSpace.setText(String.format(getString(R.string.free_space),
                              Formatter.formatFileSize(activity.getApplicationContext(),
                                                       FileIOUtils.getFreeSpace(downloadDir))));
            sizeAndCountViewLayout.setVisibility(View.VISIBLE);
        }

        if (info.creationDate == 0) {
            creationDateViewLayout.setVisibility(View.GONE);
        } else {
            creationDateView.setText(SimpleDateFormat.getDateTimeInstance()
                    .format(new Date(info.creationDate)));
            creationDateViewLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putString(TAG_DOWNLOAD_DIR, downloadDir);
        outState.putString(TAG_CUSTOM_NAME, customName);

        Bundle b = new Bundle();
        b.putParcelable(TAG_INFO, info);
        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(getFragmentManager());
        if (storage != null)
            storage.pushData(HEAVY_STATE_TAG, b);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        folderChooserButton.setOnClickListener((View v) -> {
            Intent i = new Intent(activity, FileManagerDialog.class);
            FileManagerConfig config = new FileManagerConfig(downloadDir,
                    null,
                    null,
                    FileManagerConfig.DIR_CHOOSER_MODE);
            i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            startActivityForResult(i, DIR_CHOOSER_REQUEST);
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
                checkEditTextField(s);
                String name = s.toString();
                if (info != null && name.equals(info.torrentName))
                    return;
                customName = name;
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
                    downloadDir = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);
                    pathToUploadView.setText(downloadDir);
                    freeSpace.setText(
                            String.format(getString(R.string.free_space),
                                          Formatter.formatFileSize(activity.getApplicationContext(),
                                                                   FileIOUtils.getFreeSpace(downloadDir))));
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
        } else {
            layoutTorrentName.setErrorEnabled(false);
            layoutTorrentName.setError(null);
        }
    }

    public void setInfo(TorrentMetaInfo info)
    {
        if (info == null)
            return;

        this.info = info;

        initFields();
    }

    public String getDownloadDir()
    {
        return downloadDir;
    }

    public String getTorrentName()
    {
        return torrentNameField.getText().toString();
    }

    public boolean isSequentialDownload()
    {
        return sequentialDownload.isChecked();
    }

    public boolean startTorrent()
    {
        return startTorrent.isChecked();
    }
}