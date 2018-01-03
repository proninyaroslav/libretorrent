/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
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
import android.widget.EditText;
import android.widget.ProgressBar;

import com.frostwire.jlibtorrent.Priority;

import org.proninyaroslav.libretorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.RequestPermissions;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.ViewPagerAdapter;
import org.proninyaroslav.libretorrent.core.FetchMagnetCallback;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class AddTorrentFragment extends Fragment
        implements BaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFragment.class.getSimpleName();

    private static final String TAG_URI = "uri";
    private static final int INFO_FRAG_POS = 0;
    private static final int FILE_FRAG_POS = 1;

    private static final String TAG_PATH_TO_TEMP_TORRENT = "path_to_temp_torrent";
    private static final String TAG_SAVE_TORRENT_FILE = "save_torrent_file";
    private static final String TAG_FETCHING_STATE = "fetching_state";
    private static final String TAG_INFO = "info";
    private static final String TAG_IO_EXCEPT_DIALOG = "io_except_dialog";
    private static final String TAG_DECODE_EXCEPT_DIALOG = "decode_except_dialog";
    private static final String TAG_FETCH_EXCEPT_DIALOG = "fetch_except_dialog";
    private static final String TAG_ILLEGAL_ARGUMENT = "illegal_argument";
    private static final String TAG_FROM_MAGNET = "from_magnet";

    private static final int PERMISSION_REQUEST = 1;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private ViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private TabLayout tabLayout;
    private ViewPagerAdapter adapter;
    private ProgressBar fetchMagnetProgress;

    private Uri uri;
    private TorrentMetaInfo info;
    private Callback callback;
    private TorrentDecodeTask decodeTask;
    private TorrentTaskService service;
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    private boolean fromMagnet = false;

    private String pathToTempTorrent = null;
    private boolean saveTorrentFile = true;
    private AtomicReference<State> decodeState = new AtomicReference<>(State.UNKNOWN);

    private Exception sentError;

    private enum State
    {
        UNKNOWN,
        DECODE_TORRENT_FILE,
        DECODE_TORRENT_COMPLETED,
        FETCHING_MAGNET,
        FETCHING_HTTP,
        FETCHING_MAGNET_COMPLETED,
        FETCHING_HTTP_COMPLETED,
        ERROR
    }

    public interface Callback
    {
        void onPreExecute(String progressDialogText);

        void onPostExecute();
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
        fetchMagnetProgress = (ProgressBar) v.findViewById(R.id.fetch_magnet_progress);
        viewPager = (ViewPager) v.findViewById(R.id.add_torrent_viewpager);
        tabLayout = (TabLayout) v.findViewById(R.id.add_torrent_tabs);

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
        if (bound) {
            getActivity().unbindService(connection);

            bound = false;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (bound && service != null)
            service.removeFetchMagnetCallback(magnetCallback);

        if (!saveTorrentFile)
            if (bound && service != null && info != null)
                service.cancelFetchMagnet(info.sha1Hash);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();
        Utils.showColoredStatusBar_KitKat(activity);
        toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null)
            toolbar.setTitle(R.string.add_torrent_title);
        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        adapter = new ViewPagerAdapter(activity.getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        if (savedInstanceState != null) {
            pathToTempTorrent = savedInstanceState.getString(TAG_PATH_TO_TEMP_TORRENT);
            saveTorrentFile = savedInstanceState.getBoolean(TAG_SAVE_TORRENT_FILE);
            decodeState = (AtomicReference<State>) savedInstanceState.getSerializable(TAG_FETCHING_STATE);
            info = savedInstanceState.getParcelable(TAG_INFO);
            fromMagnet = savedInstanceState.getBoolean(TAG_FROM_MAGNET);

            showFetchMagnetProgress(decodeState.get() == State.FETCHING_MAGNET);

            /*
             * No initialize fragments in the event of an decode error or
             * torrent decoding in process (after configuration changes)
             */
            if (decodeState.get() != State.FETCHING_HTTP &&
                    decodeState.get() != State.DECODE_TORRENT_FILE &&
                    decodeState.get() != State.UNKNOWN &&
                    decodeState.get() != State.ERROR)
            {
                showFragments(true, true);
            }

        } else {
            if (uri == null || uri.getScheme() == null) {
                handlingException(new IllegalArgumentException("Can't decode link/path"));

                return;
            }

            if (Utils.checkStoragePermission(activity.getApplicationContext()))
                initDecode();
            else
                startActivityForResult(new Intent(activity, RequestPermissions.class), PERMISSION_REQUEST);
        }

        if (uri.getScheme().equals(Utils.MAGNET_PREFIX)) {
            activity.startService(new Intent(activity.getApplicationContext(), TorrentTaskService.class));
            activity.bindService(new Intent(activity.getApplicationContext(), TorrentTaskService.class),
                    connection, Context.BIND_AUTO_CREATE);
        }
    }

    private void initDecode()
    {
        if (uri.getScheme().equals(Utils.MAGNET_PREFIX) && !bound)
            return;

        String progressDialogText;

        switch (uri.getScheme()) {
            case Utils.FILE_PREFIX:
            case Utils.CONTENT_PREFIX:
                decodeState.set(State.DECODE_TORRENT_FILE);
                progressDialogText = getString(R.string.decode_torrent_default_message);
                break;
            case Utils.HTTP_PREFIX:
            case Utils.HTTPS_PREFIX:
                decodeState.set(State.FETCHING_HTTP);
                progressDialogText = getString(R.string.decode_torrent_downloading_torrent_message);
                break;
            case Utils.MAGNET_PREFIX:
                fromMagnet = true;
                decodeState.set(State.FETCHING_MAGNET);
                progressDialogText = getString(R.string.decode_torrent_fetch_magnet_message);
                break;
            default:
                handlingException(new IllegalArgumentException("Unknown link/path type: " + uri.getScheme()));

                return;
        }

        startDecodeTask(progressDialogText);
    }

    private void startDecodeTask(final String progressDialogText)
    {
        /*
         * The AsyncTask class must be loaded on the UI thread. This is done automatically as of JELLY_BEAN.
         * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            decodeTask = new TorrentDecodeTask(progressDialogText);
            decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
        } else {
            Handler handler = new Handler(activity.getMainLooper());
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    decodeTask = new TorrentDecodeTask(progressDialogText);
                    decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
                }
            };
            handler.post(r);
        }
    }

    public void handlingException(Exception e)
    {
        if (e == null) {
            return;
        }

        decodeState.set(State.ERROR);
        showFetchMagnetProgress(false);

        Log.e(TAG, Log.getStackTraceString(e));
        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (e instanceof DecodeException) {
            if (fm.findFragmentByTag(TAG_DECODE_EXCEPT_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_decode_torrent),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_DECODE_EXCEPT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof FetchLinkException) {
            if (fm.findFragmentByTag(TAG_FETCH_EXCEPT_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_fetch_link),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_FETCH_EXCEPT_DIALOG);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof IllegalArgumentException) {
            if (fm.findFragmentByTag(TAG_ILLEGAL_ARGUMENT) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.error_invalid_link_or_path),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_ILLEGAL_ARGUMENT);
                ft.commitAllowingStateLoss();
            }

        } else if (e instanceof IOException) {
            sentError = e;
            if (fm.findFragmentByTag(TAG_IO_EXCEPT_DIALOG) == null) {
                ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                        activity.getApplicationContext(),
                        getString(R.string.error),
                        getString(R.string.error_io_torrent),
                        Log.getStackTraceString(e),
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_IO_EXCEPT_DIALOG);
                ft.commitAllowingStateLoss();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putParcelable(TAG_URI, uri);
        outState.putString(TAG_PATH_TO_TEMP_TORRENT, pathToTempTorrent);
        outState.putBoolean(TAG_SAVE_TORRENT_FILE, saveTorrentFile);
        outState.putSerializable(TAG_FETCHING_STATE, decodeState);
        outState.putParcelable(TAG_INFO, info);
        outState.putBoolean(TAG_FROM_MAGNET, fromMagnet);

        super.onSaveInstanceState(outState);
    }

    private ServiceConnection connection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            TorrentTaskService.LocalBinder binder = (TorrentTaskService.LocalBinder) service;
            AddTorrentFragment.this.service = binder.getService();
            bound = true;
            if (Utils.checkStoragePermission(activity.getApplicationContext()) &&
                    decodeState.get() == State.UNKNOWN)
            {
                initDecode();
            }
        }

        public void onServiceDisconnected(ComponentName className)
        {
            bound = false;
        }
    };

    public void setUri(Uri uri)
    {
        this.uri = uri;
    }

    FetchMagnetCallback magnetCallback = new FetchMagnetCallback()
    {
        @Override
        public void onMagnetFetched(String hash, TorrentMetaInfo ti)
        {
            if (!hash.equals(info.sha1Hash))
                return;

            if (service != null)
                service.removeFetchMagnetCallback(this);
            decodeState.set(State.FETCHING_MAGNET_COMPLETED);
            showFetchMagnetProgress(false);
            if (ti == null) {
                try {
                    throw new FetchLinkException("info is null");
                } catch (FetchLinkException e) {
                    handlingException(e);
                }
                return;
            }
            info = ti;
            /* Prevent race condition */
            if (decodeTask == null || decodeTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                updateInfoFragment();
                showFragments(false, true);
            }
        }
    };

    private class TorrentDecodeTask extends AsyncTask<Uri, Void, Exception> {
        String progressDialogText;

        private TorrentDecodeTask(String progressDialogText)
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

            try {
                switch (uri.getScheme()) {
                    case Utils.FILE_PREFIX:
                        pathToTempTorrent = uri.getPath();
                        break;
                    case Utils.CONTENT_PREFIX:
                        File contentTmp = FileIOUtils.makeTempFile(activity.getApplicationContext(), ".torrent");
                        FileIOUtils.copyContentURIToFile(activity.getApplicationContext(), uri, contentTmp);

                        if (contentTmp.exists()) {
                            pathToTempTorrent = contentTmp.getAbsolutePath();
                            saveTorrentFile = false;
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }
                        break;
                    case Utils.MAGNET_PREFIX:
                        saveTorrentFile = false;

                        if (service != null) {
                            Snackbar.make(coordinatorLayout,
                                    R.string.decode_torrent_fetch_magnet_message,
                                    Snackbar.LENGTH_LONG)
                                    .show();
                            showFetchMagnetProgress(true);

                            String hash = service.fetchMagnet(uri.toString(), magnetCallback);

                            if (hash != null) {
                                info = new TorrentMetaInfo(hash, hash);
                            }
                        }
                        break;
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        File httpTmp = FileIOUtils.makeTempFile(activity.getApplicationContext(), ".torrent");
                        TorrentUtils.fetchByHTTP(activity.getApplicationContext(), uri.toString(), httpTmp);

                        if (httpTmp.exists()) {
                            pathToTempTorrent = httpTmp.getAbsolutePath();
                            saveTorrentFile = false;
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }

                        break;
                }

                if (pathToTempTorrent != null) {
                    info = new TorrentMetaInfo(pathToTempTorrent);
                }

            } catch (Exception e) {
                return e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Exception e)
        {
            if (callback != null) {
                callback.onPostExecute();
            }
            handlingException(e);

            switch (decodeState.get()) {
                case DECODE_TORRENT_FILE:
                    decodeState.set(State.DECODE_TORRENT_COMPLETED);
                    break;
                case FETCHING_HTTP:
                    decodeState.set(State.FETCHING_HTTP_COMPLETED);
                    break;
            }

            if (e != null) {
                return;
            }

            showFragments(true, decodeState.get() != State.FETCHING_MAGNET);
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (sentError != null) {
            String comment = null;

            if (v != null) {
                EditText editText = (EditText) v.findViewById(R.id.comment);
                comment = editText.getText().toString();
            }

            Utils.reportError(sentError, comment);
        }

        finish(new Intent(), FragmentCallback.ResultCode.CANCEL);
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        finish(new Intent(), FragmentCallback.ResultCode.CANCEL);
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
            /* Nothing */
    }

    private synchronized void updateInfoFragment()
    {
        if (!isAdded() || adapter == null) {
            return;
        }

        final AddTorrentInfoFragment infoFrag = (AddTorrentInfoFragment) adapter.getItem(INFO_FRAG_POS);
        if (infoFrag == null) {
            return;
        }

        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                infoFrag.setInfo(info);
            }
        });
    }

    private synchronized void showFragments(final boolean showInfo, final boolean showFile)
    {
        if (!isAdded() || adapter == null) {
            return;
        }

        if ((showInfo && adapter.getItem(INFO_FRAG_POS) != null) ||
                (showFile && adapter.getItem(FILE_FRAG_POS) != null))
        {
            return;
        }

        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (showInfo) {
                    adapter.addFragment(AddTorrentInfoFragment.newInstance(info),
                            INFO_FRAG_POS, getString(R.string.torrent_info));
                }

                if (showFile) {
                    adapter.addFragment(AddTorrentFilesFragment.newInstance(info.fileList),
                            FILE_FRAG_POS, getString(R.string.torrent_files));
                }

                adapter.notifyDataSetChanged();
            }
        });
    }

    private void showFetchMagnetProgress(final boolean show)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                fetchMagnetProgress.setVisibility((show ? View.VISIBLE : View.GONE));
            }
        });
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == PERMISSION_REQUEST) {
            initDecode();
        }
    }

    private void buildTorrent()
    {
        AddTorrentFilesFragment fileFrag = (AddTorrentFilesFragment) adapter.getItem(FILE_FRAG_POS);
        AddTorrentInfoFragment infoFrag = (AddTorrentInfoFragment) adapter.getItem(INFO_FRAG_POS);

        if (infoFrag == null || info == null) {
            return;
        }

        String downloadDir = infoFrag.getDownloadDir();
        String torrentName = infoFrag.getTorrentName();
        boolean sequentialDownload = infoFrag.isSequentialDownload();
        boolean startTorrent = infoFrag.startTorrent();
        ArrayList<Integer> selectedIndexes = null;

        if (TextUtils.isEmpty(torrentName)) {
            return;
        }

        if (info.fileList.size() == 0 && decodeState.get() != State.FETCHING_MAGNET) {
            return;
        }

        if (fileFrag != null) {
            selectedIndexes = fileFrag.getSelectedFileIndexes();
        }

        if ((selectedIndexes == null || selectedIndexes.size() == 0) &&
                decodeState.get() != State.FETCHING_MAGNET)
        {
            Snackbar.make(coordinatorLayout,
                    R.string.error_no_files_selected,
                    Snackbar.LENGTH_LONG)
                    .show();

            return;
        }

        if (fileFrag != null && (FileIOUtils.getFreeSpace(downloadDir) < fileFrag.getSelectedFileSize())) {
            Snackbar.make(coordinatorLayout,
                    R.string.error_free_space,
                    Snackbar.LENGTH_LONG)
                    .show();

            return;
        }

        Intent intent = new Intent();

        ArrayList<Priority> priorities = new ArrayList<>(Collections.nCopies(info.fileList.size(), Priority.IGNORE));
        if (selectedIndexes != null)
            for (int index : selectedIndexes)
                priorities.set(index, Priority.NORMAL);

        Torrent torrent = new Torrent(info.sha1Hash, torrentName, priorities,
                                      downloadDir, System.currentTimeMillis());
        torrent.setSequentialDownload(sequentialDownload);
        torrent.setPaused(!startTorrent);
        torrent.setTorrentFilePath(pathToTempTorrent == null && fromMagnet ? uri.toString() : pathToTempTorrent);
        torrent.setDownloadingMetadata(fromMagnet);
        saveTorrentFile = true;

        intent.putExtra(AddTorrentActivity.TAG_RESULT_TORRENT, torrent);
        finish(intent, FragmentCallback.ResultCode.OK);
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
