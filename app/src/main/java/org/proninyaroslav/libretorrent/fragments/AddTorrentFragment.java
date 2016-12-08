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
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.jlibtorrent.Priority;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.ViewPagerAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentFetcher;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class AddTorrentFragment extends Fragment
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFragment.class.getSimpleName();

    private static final String TAG_URI = "uri";
    private static final String TAG_HAS_TORRENT = "has_torrent";
    private static final int INFO_FRAG_POS = 0;
    private static final int FILE_FRAG_POS = 1;

    public static final String TAG_PATH_TO_TEMP_TORRENT = "path_to_temp_torrent";
    public static final String TAG_SAVE_TORRENT_FILE = "save_torrent_file";

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private ViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private TabLayout tabLayout;
    private ViewPagerAdapter adapter;

    private Uri uri;
    private TorrentMetaInfo info;
    private Callback callback;
    private TorrentDecodeTask decodeTask;

    private String pathToTempTorrent;
    private boolean saveTorrentFile = true;
    private boolean hasTorrent = false;

    public interface Callback
    {
        void onPreExecute(String progressDialogText);

        void onPostExecute(Exception e);
    }

    public static AddTorrentFragment newInstance(Uri uri)
    {
        AddTorrentFragment fragment = new AddTorrentFragment();

        fragment.setUri(uri);

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_add_torrent, container, false);
        coordinatorLayout = (CoordinatorLayout) v.findViewById(R.id.coordinator_layout);

        return v;
    }

    /* For API < 23 */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        if (activity instanceof AppCompatActivity) {
            this.activity = (AppCompatActivity) activity;
            callback = (Callback) activity;
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (!saveTorrentFile && pathToTempTorrent != null) {
            try {
                FileUtils.forceDelete(new File(pathToTempTorrent));

            } catch (IOException e) {
                Log.w(TAG, "Could not delete temp file: ", e);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        adapter = new ViewPagerAdapter(activity.getSupportFragmentManager());

        Utils.showColoredStatusBar_KitKat(activity);

        toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.add_torrent_title);
        }

        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            pathToTempTorrent = savedInstanceState.getString(TAG_PATH_TO_TEMP_TORRENT);
            saveTorrentFile = savedInstanceState.getBoolean(TAG_SAVE_TORRENT_FILE);
            hasTorrent = savedInstanceState.getBoolean(TAG_HAS_TORRENT);
            /*
             * No initialize fragments in the event of an decode error or
             * torrent decoding in process (after configuration changes)
             */
            if (hasTorrent) {
                initFragments();
            }

        } else {
            final StringBuilder progressDialogText = new StringBuilder("");
            if (uri == null || uri.getScheme() == null) {
                progressDialogText.append(getString(R.string.decode_torrent_default_message));
            } else {
                switch (uri.getScheme()) {
                    case Utils.MAGNET_PREFIX:
                        progressDialogText.append(getString(R.string.decode_torrent_fetch_magnet_message));
                        break;
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        progressDialogText.append(getString(R.string.decode_torrent_downloading_torrent_message));
                        break;
                    default:
                        progressDialogText.append(getString(R.string.decode_torrent_default_message));
                        break;
                }
            }

            /*
             * The AsyncTask class must be loaded on the UI thread. This is done automatically as of JELLY_BEAN.
             * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                decodeTask = new TorrentDecodeTask(progressDialogText.toString());
                decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
            } else {
                Handler handler = new Handler(activity.getMainLooper());
                Runnable r = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        decodeTask = new TorrentDecodeTask(progressDialogText.toString());
                        decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
                    }
                };
                handler.post(r);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putParcelable(TAG_URI, uri);
        outState.putBoolean(TAG_HAS_TORRENT, hasTorrent);
        outState.putString(TAG_PATH_TO_TEMP_TORRENT, pathToTempTorrent);
        outState.putBoolean(TAG_SAVE_TORRENT_FILE, saveTorrentFile);

        super.onSaveInstanceState(outState);
    }

    public void setUri(Uri uri)
    {
        this.uri = uri;
    }

    private class TorrentDecodeTask extends AsyncTask<Uri, Void, Exception> {
        String progressDialogText;

        public TorrentDecodeTask(String progressDialogText)
        {
            this.progressDialogText = progressDialogText;
        }

        @Override
        protected void onPreExecute()
        {
            if (callback != null) {
                callback.onPreExecute(progressDialogText);
            }
        }

        @Override
        protected Exception doInBackground(Uri... params)
        {
            Uri uri = params[0];

            if (uri == null || uri.getScheme() == null) {
                IllegalArgumentException e = new IllegalArgumentException("Can't decode link/path");
                Log.e(TAG, Log.getStackTraceString(e));

                return e;
            }

            try {
                switch (uri.getScheme()) {
                    case Utils.FILE_PREFIX:
                        pathToTempTorrent = uri.getPath();
                        break;
                    case Utils.CONTENT_PREFIX:
                        pathToTempTorrent =
                                Utils.getRealPathFromURI(getActivity().getApplicationContext(), uri);
                        break;
                    case Utils.MAGNET_PREFIX:
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        TorrentFetcher fetcher =
                                new TorrentFetcher(getActivity().getApplicationContext(), uri);

                        File torrentFile = fetcher.fetch(FileIOUtils.getTempDir(activity.getApplicationContext()));

                        if (torrentFile != null && torrentFile.exists()) {
                            pathToTempTorrent = torrentFile.getAbsolutePath();
                            saveTorrentFile = false;
                        } else {
                            IllegalArgumentException e =
                                    new IllegalArgumentException("Unknown path to torrent file");
                            Log.e(TAG, Log.getStackTraceString(e));

                            return e;
                        }

                        break;
                    default:
                        IllegalArgumentException e =
                                new IllegalArgumentException("Unknown link/path type: " + uri.getScheme());
                        Log.e(TAG, Log.getStackTraceString(e));

                        return e;
                }

                info = new TorrentMetaInfo(pathToTempTorrent);

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));

                return e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Exception e)
        {
            if (callback != null) {
                callback.onPostExecute(e);
            }

            if (e != null) {
                return;
            }

            hasTorrent = true;
            initFragments();
        }
    }

    private void initFragments()
    {
        if (!isAdded()) {
            return;
        }

        AddTorrentInfoFragment fragmentInfo = AddTorrentInfoFragment.newInstance(info);
        AddTorrentFilesFragment fragmentFile = AddTorrentFilesFragment.newInstance(info.getFiles());

        viewPager = (ViewPager) activity.findViewById(R.id.add_torrent_viewpager);
        adapter.addFragment(fragmentInfo, INFO_FRAG_POS, getString(R.string.torrent_info));
        adapter.addFragment(fragmentFile, FILE_FRAG_POS, getString(R.string.torrent_files));
        viewPager.setAdapter(adapter);
        tabLayout = (TabLayout) activity.findViewById(R.id.add_torrent_tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
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
                buildTorrent();
                break;
        }

        return true;
    }

    private void buildTorrent()
    {
        AddTorrentFilesFragment fileFrag = (AddTorrentFilesFragment) adapter.getItem(FILE_FRAG_POS);
        AddTorrentInfoFragment infoFrag = (AddTorrentInfoFragment) adapter.getItem(INFO_FRAG_POS);

        if (fileFrag != null || infoFrag != null) {
            ArrayList<Integer> selectedIndexes = fileFrag.getSelectedFileIndexes();
            String downloadDir = infoFrag.getDownloadDir();
            String torrentName = infoFrag.getTorrentName();
            boolean sequentialDownload = infoFrag.isSequentialDownload();
            boolean startTorrent = infoFrag.startTorrent();

            if (info != null) {
                ArrayList<BencodeFileItem> files = info.getFiles();
                if (files.size() != 0 && selectedIndexes.size() != 0 && !TextUtils.isEmpty(torrentName)) {
                    if (FileIOUtils.getFreeSpace(downloadDir) >= fileFrag.getSelectedFileSize()) {
                        Intent intent = new Intent();

                        ArrayList<Integer> priorities =
                                new ArrayList<>(
                                        Collections.nCopies(
                                                files.size(),
                                                Priority.IGNORE.swig()));

                        for (int index : selectedIndexes) {
                            priorities.set(index, Priority.NORMAL.swig());
                        }

                        Torrent torrent =
                                new Torrent(info.getSha1Hash(),
                                        torrentName, priorities, downloadDir);

                        torrent.setSequentialDownload(sequentialDownload);
                        torrent.setPaused(!startTorrent);
                        torrent.setTorrentFilePath(pathToTempTorrent);

                        saveTorrentFile = true;

                        intent.putExtra(AddTorrentActivity.TAG_RESULT_TORRENT, torrent);
                        finish(intent, FragmentCallback.ResultCode.OK);

                    } else {
                        Snackbar snackbar = Snackbar.make(coordinatorLayout,
                                R.string.error_free_space,
                                Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }

                } else {
                    if (selectedIndexes.size() == 0) {
                        Snackbar snackbar = Snackbar.make(coordinatorLayout,
                                R.string.error_no_files_selected,
                                Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                }
            }
        }
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback) activity).fragmentFinished(intent, code);
    }
}
