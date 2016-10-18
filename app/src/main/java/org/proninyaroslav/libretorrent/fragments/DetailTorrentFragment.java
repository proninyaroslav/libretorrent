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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.frostwire.jlibtorrent.Priority;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.ViewPagerAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.StateParcelCache;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DetailTorrentFragment extends Fragment
        implements
        BaseAlertDialog.OnClickListener,
        BaseAlertDialog.OnDialogShowListener
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentFragment.class.getSimpleName();

    private static final String TAG_TORRENT_ID = "torrent_id";
    private static final String TAG_TORRENT_INFO_CHANGED = "torrent_info_changed";
    private static final String TAG_TORRENT_FILES_CHANGED = "torrent_files_changed";
    private static final String TAG_CHILD_IN_ACTION_MODE = "child_in_action_mode";
    private static final String TAG_ADD_TRACKERS_DIALOG= "add_trackers_dialog";
    private static final String TAG_DELETE_TORRENT_DIALOG = "delete_torrent_dialog";
    private static final String TAG_SAVE_ERR_TORRENT_FILE_DIALOG = "save_err_torrent_file_dialog";
    private static final String TAG_SPEED_LIMIT_DIALOG = "speed_limit_dialog";

    private static final int SAVE_TORRENT_FILE_CHOOSE_REQUEST = 1;

    private static final int SYNC_TIME = 1000; /* ms */
    private static final int INFO_FRAG_POS = 0;
    private static final int STATE_FRAG_POS = 1;
    private static final int FILE_FRAG_POS = 2;
    private static final int TRACKERS_FRAG_POS = 3;
    private static final int PEERS_FRAG_POS = 4;
    private static final int PIECES_FRAG_POS = 5;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private ViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private TabLayout tabLayout;
    private ViewPagerAdapter adapter;
    private FloatingActionButton saveChangesButton;

    private String torrentId;
    private Torrent torrent;
    private TorrentStorage repo;
    private Intent torrentServiceIntent;
    private Messenger serviceCallback = null;
    private Messenger clientCallback = new Messenger(new CallbackHandler(this));
    private TorrentTaskServiceIPC ipc = new TorrentTaskServiceIPC();
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    /* Update the torrent status params, which aren't included in TorrentStateParcel */
    private Handler updateTorrentState;

    private boolean isTorrentInfoChanged = false;
    private boolean isTorrentFilesChanged = false;
    /* One of the fragments in ViewPagerAdapter is in ActionMode */
    private boolean childInActionMode = false;

    private Exception sentError;

    /*
     * Caching data, if a fragment in tab isn't into view,
     * thereby preventing a useless update data in hidden fragments
     */
    private TorrentMetaInfo infoCache;
    private TorrentStateParcel stateCache;
    private int activeTimeCache, seedingTimeCache;
    private StateParcelCache<TrackerStateParcel> trackersCache = new StateParcelCache<>();
    private StateParcelCache<PeerStateParcel> peersCache = new StateParcelCache<>();
    private boolean[] piecesCache;
    private int uploadSpeedLimit = -1;
    private int downloadSpeedLimit = -1;

    public interface Callback
    {
        void onTorrentInfoChanged();

        void onTorrentInfoChangesUndone();

        void onTorrentFilesChanged();

        void onTrackersChanged(ArrayList<String> trackers, boolean replace);

        void openFile(String relativePath);
    }

    public static DetailTorrentFragment newInstance(String id)
    {
        DetailTorrentFragment fragment = new DetailTorrentFragment();
        fragment.setTorrentId(id);

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_detail_torrent, container, false);

        toolbar = (Toolbar) v.findViewById(R.id.detail_toolbar);
        coordinatorLayout = (CoordinatorLayout) v.findViewById(R.id.coordinator_layout);
        saveChangesButton = (FloatingActionButton) v.findViewById(R.id.save_changes_button);

        return v;
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
    public void onDestroy()
    {
        super.onDestroy();

        if (bound) {
            try {
                ipc.sendClientDisconnect(serviceCallback, clientCallback);

            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            getActivity().unbindService(connection);

            bound = false;
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

        Fragment fragment = getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG);

        /* Prevents leak the dialog in portrait mode */
        if (Utils.isTablet(activity.getApplicationContext()) && fragment != null) {
            ((BaseAlertDialog) fragment).dismiss();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        Utils.showColoredStatusBar_KitKat(activity);

        if (savedInstanceState != null) {
            torrentId = savedInstanceState.getString(TAG_TORRENT_ID);
            isTorrentInfoChanged = savedInstanceState.getBoolean(TAG_TORRENT_INFO_CHANGED);
            isTorrentFilesChanged = savedInstanceState.getBoolean(TAG_TORRENT_FILES_CHANGED);
            childInActionMode = savedInstanceState.getBoolean(TAG_CHILD_IN_ACTION_MODE);
        }

        if (isTorrentFilesChanged || isTorrentInfoChanged) {
            saveChangesButton.setVisibility(View.VISIBLE);
        } else {
            saveChangesButton.setVisibility(View.GONE);
        }

        saveChangesButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                saveChangesButton.hide();

                if (isTorrentInfoChanged) {
                    applyInfoChanges();
                }

                if (isTorrentFilesChanged) {
                    applyFilesChanges();
                }

                isTorrentInfoChanged = false;
                isTorrentFilesChanged = false;
            }
        });

        if (savedInstanceState != null) {
            torrentId = savedInstanceState.getString(TAG_TORRENT_ID);
        }

        repo = new TorrentStorage(activity.getApplicationContext());
        if (torrentId != null) {
            torrent = repo.getTorrentByID(torrentId);
        }

        torrentServiceIntent = new Intent(
                activity.getApplicationContext(),
                TorrentTaskService.class);
        activity.startService(torrentServiceIntent);

        activity.bindService(torrentServiceIntent,
                connection, Context.BIND_AUTO_CREATE);

        if (Utils.isTwoPane(activity.getApplicationContext())) {
            toolbar.inflateMenu(R.menu.detail_torrent);
            toolbar.setNavigationIcon(
                    ContextCompat.getDrawable(activity.getApplicationContext(),
                            R.drawable.ic_arrow_back_white_24dp));
            toolbar.setNavigationOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    onBackPressed();
                }
            });
            toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item)
                {
                    return onOptionsItemSelected(item);
                }
            });

        } else {
            toolbar.setTitle(torrent.getName());

            activity.setSupportActionBar(toolbar);
            setHasOptionsMenu(true);

            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putBoolean(TAG_TORRENT_INFO_CHANGED, isTorrentInfoChanged);
        outState.putBoolean(TAG_TORRENT_FILES_CHANGED, isTorrentFilesChanged);
        outState.putString(TAG_TORRENT_ID, torrentId);
        outState.putBoolean(TAG_CHILD_IN_ACTION_MODE, childInActionMode);

        super.onSaveInstanceState(outState);
    }

    private ServiceConnection connection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service) {
            serviceCallback = new Messenger(service);
            bound = true;
            try {
                ipc.sendClientConnect(serviceCallback, clientCallback);

            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            initRequest();
            startUpdateTorrentState();
        }

        public void onServiceDisconnected(ComponentName className) {
            serviceCallback = null;
            bound = false;
        }
    };

    public void setTorrentId(String id)
    {
        torrentId = id;
    }

    public String getTorrentId()
    {
        return torrentId;
    }

    private void initFragments()
    {
        if (viewPager != null) {
            return;
        }

        ArrayList<BencodeFileItem> files = null;
        ArrayList<Priority> priorities = null;

        if (infoCache != null) {
            files = infoCache.getFiles();
        }

        if (torrent != null && files != null) {
            /*
             * The index position in array must be
             * equal to the priority position in array
             */
            priorities = new ArrayList<>();
            List<Integer> torrentPriorities = torrent.getFilePriorities();

            for (int i = 0; i < torrentPriorities.size(); i++) {
                priorities.add(Priority.fromSwig(torrentPriorities.get(i)));
            }
        }

        DetailTorrentInfoFragment fragmentInfo = DetailTorrentInfoFragment.newInstance(torrent, infoCache);
        DetailTorrentStateFragment fragmentState = DetailTorrentStateFragment.newInstance(infoCache);
        DetailTorrentFilesFragment fragmentFiles = DetailTorrentFilesFragment.newInstance(files, priorities);
        DetailTorrentTrackersFragment fragmentTrackers = DetailTorrentTrackersFragment.newInstance();
        DetailTorrentPeersFragment fragmentPeers = DetailTorrentPeersFragment.newInstance();
        DetailTorrentPiecesFragment fragmentPieces =
                DetailTorrentPiecesFragment.newInstance(infoCache.getNumPieces(), infoCache.getPieceLength());

        viewPager = (ViewPager) activity.findViewById(R.id.detail_torrent_viewpager);

        if (viewPager == null) {
            return;
        }

        /* Removing previous ViewPagerAdapter fragments, if any */
        if (Utils.isTablet(activity.getApplicationContext())) {
            android.support.v4.app.FragmentManager fm = activity.getSupportFragmentManager();
            List<android.support.v4.app.Fragment> fragments = fm.getFragments();

            if (fragments != null) {
                FragmentTransaction ft = fm.beginTransaction();
                for (android.support.v4.app.Fragment f : fragments) {
                    if (f != null) {
                        ft.remove(f);
                    }
                }

                ft.commitAllowingStateLoss();
            }
        }

        adapter = new ViewPagerAdapter(activity.getSupportFragmentManager());
        adapter.addFragment(fragmentInfo, INFO_FRAG_POS, getString(R.string.torrent_info));
        adapter.addFragment(fragmentState, STATE_FRAG_POS, getString(R.string.torrent_state));
        adapter.addFragment(fragmentFiles, FILE_FRAG_POS, getString(R.string.torrent_files));
        adapter.addFragment(fragmentTrackers, TRACKERS_FRAG_POS, getString(R.string.torrent_trackers));
        adapter.addFragment(fragmentPeers, PEERS_FRAG_POS, getString(R.string.torrent_peers));
        adapter.addFragment(fragmentPieces, PIECES_FRAG_POS, getString(R.string.torrent_pieces));
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(6);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener()
        {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
            {
                /* Nothing */
            }

            @Override
            public void onPageSelected(int position)
            {
                switch (position) {
                    case STATE_FRAG_POS:
                        DetailTorrentStateFragment stateFrag =
                                (DetailTorrentStateFragment) adapter.getItem(STATE_FRAG_POS);
                        if (stateFrag != null) {
                            stateFrag.setState(stateCache);
                            stateFrag.setActiveAndSeedingTime(activeTimeCache, seedingTimeCache);
                        }
                        break;
                    case FILE_FRAG_POS:
                        DetailTorrentFilesFragment fileFrag =
                                (DetailTorrentFilesFragment) adapter.getItem(FILE_FRAG_POS);
                        if (fileFrag != null) {
                            fileFrag.setFilesReceivedBytes(stateCache.filesReceivedBytes);
                        }
                        break;
                    case TRACKERS_FRAG_POS:
                        DetailTorrentTrackersFragment trackersFrag =
                                (DetailTorrentTrackersFragment) adapter.getItem(TRACKERS_FRAG_POS);
                        if (trackersFrag != null) {
                            trackersFrag.setTrackersList(new ArrayList<>(trackersCache.getAll()));
                        }
                        break;
                    case PEERS_FRAG_POS:
                        DetailTorrentPeersFragment peersFrag =
                                (DetailTorrentPeersFragment) adapter.getItem(PEERS_FRAG_POS);
                        if (peersFrag != null) {
                            peersFrag.setPeerList(new ArrayList<>(peersCache.getAll()));
                        }
                        break;
                    case PIECES_FRAG_POS:
                        DetailTorrentPiecesFragment piecesFrag =
                                (DetailTorrentPiecesFragment) adapter.getItem(PIECES_FRAG_POS);

                        if (piecesFrag != null) {
                            piecesFrag.setPieces(piecesCache);
                            piecesFrag.setDownloadedPiecesCount(stateCache.downloadedPieces);
                        }
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state)
            {
                /* Nothing */
            }
        });
        tabLayout = (TabLayout) activity.findViewById(R.id.detail_torrent_tabs);

        int colorId = (childInActionMode ? R.color.action_mode : R.color.primary);
        setTabLayoutColor(colorId);

        tabLayout.setupWithViewPager(viewPager);

        if (bound && serviceCallback != null) {
            try {
                ipc.sendTorrentStateOneShot(serviceCallback, clientCallback);

            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    public void onTorrentInfoChanged()
    {
        isTorrentInfoChanged = true;

        saveChangesButton.show();
    }

    public void onTorrentInfoChangesUndone()
    {
        isTorrentInfoChanged = false;

        saveChangesButton.hide();
    }

    public void onTorrentFilesChanged()
    {
        isTorrentFilesChanged = true;

        saveChangesButton.show();
    }

    private void applyInfoChanges()
    {
        DetailTorrentInfoFragment infoFrag = (DetailTorrentInfoFragment) adapter.getItem(INFO_FRAG_POS);
        DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment) adapter.getItem(FILE_FRAG_POS);

        if (infoFrag == null || fileFrag == null) {
            return;
        }

        String name = infoFrag.getTorrentName();
        String path = infoFrag.getDownloadPath();
        boolean sequential = infoFrag.isSequentialDownload();

        if (FileIOUtils.getFreeSpace(path) < fileFrag.getSeletedFileSize()) {
            Snackbar snackbar = Snackbar.make(coordinatorLayout,
                    R.string.error_free_space,
                    Snackbar.LENGTH_LONG);
            snackbar.show();

            return;
        }

        if (!name.equals(torrent.getName())) {
            try {
                ipc.sendSetTorrentName(serviceCallback, torrentId, name);
                torrent.setName(name);
                infoFrag.setTorrentName(name);

            } catch (RemoteException e) {
                /* Ignore */
            }
        }

        if (!path.equals(torrent.getDownloadPath())) {
            try {
                ArrayList<String> list = new ArrayList<>();
                list.add(torrentId);

                ipc.sendSetDownloadPath(serviceCallback, list, path);
                torrent.setDownloadPath(path);
                infoFrag.setDownloadPath(path);

            } catch (RemoteException e) {
                /* Ignore */
            }
        }

        if (sequential != torrent.isSequentialDownload()) {
            try {
                ipc.sendSetSequentialDownload(serviceCallback, torrentId, sequential);
                torrent.setSequentialDownload(sequential);
                infoFrag.setSequentialDownload(sequential);

            } catch (RemoteException e) {
                /* Ignore */
            }
        }
    }

    private void applyFilesChanges()
    {
        DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment) adapter.getItem(FILE_FRAG_POS);

        if (fileFrag == null) {
            return;
        }

        Priority[] priorities = fileFrag.getPriorities();

        if (priorities != null) {
            ArrayList<Integer> list = new ArrayList<>();

            for (Priority priority : priorities) {
                if (priority != null) {
                    list.add(priority.swig());
                }
            }

            fileFrag.disableSelectedFiles();

            changeFilesPriorityRequest(list);
        }
    }

    /*
     * Repaint TabLayout in ActionMode color if it created.
     */

    private void setTabLayoutColor(int colorId)
    {
        if (tabLayout == null) {
            return;
        }

        Utils.setBackground(tabLayout,
                ContextCompat.getDrawable(activity.getApplicationContext(), colorId));
    }

    public void onTrackersChanged(ArrayList<String> trackers, boolean replace)
    {
        trackersCache.clear();
        addTrackersRequest(trackers, replace);
    }

    public void showSnackbar(String text, int length)
    {
        Snackbar.make(coordinatorLayout,
                text,
                length)
                .show();
    }

    public void openFile(String relativePath)
    {
        if (relativePath == null) {
            return;
        }

        String path = torrent.getDownloadPath() + File.separator + relativePath;

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        /* Give user a choice than to open file (without determining MIME type) */
        intent.setDataAndType(Uri.fromFile(new File(path)), "*/*");
        startActivity(Intent.createChooser(intent, getString(R.string.open_using)));
    }

    private void torrentSaveChooseDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(FileIOUtils.getUserDirPath(),
                null,
                null,
                FileManagerConfig.DIR_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, SAVE_TORRENT_FILE_CHOOSE_REQUEST);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.detail_torrent, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);

        prepareOptionsMenu(menu);
    }

    private void prepareOptionsMenu(Menu menu)
    {
        MenuItem pauseResume = menu.findItem(R.id.pause_resume_torrent_menu);

        if (torrent == null || !torrent.isPaused()) {
            pauseResume.setTitle(R.string.pause_torrent);
            if (!Utils.isTwoPane(activity.getApplicationContext())) {
                pauseResume.setIcon(R.drawable.ic_pause_white_24dp);
            }

        } else {
            pauseResume.setTitle(R.string.resume_torrent);
            if (!Utils.isTwoPane(activity.getApplicationContext())) {
                pauseResume.setIcon(R.drawable.ic_play_arrow_white_24dp);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.pause_resume_torrent_menu:
                pauseResumeTorrentRequest();
                break;
            case R.id.delete_torrent_menu:
                if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
                    BaseAlertDialog deleteTorrentDialog = BaseAlertDialog.newInstance(
                            getString(R.string.deleting),
                            getString(R.string.delete_selected_torrent),
                            R.layout.dialog_delete_torrent,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            R.style.BaseTheme_Dialog,
                            DetailTorrentFragment.this);

                    deleteTorrentDialog.show(getFragmentManager(), TAG_DELETE_TORRENT_DIALOG);
                }
                break;
            case R.id.force_recheck_torrent_menu:
                forceRecheckRequest();
                break;
            case R.id.force_announce_torrent_menu:
                forceAnnounceRequest();
                break;
            case R.id.share_magnet_menu:
                getMagnetRequest();
                break;
            case R.id.save_torrent_file_menu:
                torrentSaveChooseDialog();
                break;
            case R.id.add_trackers_menu:
                if (getFragmentManager().findFragmentByTag(TAG_ADD_TRACKERS_DIALOG) == null) {
                    BaseAlertDialog addTrackersDialog = BaseAlertDialog.newInstance(
                            getString(R.string.add_trackers),
                            getString(R.string.dialog_add_trackers),
                            R.layout.dialog_multiline_text_input,
                            getString(R.string.add),
                            getString(R.string.replace),
                            getString(R.string.cancel),
                            R.style.BaseTheme_Dialog,
                            DetailTorrentFragment.this);

                    addTrackersDialog.show(getFragmentManager(), TAG_ADD_TRACKERS_DIALOG);
                }
                break;
            case R.id.torrent_speed_limit:
                if (getFragmentManager().findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) == null) {
                    BaseAlertDialog speedLimitDialog = BaseAlertDialog.newInstance(
                            getString(R.string.speed_limit_title),
                            getString(R.string.speed_limit_dialog),
                            R.layout.dialog_speed_limit,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            R.style.BaseTheme_Dialog,
                            DetailTorrentFragment.this);

                    speedLimitDialog.show(getFragmentManager(), TAG_SPEED_LIMIT_DIALOG);
                }
                break;
        }

        return true;
    }

    @Override
    public void onShow(final AlertDialog dialog)
    {
        if (dialog == null) {
            return;
        }

        if (getFragmentManager().findFragmentByTag(TAG_ADD_TRACKERS_DIALOG) != null) {
            final TextInputEditText field =
                    (TextInputEditText) dialog.findViewById(R.id.multiline_text_input_dialog);
            final TextInputLayout fieldLayout =
                    (TextInputLayout) dialog.findViewById(R.id.layout_multiline_text_input_dialog);

            /* Dismiss error label if user has changed the text */
            if (field != null && fieldLayout != null) {
                field.addTextChangedListener(new TextWatcher()
                {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after)
                    {
                        /* Nothing */
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count)
                    {
                        fieldLayout.setErrorEnabled(false);
                        fieldLayout.setError(null);

                        /* Clear selection of invalid url */
                        Spannable text = field.getText();
                        ForegroundColorSpan[] errorSpans = text.getSpans(0, text.length(),
                                ForegroundColorSpan.class);
                        for (ForegroundColorSpan span : errorSpans) {
                            text.removeSpan(span);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s)
                    {
                        /* Nothing */
                    }
                });
            }

            /*
             * It is necessary in order to the dialog is not closed by
             * pressing add/replace button if the text checker gave a false result
             */
            Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button replaceButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            addButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (field != null && fieldLayout != null) {
                        String text = field.getText().toString();
                        List<String> urls = Arrays.asList(text.split(Utils.getLineSeparator()));

                        if (checkEditTextField(urls, fieldLayout, field)) {
                            addTrackersRequest(new ArrayList<>(urls), false);

                            dialog.dismiss();
                        }
                    }
                }
            });

            replaceButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (field != null && fieldLayout != null) {
                        String text = field.getText().toString();
                        List<String> urls = Arrays.asList(text.split(Utils.getLineSeparator()));

                        if (checkEditTextField(urls, fieldLayout, field)) {
                            addTrackersRequest(new ArrayList<>(urls), true);

                            dialog.dismiss();
                        }
                    }
                }
            });

            /* Inserting links from the clipboard */
            String clipboard = Utils.getClipboard(activity.getApplicationContext());

            if (clipboard != null && field != null) {
                List<String> urls = Arrays.asList(clipboard.split(Utils.getLineSeparator()));
                ArrayList<String> validUrls = new ArrayList<>();

                for (String url : urls) {
                    if (Utils.isValidTrackerUrl(url)) {
                        validUrls.add(url);
                    }
                }

                field.setText(TextUtils.join(Utils.getLineSeparator(), validUrls));
            }

        } else if (getFragmentManager().findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) != null) {
            TextInputEditText upload = (TextInputEditText) dialog.findViewById(R.id.upload_limit);
            TextInputEditText download = (TextInputEditText) dialog.findViewById(R.id.download_limit);

            if (upload != null && download != null) {
                int minSpeedLimit = 0;
                int maxSpeedLimit = Integer.MAX_VALUE;
                InputFilter[] filter = new InputFilter[]{ new InputFilterMinMax(minSpeedLimit, maxSpeedLimit) };

                upload.setFilters(filter);
                if (TextUtils.isEmpty(upload.getText())) {
                    upload.setText((uploadSpeedLimit != -1 ?
                            Integer.toString(uploadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));
                }

                download.setFilters(filter);
                if (TextUtils.isEmpty(download.getText())) {
                    download.setText((downloadSpeedLimit != -1 ?
                            Integer.toString(downloadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));
                }
            }
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (v != null) {
            if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
                CheckBox withFiles = (CheckBox) v.findViewById(R.id.dialog_delete_torrent_with_downloaded_files);
                deleteTorrentRequest(withFiles.isChecked());

                finish(new Intent(), FragmentCallback.ResultCode.CANCEL);

            } else if (getFragmentManager().findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) != null) {
                TextInputEditText upload = (TextInputEditText) v.findViewById(R.id.upload_limit);
                TextInputEditText download = (TextInputEditText) v.findViewById(R.id.download_limit);

                uploadSpeedLimit = Integer.parseInt(upload.getText().toString()) * 1024;
                downloadSpeedLimit = Integer.parseInt(download.getText().toString()) * 1024;

                setSpeedLimitRequest(uploadSpeedLimit, downloadSpeedLimit);

            } else if (getFragmentManager().findFragmentByTag(TAG_SAVE_ERR_TORRENT_FILE_DIALOG) != null) {
                if (sentError != null) {
                    EditText editText = (EditText) v.findViewById(R.id.comment);
                    String comment = editText.getText().toString();

                    Utils.reportError(sentError, comment);
                }
            }
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

    private boolean checkEditTextField(List<String> strings,
                                       TextInputLayout layout,
                                       TextInputEditText field)
    {
        if (strings == null || layout == null) {
            return false;
        }

        if (strings.isEmpty()) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        boolean valid = true;
        int curLineStartIndex = 0;
        for (String s : strings) {
            if (!Utils.isValidTrackerUrl(s)) {
                /* Select invalid url */
                field.getText().setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(activity.getApplicationContext(), R.color.error)),
                        curLineStartIndex,
                        curLineStartIndex + s.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                valid = false;
            }
            /* Considering newline char */
            curLineStartIndex += s.length() + 1;
        }

        if (valid) {
            layout.setErrorEnabled(false);
            layout.setError(null);
        } else {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_invalid_link));
            layout.requestFocus();
        }

        return valid;
    }

    private void saveErrorTorrentFileDialog(Exception e)
    {
        sentError = e;

        if (getFragmentManager().findFragmentByTag(TAG_SAVE_ERR_TORRENT_FILE_DIALOG) == null) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_save_torrent_file),
                    (e != null ? Log.getStackTraceString(e) : null),
                    R.style.BaseTheme_Dialog,
                    this);

            errDialog.show(getFragmentManager(), TAG_SAVE_ERR_TORRENT_FILE_DIALOG);
        }
    }

    private void initRequest()
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendGetTorrentInfo(serviceCallback, clientCallback, torrentId);
            ipc.sendGetSpeedLimit(serviceCallback, clientCallback, torrentId);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void changeFilesPriorityRequest(ArrayList<Integer> priorities)
    {
        if (!bound || serviceCallback == null || priorities == null || torrentId == null) {
            return;
        }

        try {
            ipc.sendChangeFilesPriority(serviceCallback, torrentId, priorities);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void addTrackersRequest(ArrayList<String> trackers, boolean replace)
    {
        if (!bound || serviceCallback == null || trackers == null || torrentId == null) {
            return;
        }

        try {
            if (replace) {
                ipc.sendReplaceTrackers(serviceCallback, torrentId, trackers);
            } else {
                ipc.sendAddTrackers(serviceCallback, torrentId, trackers);
            }

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void deleteTorrentRequest(boolean withFiles)
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        try {
            ipc.sendDeleteTorrents(serviceCallback, list, withFiles);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void forceRecheckRequest()
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        try {
            ipc.sendForceRecheck(serviceCallback, list);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void forceAnnounceRequest()
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        try {
            ipc.sendForceAnnounce(serviceCallback, list);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void pauseResumeTorrentRequest()
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        try {
            ipc.sendPauseResumeTorrents(serviceCallback, list);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void getMagnetRequest()
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendGetMagnet(serviceCallback, clientCallback, torrentId);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void setSpeedLimitRequest(int uploadSpeedLimit, int downloadSpeedLimit)
    {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendSetUploadSpeedLimit(serviceCallback, torrentId, uploadSpeedLimit);
            ipc.sendSetDownloadSpeedLimit(serviceCallback, torrentId, downloadSpeedLimit);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void startUpdateTorrentState()
    {
        if (updateTorrentState != null) {
            return;
        }

        updateTorrentState = new Handler();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                if (bound && serviceCallback != null && torrentId != null) {
                    try {
                        ipc.sendGetActiveAndSeedingTime(serviceCallback, clientCallback, torrentId);
                        ipc.sendGetTrackersStates(serviceCallback, clientCallback, torrentId);
                        ipc.sendGetPeersStates(serviceCallback, clientCallback, torrentId);
                        ipc.sendGetPieces(serviceCallback, clientCallback, torrentId);

                    } catch (RemoteException e) {
                        /* Ignore */
                    }
                }
                updateTorrentState.postDelayed(this, SYNC_TIME);
            }
        };
        updateTorrentState.postDelayed(r, SYNC_TIME);
    }

    static class CallbackHandler extends Handler
    {
        WeakReference<DetailTorrentFragment> fragment;

        public CallbackHandler(DetailTorrentFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle b;

            try {
                switch (msg.what) {
                    case TorrentTaskServiceIPC.UPDATE_STATES_ONESHOT: {
                        b = msg.getData();
                        b.setClassLoader(TorrentStateParcel.class.getClassLoader());

                        Bundle states = b.getParcelable(TorrentTaskServiceIPC.TAG_STATES_LIST);
                        if (states != null) {
                            TorrentStateParcel state = states.getParcelable(fragment.get().torrentId);

                            if (state != null) {
                                fragment.get().stateCache = state;

                                if (fragment.get().torrent != null) {
                                    if (state.stateCode == TorrentStateCode.PAUSED ||
                                            fragment.get().torrent.isPaused()) {

                                        fragment.get()
                                                .torrent
                                                .setPaused(state.stateCode == TorrentStateCode.PAUSED);

                                        /* Redraw pause/resume menu */
                                        if (Utils.isTwoPane(fragment.get().activity.getApplicationContext())) {
                                            fragment.get().prepareOptionsMenu(fragment.get().toolbar.getMenu());
                                        } else {
                                            fragment.get().activity.invalidateOptionsMenu();
                                        }
                                    }
                                }

                                switch (fragment.get().viewPager.getCurrentItem()) {
                                    case STATE_FRAG_POS:
                                        DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)
                                                fragment.get().adapter.getItem(STATE_FRAG_POS);

                                        if (stateFrag != null) {
                                            stateFrag.setState(state);
                                        }
                                        break;
                                    case FILE_FRAG_POS:
                                        DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)
                                                fragment.get().adapter.getItem(FILE_FRAG_POS);

                                        if (fileFrag != null) {
                                            fileFrag.setFilesReceivedBytes(state.filesReceivedBytes);
                                        }
                                        break;
                                    case PIECES_FRAG_POS:
                                        DetailTorrentPiecesFragment piecesFrag = (DetailTorrentPiecesFragment)
                                                fragment.get().adapter.getItem(PIECES_FRAG_POS);

                                        if (piecesFrag != null) {
                                            piecesFrag.setDownloadedPiecesCount(state.downloadedPieces);
                                        }
                                        break;
                                }
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.UPDATE_STATE: {
                        b = msg.getData();
                        b.setClassLoader(TorrentStateParcel.class.getClassLoader());
                        TorrentStateParcel state = b.getParcelable(TorrentTaskServiceIPC.TAG_STATE);

                        if (state != null) {
                            fragment.get().stateCache = state;

                            if (fragment.get().torrent != null) {
                                if (state.stateCode == TorrentStateCode.PAUSED ||
                                        fragment.get().torrent.isPaused()) {

                                    fragment.get()
                                            .torrent
                                            .setPaused(state.stateCode == TorrentStateCode.PAUSED);

                                    /* Redraw pause/resume menu */
                                    if (Utils.isTwoPane(fragment.get().activity.getApplicationContext())) {
                                        fragment.get().prepareOptionsMenu(fragment.get().toolbar.getMenu());
                                    } else {
                                        fragment.get().activity.invalidateOptionsMenu();
                                    }
                                }
                            }

                            switch (fragment.get().viewPager.getCurrentItem()) {
                                case STATE_FRAG_POS:
                                    DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)
                                            fragment.get().adapter.getItem(STATE_FRAG_POS);

                                    if (stateFrag != null) {
                                        stateFrag.setState(state);
                                    }
                                    break;
                                case FILE_FRAG_POS:
                                    DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)
                                            fragment.get().adapter.getItem(FILE_FRAG_POS);

                                    if (fileFrag != null) {
                                        fileFrag.setFilesReceivedBytes(state.filesReceivedBytes);
                                    }
                                    break;
                                case PIECES_FRAG_POS:
                                    DetailTorrentPiecesFragment piecesFrag = (DetailTorrentPiecesFragment)
                                            fragment.get().adapter.getItem(PIECES_FRAG_POS);

                                    if (piecesFrag != null) {
                                        piecesFrag.setDownloadedPiecesCount(state.downloadedPieces);
                                    }
                                    break;
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_TORRENT_INFO: {
                        b = msg.getData();
                        b.setClassLoader(TorrentMetaInfo.class.getClassLoader());
                        fragment.get().infoCache = b.getParcelable(TorrentTaskServiceIPC.TAG_TORRENT_INFO);
                        fragment.get().initFragments();
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_ACTIVE_AND_SEEDING_TIME: {
                        int activeTime = msg.arg1;
                        int seedingTime = msg.arg2;
                        fragment.get().activeTimeCache = activeTime;
                        fragment.get().seedingTimeCache = seedingTime;

                        if (fragment.get().viewPager.getCurrentItem() == STATE_FRAG_POS) {
                            DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)
                                    fragment.get().adapter.getItem(STATE_FRAG_POS);

                            if (stateFrag != null) {
                                stateFrag.setActiveAndSeedingTime(activeTime, seedingTime);
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_TRACKERS_STATES: {
                        b = msg.getData();
                        b.setClassLoader(TrackerStateParcel.class.getClassLoader());
                        ArrayList<TrackerStateParcel> states =
                                b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_TRACKERS_STATES_LIST);

                        if (!fragment.get().trackersCache.containsAll(states)) {
                            fragment.get().trackersCache.clear();
                            fragment.get().trackersCache.putAll(states);

                            if (fragment.get().viewPager.getCurrentItem() == TRACKERS_FRAG_POS) {
                                DetailTorrentTrackersFragment trackersFrag = (DetailTorrentTrackersFragment)
                                        fragment.get().adapter.getItem(TRACKERS_FRAG_POS);

                                if (trackersFrag != null) {
                                    trackersFrag.setTrackersList(states);
                                }
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_PEERS_STATES: {
                        b = msg.getData();
                        b.setClassLoader(PeerStateParcel.class.getClassLoader());
                        ArrayList<PeerStateParcel> states =
                                b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_PEERS_STATES_LIST);

                        if (!fragment.get().peersCache.containsAll(states) || states.isEmpty()) {
                            fragment.get().peersCache.clear();
                            fragment.get().peersCache.putAll(states);

                            if (fragment.get().viewPager.getCurrentItem() == PEERS_FRAG_POS) {
                                DetailTorrentPeersFragment peersFrag = (DetailTorrentPeersFragment)
                                        fragment.get().adapter.getItem(PEERS_FRAG_POS);

                                if (peersFrag != null) {
                                    peersFrag.setPeerList(states);
                                }
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_PIECES: {
                        b = msg.getData();
                        boolean[] pieces = b.getBooleanArray(TorrentTaskServiceIPC.TAG_PIECE_LIST);

                        if (!Arrays.equals(fragment.get().piecesCache, pieces)) {
                            fragment.get().piecesCache = pieces;

                            if (fragment.get().viewPager.getCurrentItem() == PIECES_FRAG_POS) {
                                DetailTorrentPiecesFragment piecesFrag = (DetailTorrentPiecesFragment)
                                        fragment.get().adapter.getItem(PIECES_FRAG_POS);

                                if (piecesFrag != null) {
                                    piecesFrag.setPieces(pieces);
                                }
                            }
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_MAGNET: {
                        String magnet = msg.getData().getString(TorrentTaskServiceIPC.TAG_MAGNET);

                        if (magnet != null) {
                            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "magnet");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, magnet);
                            fragment.get().startActivity(
                                    Intent.createChooser(sharingIntent,
                                            fragment.get().getString(R.string.share_via)));
                        }
                        break;
                    }
                    case TorrentTaskServiceIPC.GET_SPEED_LIMIT: {
                        fragment.get().uploadSpeedLimit = msg.arg1;
                        fragment.get().downloadSpeedLimit = msg.arg2;
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                }

            } catch (Exception e) {
                /* Ignore */
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == SAVE_TORRENT_FILE_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                String path = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);

                if (path != null && torrentId != null && torrent != null) {
                    try {
                        if (TorrentUtils.copyTorrentFile(activity.getApplicationContext(),
                                torrentId, path, torrent.getName() + ".torrent")) {
                            showSnackbar(getString(R.string.save_torrent_file_successfully),
                                    Snackbar.LENGTH_SHORT);
                        } else {
                            saveErrorTorrentFileDialog(null);
                        }

                    } catch (IOException e) {
                        saveErrorTorrentFileDialog(e);
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
