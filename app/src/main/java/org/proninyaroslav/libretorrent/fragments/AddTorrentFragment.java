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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

import org.libtorrent4j.Priority;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.proninyaroslav.libretorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.RequestPermissions;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.AddTorrentPagerAdapter;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.MagnetInfo;
import org.proninyaroslav.libretorrent.core.TorrentHelper;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.receivers.TorrentTaskServiceReceiver;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.TorrentStateMsg;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class AddTorrentFragment extends Fragment
        implements BaseAlertDialog.OnClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentFragment.class.getSimpleName();

    private static final String HEAVY_STATE_TAG = TAG + "_" + HeavyInstanceStorage.class.getSimpleName();
    private static final String TAG_URI = "uri";

    private static final String TAG_PATH_TO_TEMP_TORRENT = "path_to_temp_torrent";
    private static final String TAG_SAVE_TORRENT_FILE = "save_torrent_file";
    private static final String TAG_FETCHING_STATE = "fetching_state";
    private static final String TAG_INFO = "info";
    private static final String TAG_IO_EXCEPT_DIALOG = "io_except_dialog";
    private static final String TAG_DECODE_EXCEPT_DIALOG = "decode_except_dialog";
    private static final String TAG_FETCH_EXCEPT_DIALOG = "fetch_except_dialog";
    private static final String TAG_OUT_OF_MEMORY_DIALOG = "out_of_memory_dialog";
    private static final String TAG_ILLEGAL_ARGUMENT = "illegal_argument";
    private static final String TAG_FROM_MAGNET = "from_magnet";
    private static final String TAG_MAGNET_PRIORITIES = "magnet_priorities";

    private static final int PERMISSION_REQUEST = 1;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private ViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private TabLayout tabLayout;
    private AddTorrentPagerAdapter adapter;
    private ProgressBar fetchMagnetProgress;

    private Uri uri;
    private TorrentMetaInfo info;
    private Callback callback;
    private TorrentDecodeTask decodeTask;
    private boolean fromMagnet = false;
    /* BEP53 standard */
    private ArrayList<Priority> magnetPriorities;

    private String pathToTempTorrent = null;
    private boolean saveTorrentFile = true;
    private AtomicReference<State> decodeState = new AtomicReference<>(State.UNKNOWN);

    private Throwable sentError;

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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_add_torrent, container, false);
        coordinatorLayout = v.findViewById(R.id.coordinator_layout);
        fetchMagnetProgress = v.findViewById(R.id.fetch_magnet_progress);
        viewPager = v.findViewById(R.id.add_torrent_viewpager);
        tabLayout = v.findViewById(R.id.add_torrent_tabs);

        return v;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity)context;
            callback = (Callback)context;
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

        TorrentTaskServiceReceiver.getInstance().unregister(serviceReceiver);
        if (!saveTorrentFile && info != null)
            TorrentHelper.cancelFetchMagnet(info.sha1Hash);
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
            toolbar.setTitle(R.string.add_torrent_title);
        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        adapter = new AddTorrentPagerAdapter(activity.getSupportFragmentManager(), activity);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(AddTorrentPagerAdapter.NUM_FRAGMENTS);
        viewPager.post(() -> {
            updateInfoFragment();
            updateFilesFragment();
        });
        tabLayout.setupWithViewPager(viewPager);

        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(activity.getSupportFragmentManager());
        if (storage != null) {
            Bundle heavyInstance = storage.popData(HEAVY_STATE_TAG);
            if (heavyInstance != null) {
                info = heavyInstance.getParcelable(TAG_INFO);
                magnetPriorities = (ArrayList<Priority>)heavyInstance.getSerializable(TAG_FROM_MAGNET);
            }
        }
        if (savedInstanceState != null) {
            pathToTempTorrent = savedInstanceState.getString(TAG_PATH_TO_TEMP_TORRENT);
            saveTorrentFile = savedInstanceState.getBoolean(TAG_SAVE_TORRENT_FILE);
            decodeState = (AtomicReference<State>)savedInstanceState.getSerializable(TAG_FETCHING_STATE);
            fromMagnet = savedInstanceState.getBoolean(TAG_FROM_MAGNET);

            showFetchMagnetProgress(decodeState.get() == State.FETCHING_MAGNET);

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
            if (!TorrentTaskServiceReceiver.getInstance().isRegistered(serviceReceiver))
                TorrentTaskServiceReceiver.getInstance().register(serviceReceiver);
            if (Utils.checkStoragePermission(activity.getApplicationContext()) &&
                decodeState.get() == State.UNKNOWN)
            {
                initDecode();
            }
        }
    }

    private void initDecode()
    {
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
            decodeTask = new TorrentDecodeTask(this, progressDialogText);
            decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
        } else {
            Handler handler = new Handler(activity.getMainLooper());
            handler.post(() -> {
                decodeTask = new TorrentDecodeTask(AddTorrentFragment.this, progressDialogText);
                decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
            });
        }
    }

    public void handlingException(Throwable e)
    {
        if (e == null)
            return;

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

        } else if (e instanceof OutOfMemoryError) {
            if (fm.findFragmentByTag(TAG_OUT_OF_MEMORY_DIALOG) == null) {
                BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                        getString(R.string.error),
                        getString(R.string.file_is_too_large_error),
                        0,
                        getString(R.string.ok),
                        null,
                        null,
                        this);

                FragmentTransaction ft = fm.beginTransaction();
                ft.add(errDialog, TAG_OUT_OF_MEMORY_DIALOG);
                ft.commitAllowingStateLoss();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putParcelable(TAG_URI, uri);
        outState.putString(TAG_PATH_TO_TEMP_TORRENT, pathToTempTorrent);
        outState.putBoolean(TAG_SAVE_TORRENT_FILE, saveTorrentFile);
        outState.putSerializable(TAG_FETCHING_STATE, decodeState);
        outState.putBoolean(TAG_FROM_MAGNET, fromMagnet);

        Bundle b = new Bundle();
        b.putParcelable(TAG_INFO, info);
        b.putSerializable(TAG_MAGNET_PRIORITIES, magnetPriorities);
        HeavyInstanceStorage storage = HeavyInstanceStorage.getInstance(activity.getSupportFragmentManager());
        if (storage != null)
            storage.pushData(HEAVY_STATE_TAG, b);
    }

    public void setUri(Uri uri)
    {
        this.uri = uri;
    }

    TorrentTaskServiceReceiver.Callback serviceReceiver = new TorrentTaskServiceReceiver.Callback()
    {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onReceive(Bundle b)
        {
            if (b != null && b.getSerializable(TorrentStateMsg.TYPE) == TorrentStateMsg.Type.MAGNET_FETCHED) {
                TorrentMetaInfo ti = b.getParcelable(TorrentStateMsg.META_INFO);
                decodeState.set(State.FETCHING_MAGNET_COMPLETED);
                showFetchMagnetProgress(false);
                if (info != null && ti != null) {
                    if (ti.sha1Hash.equals(info.sha1Hash)) {
                        info = ti;
                        /* Prevent race condition */
                        if (decodeTask == null || decodeTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                            updateInfoFragment();
                            updateFilesFragment();
                        }
                    }
                } else {
                    try {
                        throw new FetchLinkException("info is null");
                    } catch (FetchLinkException e) {
                        handlingException(e);
                    }
                }
            }
        }
    };

    private static class TorrentDecodeTask extends AsyncTask<Uri, Void, Throwable>
    {
        private final WeakReference<AddTorrentFragment> fragment;
        private String progressDialogText;

        private TorrentDecodeTask(AddTorrentFragment fragment, String progressDialogText)
        {
            this.fragment = new WeakReference<>(fragment);
            this.progressDialogText = progressDialogText;
        }

        @Override
        protected void onPreExecute()
        {
            if (fragment.get() != null && fragment.get().callback != null)
                fragment.get().callback.onPreExecute(progressDialogText);
        }

        @Override
        protected Throwable doInBackground(Uri... params)
        {
            if (fragment.get() == null || isCancelled())
                return null;

            Uri uri = params[0];
            try {
                switch (uri.getScheme()) {
                    case Utils.FILE_PREFIX:
                        fragment.get().pathToTempTorrent = uri.getPath();
                        break;
                    case Utils.CONTENT_PREFIX:
                        File contentTmp = FileIOUtils.makeTempFile(fragment.get().activity.getApplicationContext(), ".torrent");
                        FileIOUtils.copyContentURIToFile(fragment.get().activity.getApplicationContext(), uri, contentTmp);

                        if (contentTmp.exists() && !isCancelled()) {
                            fragment.get().pathToTempTorrent = contentTmp.getAbsolutePath();
                            fragment.get().saveTorrentFile = false;
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }
                        break;
                    case Utils.MAGNET_PREFIX:
                        fragment.get().saveTorrentFile = false;
                        Snackbar.make(fragment.get().coordinatorLayout,
                                R.string.decode_torrent_fetch_magnet_message,
                                Snackbar.LENGTH_LONG)
                                .show();
                        fragment.get().showFetchMagnetProgress(true);

                        MagnetInfo magnetInfo = TorrentHelper.fetchMagnet(uri.toString());
                        if (magnetInfo  != null && !isCancelled()) {
                            fragment.get().info = new TorrentMetaInfo(magnetInfo.getName(),
                                    magnetInfo.getSha1hash());
                            if (magnetInfo.getFilePriorities() != null)
                                fragment.get().magnetPriorities = new ArrayList<>(
                                        magnetInfo.getFilePriorities());
                        }
                        break;
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        File httpTmp = FileIOUtils.makeTempFile(fragment.get().activity.getApplicationContext(), ".torrent");
                        byte[] response = Utils.fetchHttpUrl(fragment.get().activity.getApplicationContext(), uri.toString());
                        FileUtils.writeByteArrayToFile(httpTmp, response);

                        if (httpTmp.exists() && !isCancelled()) {
                            fragment.get().pathToTempTorrent = httpTmp.getAbsolutePath();
                            fragment.get().saveTorrentFile = false;
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }

                        break;
                }

                if (fragment.get().pathToTempTorrent != null && !isCancelled())
                    fragment.get().info = new TorrentMetaInfo(fragment.get().pathToTempTorrent);
            } catch (Throwable e) {
                return e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Throwable e)
        {
            if (fragment.get() == null)
                return;

            if (fragment.get().callback != null)
                fragment.get().callback.onPostExecute();
            fragment.get().handlingException(e);

            switch (fragment.get().decodeState.get()) {
                case DECODE_TORRENT_FILE:
                    fragment.get().decodeState.set(State.DECODE_TORRENT_COMPLETED);
                    break;
                case FETCHING_HTTP:
                    fragment.get().decodeState.set(State.FETCHING_HTTP_COMPLETED);
                    break;
            }

            if (e != null)
                return;

            fragment.get().updateInfoFragment();
            if (fragment.get().decodeState.get() != State.FETCHING_MAGNET)
                fragment.get().updateFilesFragment();
        }
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
        if (!isAdded() || adapter == null || info == null)
            return;

        AddTorrentInfoFragment infoFrag = (AddTorrentInfoFragment)adapter
                .getFragment(AddTorrentPagerAdapter.INFO_FRAG_POS);
        if (infoFrag == null)
            return;
        infoFrag.setInfo(info);
    }

    private synchronized void updateFilesFragment()
    {
        if (!isAdded() || adapter == null || info == null)
            return;

        AddTorrentFilesFragment filesFrag = (AddTorrentFilesFragment)adapter
                .getFragment(AddTorrentPagerAdapter.FILES_FRAG_POS);
        if (filesFrag == null)
            return;
        filesFrag.setFilesAndPriorities(info.fileList, magnetPriorities);
    }

    private void showFetchMagnetProgress(final boolean show)
    {
        activity.runOnUiThread(() -> fetchMagnetProgress.setVisibility((show ? View.VISIBLE : View.GONE)));
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
        if (requestCode == PERMISSION_REQUEST && resultCode == Activity.RESULT_OK)
            initDecode();
    }

    private void buildTorrent()
    {
        AddTorrentFilesFragment fileFrag = (AddTorrentFilesFragment)adapter
                .getFragment(AddTorrentPagerAdapter.FILES_FRAG_POS);
        AddTorrentInfoFragment infoFrag = (AddTorrentInfoFragment)adapter
                .getFragment(AddTorrentPagerAdapter.INFO_FRAG_POS);
        if (infoFrag == null || info == null)
            return;

        String downloadDir = infoFrag.getDownloadDir();
        String torrentName = infoFrag.getTorrentName();
        if (TextUtils.isEmpty(torrentName))
            return;
        boolean sequentialDownload = infoFrag.isSequentialDownload();
        boolean startTorrent = infoFrag.startTorrent();
        Set<Integer> selectedIndexes = null;
        if (fileFrag != null)
            selectedIndexes = fileFrag.getSelectedFileIndexes();

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
            updateInfoFragment();

            return;
        }

        ArrayList<Priority> priorities = null;
        if (info.fileCount != 0) {
            if (selectedIndexes != null && selectedIndexes.size() == info.fileCount) {
                priorities = new ArrayList<>(Collections.nCopies(info.fileCount, Priority.DEFAULT));
            } else {
                priorities = new ArrayList<>(Collections.nCopies(info.fileCount, Priority.IGNORE));
                if (selectedIndexes != null)
                    for (int index : selectedIndexes)
                        priorities.set(index, Priority.DEFAULT);
            }
        }
        String source = (pathToTempTorrent == null && fromMagnet ? uri.toString() : pathToTempTorrent);
        AddTorrentParams params = new AddTorrentParams(source, fromMagnet, info.sha1Hash,
                                                       torrentName, priorities, downloadDir,
                                                       sequentialDownload, !startTorrent);
        saveTorrentFile = true;

        Intent intent = new Intent();
        intent.putExtra(AddTorrentActivity.TAG_ADD_TORRENT_PARAMS, params);
        finish(intent, FragmentCallback.ResultCode.OK);
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
