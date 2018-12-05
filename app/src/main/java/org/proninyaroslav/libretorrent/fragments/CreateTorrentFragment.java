/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.libtorrent4j.Pair;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentBuilder;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.CreateTorrentActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.RequestPermissions;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.CreateTorrentParams;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateTorrentFragment extends Fragment
    implements BaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = CreateTorrentFragment.class.getSimpleName();

    private static final String TAG_PATH_TO_FILE_OR_DIR = "path_to_file_or_dir";
    private static final String TAG_PIECE_SIZE = "piece_size";
    private static final String TAG_CREATE_PARAMS = "create_params";
    private static final String TAG_ERROR_FILE_OR_FOLDER_NOT_FOUND = "error_file_or_folder_not_found";
    private static final String TAG_ERROR_FOLDER_IS_EMPTY = "error_folder_is_empty";
    private static final String TAG_IO_ERROR = "io_error";

    private static final int PERMISSION_REQUEST = 1;
    private static final int CHOOSE_FILE_REQUEST = 2;
    private static final int CHOOSE_DIR_REQUEST = 3;
    private static final int CHOOSE_PATH_TO_SAVE_REQUEST = 4;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private CoordinatorLayout coordinatorLayout;
    private TextInputEditText trackersEditText, webSeedsEditText,
            commentsEditText, skipFilesEditText;
    private TextInputLayout trackersLayout, webSeedsLayout;
    private TextView pathToFileOrDirView;
    private ImageButton fileChooserButton, folderChooserButton;
    private Spinner pieceSizeSpinner;
    private CheckBox startSeedingOption, isPrivateOption, optimizeAlignmentOption;
    private ProgressBar buildProgress;
    private String pathToFileOrDir;
    /* In bytes */
    private int pieceSize = 0;
    private CreateTorrentParams createParams;
    private BuildTorrentTask decodeTask;
    private Exception sentError;

    public static CreateTorrentFragment newInstance()
    {
        CreateTorrentFragment fragment = new CreateTorrentFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        /* Retain this fragment across configuration changes */
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_create_torrent, container, false);
        coordinatorLayout = v.findViewById(R.id.coordinator_layout);
        skipFilesEditText = v.findViewById(R.id.skip_files);
        trackersEditText = v.findViewById(R.id.tracker_urls);
        webSeedsEditText = v.findViewById(R.id.web_seed_urls);
        commentsEditText = v.findViewById(R.id.comments);
        trackersLayout = v.findViewById(R.id.layout_tracker_urls);
        webSeedsLayout = v.findViewById(R.id.layout_web_seed_urls);
        pathToFileOrDirView = v.findViewById(R.id.file_or_dir_path);
        fileChooserButton = v.findViewById(R.id.file_chooser_button);
        folderChooserButton = v.findViewById(R.id.folder_chooser_button);
        pieceSizeSpinner = v.findViewById(R.id.pieces_size);
        startSeedingOption = v.findViewById(R.id.option_start_seeding);
        isPrivateOption = v.findViewById(R.id.option_private_torrent);
        optimizeAlignmentOption = v.findViewById(R.id.option_optimize_alignment);
        buildProgress = v.findViewById(R.id.build_progress);

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
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        Utils.showColoredStatusBar_KitKat(activity);

        toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null)
            toolbar.setTitle(R.string.create_torrent);
        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            pathToFileOrDir = savedInstanceState.getString(TAG_PATH_TO_FILE_OR_DIR);
            pieceSize = savedInstanceState.getInt(TAG_PIECE_SIZE);
            createParams = savedInstanceState.getParcelable(TAG_CREATE_PARAMS);
        } else {
            if (Utils.checkStoragePermission(activity.getApplicationContext()))
                startActivityForResult(new Intent(activity, RequestPermissions.class), PERMISSION_REQUEST);
            pathToFileOrDir = FileIOUtils.getUserDirPath();
        }

        pathToFileOrDirView.setText(pathToFileOrDir);
        optimizeAlignmentOption.setChecked(true);
        if (decodeTask != null && decodeTask.getStatus() != BuildTorrentTask.Status.FINISHED)
            buildProgress.setVisibility(View.VISIBLE);
        else
            buildProgress.setVisibility(View.GONE);
        pieceSizeSpinner.setSelection(pieceSize);
        pieceSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                int index = pieceSizeSpinner.getSelectedItemPosition();
                pieceSize = TorrentEngine.pieceSize[index] * 1024;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });

        fileChooserButton.setOnClickListener((View v) -> {
            Intent i = new Intent(activity, FileManagerDialog.class);
            FileManagerConfig config = new FileManagerConfig(
                    FileIOUtils.getUserDirPath(),
                    null,
                    null,
                    FileManagerConfig.FILE_CHOOSER_MODE);
            i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            startActivityForResult(i, CHOOSE_FILE_REQUEST);
        });

        folderChooserButton.setOnClickListener((View v) -> {
            Intent i = new Intent(activity, FileManagerDialog.class);
            FileManagerConfig config = new FileManagerConfig(
                    FileIOUtils.getUserDirPath(),
                    null,
                    null,
                    FileManagerConfig.DIR_CHOOSER_MODE);
            i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            startActivityForResult(i, CHOOSE_DIR_REQUEST);
        });

        /* Dismiss error label if user has changed the text */
        trackersEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                trackersLayout.setErrorEnabled(false);
                trackersLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) { /* Nothing */ }
        });

        webSeedsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                webSeedsLayout.setErrorEnabled(false);
                webSeedsLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) { /* Nothing */ }
        });
    }

    private void startBuildTask(final CreateTorrentParams createParams)
    {
        if (createParams == null)
            return;

        /*
         * The AsyncTask class must be loaded on the UI thread. This is done automatically as of JELLY_BEAN.
         * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            decodeTask = new BuildTorrentTask(this);
            decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, createParams);
        } else {
            Handler handler = new Handler(activity.getMainLooper());
            handler.post(() -> {
                decodeTask = new BuildTorrentTask(CreateTorrentFragment.this);
                decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, createParams);
            });
        }
    }

    public void handlingException(Exception e)
    {
        if (e == null)
            return;

        Log.e(TAG, Log.getStackTraceString(e));
        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (e instanceof FileNotFoundException) {
            if (fm.findFragmentByTag(TAG_ERROR_FILE_OR_FOLDER_NOT_FOUND) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        e.getMessage(),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_ERROR_FILE_OR_FOLDER_NOT_FOUND);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof IOException) {
            if (e.getMessage().contains("content total size can't be 0")) {
                if (fm.findFragmentByTag(TAG_ERROR_FOLDER_IS_EMPTY) == null) {
                    BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                            getString(R.string.error),
                            getString(R.string.folder_is_empty),
                            0,
                            getString(R.string.ok),
                            null,
                            null,
                            this);

                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(errDialog, TAG_ERROR_FOLDER_IS_EMPTY);
                    ft.commitAllowingStateLoss();
                }
            } else {
                sentError = e;
                if (fm.findFragmentByTag(TAG_IO_ERROR) == null) {
                    ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                            activity.getApplicationContext(),
                            getString(R.string.error),
                            getString(R.string.error_create_torrent) + ": " + e.getMessage(),
                            Log.getStackTraceString(e),
                            this);

                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(errDialog, TAG_IO_ERROR);
                    ft.commitAllowingStateLoss();
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putString(TAG_PATH_TO_FILE_OR_DIR, pathToFileOrDir);
        outState.putInt(TAG_PIECE_SIZE, pieceSize);
        outState.putParcelable(TAG_CREATE_PARAMS, createParams);

        super.onSaveInstanceState(outState);
    }

    private static class BuildTorrentTask extends AsyncTask<CreateTorrentParams, Void, byte[]>
    {
        private final WeakReference<CreateTorrentFragment> fragment;
        private CreateTorrentParams createParams;
        private Exception err;

        private BuildTorrentTask(CreateTorrentFragment fragment)
        {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        protected void onPreExecute()
        {
            if (fragment.get() != null) {
                fragment.get().buildProgress.setProgress(0);
                fragment.get().buildProgress.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected byte[] doInBackground(CreateTorrentParams... params)
        {
            if (fragment.get() == null || isCancelled())
                return null;

            createParams = params[0];

            List<Pair<String, Integer>> trackers = new ArrayList<>();
            for (String url : createParams.getTrackerUrls())
                trackers.add(new Pair<>(url, 0));

            try {
                TorrentBuilder builder = new TorrentBuilder()
                        .path(new File(createParams.getPath()))
                        .pieceSize(createParams.getPieceSize())
                        .addTrackers(trackers)
                        .addUrlSeeds(createParams.getWebSeedUrls())
                        .setPrivate(createParams.isPrivate())
                        .creator(makeCreator())
                        .comment(createParams.getComments())
                        .listener(new TorrentBuilder.Listener()
                        {
                            @Override
                            public boolean accept(String filename)
                            {
                                List<String> skipFilesList = createParams.getSkipFilesList();
                                if (skipFilesList == null || skipFilesList.isEmpty())
                                    return true;

                                for (String skipFile : skipFilesList)
                                    if (filename.toLowerCase().endsWith(skipFile.toLowerCase().trim()))
                                        return false;

                                return true;
                            }

                            @Override
                            public void progress(int pieceIndex, int numPieces)
                            {
                                if (fragment.get() == null)
                                    return;

                                fragment.get().buildProgress
                                        .setProgress((int)(pieceIndex * 100.0) / numPieces);
                            }
                        });
                if (createParams.isOptimizeAlignment())
                    builder.flags(builder.flags().or_(TorrentBuilder.OPTIMIZE_ALIGNMENT));
                else
                    builder.flags(builder.flags().and_(TorrentBuilder.OPTIMIZE_ALIGNMENT.inv()));

                return builder.generate().entry().bencode();

            } catch (Exception e) {
                err = e;
            }

            return null;
        }

        private String makeCreator()
        {
            if (fragment.get() == null)
                return "";

            Context context = fragment.get().activity.getApplicationContext();
            String creator = context.getString(R.string.app_name);
            String versionName = Utils.getAppVersionName(context);
            if (versionName == null)
                return creator;

            return creator + " " + versionName;
        }

        @Override
        protected void onPostExecute(byte[] bencode)
        {
            if (fragment.get() == null)
                return;

            if (err != null) {
                fragment.get().handlingException(err);
                return;
            }

            if (bencode != null) {
                try {
                    FileUtils.writeByteArrayToFile(new File(createParams.getPathToSave()), bencode);
                } catch (IOException e) {
                    fragment.get().handlingException(e);
                }
            }

            fragment.get().buildProgress.setVisibility(View.GONE);
            fragment.get().handlingBuildTaskResult(createParams);
        }
    }

    private void buildTorrent() throws Exception
    {
        if (decodeTask != null && decodeTask.getStatus() != BuildTorrentTask.Status.FINISHED)
            return;

        File path = new File(pathToFileOrDir);
        if (!path.exists())
            throw new FileNotFoundException(getString(R.string.file_or_folder_not_found));

        trackersLayout.setErrorEnabled(false);
        trackersLayout.setError(null);
        webSeedsLayout.setErrorEnabled(false);
        webSeedsLayout.setError(null);

        ArrayList<String> trackerUrls = new ArrayList<>();
        ArrayList<String> webSeedUrls = new ArrayList<>();
        IllegalArgumentException err = null;
        if (!TextUtils.isEmpty(trackersEditText.getText())) {
            try {
                trackerUrls = getAndValidateTrackers();
            } catch (IllegalArgumentException e) {
                err = e;
            }
        }
        if (!TextUtils.isEmpty(webSeedsEditText.getText())) {
            try {
                webSeedUrls = getAndValidateWebSeeds();
            } catch (IllegalArgumentException e) {
                err = e;
            }
        }
        /* Invalid trackers or web seeds */
        if (err != null)
            return;

        String comments = null;
        if (commentsEditText.getText() != null)
            comments = commentsEditText.getText().toString();
        boolean startSeeding = startSeedingOption.isChecked();
        boolean isPrivate = isPrivateOption.isChecked();
        boolean optimizeAlignment = optimizeAlignmentOption.isChecked();
        ArrayList<String> skipFilesList = new ArrayList<>();
        if (!TextUtils.isEmpty(skipFilesEditText.getText()))
            skipFilesList = new ArrayList<>(Arrays.asList(skipFilesEditText.getText().toString()
                    .split(CreateTorrentParams.FILTER_SEPARATOR)));

        createParams = new CreateTorrentParams(pathToFileOrDir, trackerUrls, webSeedUrls, comments,
                startSeeding, isPrivate, optimizeAlignment, skipFilesList, pieceSize);

        String pathComponents[] = pathToFileOrDir.split(File.separator);
        choosePathToSaveDialog(pathComponents[pathComponents.length - 1]);
    }

    private ArrayList<String> getAndValidateTrackers()
    {
        String[] trackerUrls = new String[0];
        if (trackersEditText.getText() != null)
            trackerUrls = trackersEditText.getText().toString().split("\n");
        ArrayList<String> validatedTrackers = new ArrayList<>();
        for (String url : trackerUrls) {
            url = Utils.normalizeURL(url.trim());
            if (Utils.isValidTrackerUrl(url)) {
                validatedTrackers.add(url);
            } else {
                trackersLayout.setErrorEnabled(true);
                trackersLayout.setError(String.format(getString(R.string.invalid_url), url));
                trackersLayout.requestFocus();

                throw new IllegalArgumentException();
            }
        }

        return validatedTrackers;
    }

    private ArrayList<String> getAndValidateWebSeeds()
    {
        String[] webSeedsUrls = new String[0];
        if (webSeedsEditText.getText() != null)
            webSeedsUrls = webSeedsEditText.getText().toString().split("\n");
        ArrayList<String> validatedWebSeeds = new ArrayList<>();
        for (String url : webSeedsUrls) {
            url = Utils.normalizeURL(url.trim());
            if (Utils.isValidTrackerUrl(url)) {
                validatedWebSeeds.add(url);
            } else {
                webSeedsLayout.setErrorEnabled(true);
                webSeedsLayout.setError(String.format(getString(R.string.invalid_url), url));
                webSeedsLayout.requestFocus();

                throw new IllegalArgumentException();
            }
        }

        return validatedWebSeeds;
    }

    private void handlingBuildTaskResult(CreateTorrentParams createParams)
    {
        Intent intent = new Intent();

        if (createParams.getPathToSave() == null) {
            Toast.makeText(activity.getApplicationContext(),
                    getString(R.string.error_create_torrent),
                    Toast.LENGTH_SHORT)
                    .show();
            finish(intent, FragmentCallback.ResultCode.OK);

            return;
        }

       Toast.makeText(activity.getApplicationContext(),
                String.format(getString(R.string.torrent_saved_to), createParams.getPathToSave()),
                Toast.LENGTH_SHORT)
                .show();

        if (createParams.isStartSeeding()) {
            TorrentMetaInfo info;
            try {
                info = new TorrentMetaInfo(createParams.getPathToSave());
            } catch (Exception e) {
                Toast.makeText(activity.getApplicationContext(),
                        getString(R.string.error_decode_torrent),
                        Toast.LENGTH_SHORT)
                        .show();
                finish(intent, FragmentCallback.ResultCode.OK);

                return;
            }
            AddTorrentParams params = new AddTorrentParams(
                    createParams.getPathToSave(),
                    false,
                    info.sha1Hash,
                    info.torrentName,
                    new ArrayList<>(Collections.nCopies(info.fileList.size(), Priority.DEFAULT)),
                    createParams.getPath().substring(0, createParams.getPath().lastIndexOf(File.separator)),
                    false,
                    false);
            intent.putExtra(CreateTorrentActivity.TAG_CREATED_TORRENT, params);
        }
        finish(intent, FragmentCallback.ResultCode.OK);
    }

    private void choosePathToSaveDialog(String fileName)
    {
        Intent i = new Intent(activity, FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(
                FileIOUtils.getUserDirPath(),
                getString(R.string.select_folder_to_save),
                null,
                FileManagerConfig.SAVE_FILE_MODE)
                .setFileName(fileName + ".torrent");
        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, CHOOSE_PATH_TO_SAVE_REQUEST);
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (sentError != null) {
            String comment = null;
            if (v != null) {
                EditText editText = v.findViewById(R.id.comment);
                comment = editText.getText().toString();
            }
            Utils.reportError(sentError, comment);
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        /* Nothing */
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
            /* Nothing */
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.add_torrent, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.add_torrent_dialog_add_menu:
                try {
                    buildTorrent();
                } catch (Exception e) {
                    handlingException(e);
                }
                break;
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == CHOOSE_DIR_REQUEST || requestCode == CHOOSE_FILE_REQUEST) {
            if (resultCode != Activity.RESULT_OK)
                return;
            if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                pathToFileOrDir = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);
                pathToFileOrDirView.setText(pathToFileOrDir);
            }

        } else if (requestCode == CHOOSE_PATH_TO_SAVE_REQUEST) {
            if (resultCode != Activity.RESULT_OK) {
                createParams = null;
                return;
            }
            if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH) && createParams != null) {
                createParams.setPathToSave(data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH));
                startBuildTask(createParams);
                createParams = null;
            }
        }
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        if (decodeTask != null)
            decodeTask.cancel(true);

        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
