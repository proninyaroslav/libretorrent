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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
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

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.ViewPagerAdapter;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.receivers.TorrentTaskServiceReceiver;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.StateParcelCache;
import org.proninyaroslav.libretorrent.core.TorrentStateMsg;
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
    private static final String TAG_CURRENT_FRAG_POS = "current_frag_pos";

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
    private TorrentTaskService service;
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    /* Update the torrent status params, which aren't included in BasicStateParcel */
    private Handler updateTorrentStateHandler = new Handler();
    Runnable updateTorrentState = new Runnable()
    {
        @Override
        public void run()
        {
            if (bound && service != null && torrentId != null) {
                getAdvancedStateRequest();
                getTrackersStatesRequest();
                getPeerStatesRequest();
                getPiecesRequest();
            }
            updateTorrentStateHandler.postDelayed(this, SYNC_TIME);
        }
    };

    private boolean isTorrentInfoChanged = false;
    private boolean isTorrentFilesChanged = false;
    /* One of the fragments in ViewPagerAdapter is in ActionMode */
    private boolean childInActionMode = false;
    private int currentFragPos = INFO_FRAG_POS;
    private Exception sentError;
    /*
     * Caching data, if a fragment in tab isn't into view,
     * thereby preventing a useless update data in hidden fragments
     */
    private TorrentMetaInfo infoCache;
    private BasicStateParcel basicStateCache;
    private AdvanceStateParcel advanceStateCache;
    private StateParcelCache<TrackerStateParcel> trackersCache = new StateParcelCache<>();
    private StateParcelCache<PeerStateParcel> peersCache = new StateParcelCache<>();
    private boolean[] piecesCache;
    private int uploadSpeedLimit = -1;
    private int downloadSpeedLimit = -1;
    private boolean downloadingMetadata = false;

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
        fragment.torrentId = id;

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_detail_torrent, container, false);

        toolbar = v.findViewById(R.id.detail_toolbar);
        coordinatorLayout = v.findViewById(R.id.coordinator_layout);
        saveChangesButton = v.findViewById(R.id.save_changes_button);
        viewPager = v.findViewById(R.id.detail_torrent_viewpager);
        tabLayout = v.findViewById(R.id.detail_torrent_tabs);

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
    public void onStart()
    {
        super.onStart();

        activity.bindService(new Intent(activity.getApplicationContext(), TorrentTaskService.class),
                connection, Context.BIND_AUTO_CREATE);
        if (!TorrentTaskServiceReceiver.getInstance().isRegistered(serviceReceiver))
            TorrentTaskServiceReceiver.getInstance().register(serviceReceiver);
    }

    @Override
    public void onStop()
    {
        super.onStop();

        TorrentTaskServiceReceiver.getInstance().unregister(serviceReceiver);
        stopUpdateTorrentState();
        if (bound) {
            getActivity().unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            Fragment fragment = fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG);
            /* Prevents leak the dialog in portrait mode */
            if (Utils.isLargeScreenDevice(activity.getApplicationContext()) && fragment != null)
                ((BaseAlertDialog)fragment).dismiss();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        Utils.showColoredStatusBar_KitKat(activity);

        adapter = new ViewPagerAdapter(activity.getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(6);
        viewPager.addOnPageChangeListener(viewPagerListener);
        int colorId = (childInActionMode ? R.color.action_mode : R.color.primary);
        setTabLayoutColor(colorId);
        tabLayout.setupWithViewPager(viewPager);

        if (savedInstanceState != null) {
            torrentId = savedInstanceState.getString(TAG_TORRENT_ID);
            isTorrentInfoChanged = savedInstanceState.getBoolean(TAG_TORRENT_INFO_CHANGED);
            isTorrentFilesChanged = savedInstanceState.getBoolean(TAG_TORRENT_FILES_CHANGED);
            childInActionMode = savedInstanceState.getBoolean(TAG_CHILD_IN_ACTION_MODE);
            currentFragPos = savedInstanceState.getInt(TAG_CURRENT_FRAG_POS);
        }

        if (isTorrentFilesChanged || isTorrentInfoChanged)
            saveChangesButton.show();
        else
            saveChangesButton.hide();

        saveChangesButton.setOnClickListener((View view) -> {
            saveChangesButton.hide();

            if (isTorrentInfoChanged)
                applyInfoChanges();

            if (isTorrentFilesChanged)
                applyFilesChanges();

            isTorrentInfoChanged = false;
            isTorrentFilesChanged = false;
        });

        if (savedInstanceState != null)
            torrentId = savedInstanceState.getString(TAG_TORRENT_ID);

        repo = new TorrentStorage(activity.getApplicationContext());
        if (torrentId != null)
            torrent = repo.getTorrentByID(torrentId);

        downloadingMetadata = torrent != null && torrent.isDownloadingMetadata();
        if (Utils.isTwoPane(activity.getApplicationContext())) {
            toolbar.inflateMenu(R.menu.detail_torrent);
            toolbar.setNavigationIcon(ContextCompat.getDrawable(activity.getApplicationContext(),
                                                                R.drawable.ic_arrow_back_white_24dp));
            toolbar.setNavigationOnClickListener((View view) -> onBackPressed());
            toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        } else {
            if (torrent != null)
                toolbar.setTitle(torrent.getName());

            activity.setSupportActionBar(toolbar);
            setHasOptionsMenu(true);

            if (activity.getSupportActionBar() != null)
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putBoolean(TAG_TORRENT_INFO_CHANGED, isTorrentInfoChanged);
        outState.putBoolean(TAG_TORRENT_FILES_CHANGED, isTorrentFilesChanged);
        outState.putString(TAG_TORRENT_ID, torrentId);
        outState.putBoolean(TAG_CHILD_IN_ACTION_MODE, childInActionMode);
        outState.putInt(TAG_CURRENT_FRAG_POS, currentFragPos);

        super.onSaveInstanceState(outState);
    }

    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            TorrentTaskService.LocalBinder binder = (TorrentTaskService.LocalBinder) service;
            DetailTorrentFragment.this.service = binder.getService();
            bound = true;
            initRequest();
            initFragments();
            startUpdateTorrentState();
        }

        public void onServiceDisconnected(ComponentName className)
        {
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
        if (!isAdded() || adapter.getCount() > 0)
            return;

        DetailTorrentInfoFragment fragmentInfo = DetailTorrentInfoFragment.newInstance(torrent, infoCache);
        DetailTorrentStateFragment fragmentState = DetailTorrentStateFragment.newInstance(infoCache);
        DetailTorrentFilesFragment fragmentFiles =
                DetailTorrentFilesFragment.newInstance(torrentId, getFileList(), getPrioritiesList());
        DetailTorrentTrackersFragment fragmentTrackers = DetailTorrentTrackersFragment.newInstance();
        DetailTorrentPeersFragment fragmentPeers = DetailTorrentPeersFragment.newInstance();
        DetailTorrentPiecesFragment fragmentPieces = (infoCache != null ?
                DetailTorrentPiecesFragment.newInstance(infoCache.numPieces, infoCache.pieceLength) :
                DetailTorrentPiecesFragment.newInstance(0, 0));

        /* Removing previous ViewPagerAdapter fragments, if any */
        if (Utils.isLargeScreenDevice(activity.getApplicationContext())) {
            FragmentManager fm = activity.getSupportFragmentManager();
            List<Fragment> fragments = fm.getFragments();
            FragmentTransaction ft = fm.beginTransaction();
            for (Fragment f : fragments)
                if (f != null)
                    ft.remove(f);
            ft.commitAllowingStateLoss();
        }

        adapter.addFragment(fragmentInfo, INFO_FRAG_POS, getString(R.string.torrent_info));
        adapter.addFragment(fragmentState, STATE_FRAG_POS, getString(R.string.torrent_state));
        adapter.addFragment(fragmentFiles, FILE_FRAG_POS, getString(R.string.torrent_files));
        adapter.addFragment(fragmentTrackers, TRACKERS_FRAG_POS, getString(R.string.torrent_trackers));
        adapter.addFragment(fragmentPeers, PEERS_FRAG_POS, getString(R.string.torrent_peers));
        adapter.addFragment(fragmentPieces, PIECES_FRAG_POS, getString(R.string.torrent_pieces));

        adapter.notifyDataSetChanged();
        viewPager.setCurrentItem(currentFragPos);
    }

    ViewPager.OnPageChangeListener viewPagerListener = new ViewPager.OnPageChangeListener()
    {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
        {
            /* Nothing */
        }

        @Override
        public void onPageSelected(int position)
        {
            currentFragPos = position;
            switch (position) {
                case STATE_FRAG_POS:
                    DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)adapter.getItem(STATE_FRAG_POS);
                    if (stateFrag != null && basicStateCache != null && advanceStateCache != null)
                        stateFrag.setStates(basicStateCache, advanceStateCache);
                    break;
                case FILE_FRAG_POS:
                    DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)adapter.getItem(FILE_FRAG_POS);
                    if (fileFrag != null && advanceStateCache != null)
                        fileFrag.updateFiles(advanceStateCache.filesReceivedBytes,
                                             advanceStateCache.filesAvailability);
                    break;
                case TRACKERS_FRAG_POS:
                    DetailTorrentTrackersFragment trackersFrag = (DetailTorrentTrackersFragment)adapter.getItem(TRACKERS_FRAG_POS);
                    if (trackersFrag != null && trackersCache != null)
                        trackersFrag.setTrackersList(new ArrayList<>(trackersCache.getAll()));
                    break;
                case PEERS_FRAG_POS:
                    DetailTorrentPeersFragment peersFrag = (DetailTorrentPeersFragment)adapter.getItem(PEERS_FRAG_POS);
                    if (peersFrag != null && piecesCache != null)
                        peersFrag.setPeerList(new ArrayList<>(peersCache.getAll()));
                    break;
                case PIECES_FRAG_POS:
                    DetailTorrentPiecesFragment piecesFrag = (DetailTorrentPiecesFragment)adapter.getItem(PIECES_FRAG_POS);
                    if (piecesFrag != null && advanceStateCache != null) {
                        piecesFrag.setPieces(piecesCache);
                        piecesFrag.setDownloadedPiecesCount(advanceStateCache.downloadedPieces);
                    }
                    break;
            }
        }

        @Override
        public void onPageScrollStateChanged(int state)
        {
            /* Nothing */
        }
    };

    private ArrayList<BencodeFileItem> getFileList()
    {
        return (infoCache != null ? infoCache.fileList : new ArrayList<>());
    }

    private List<Priority>  getPrioritiesList()
    {
        if (torrent == null)
            return null;

        return torrent.getFilePriorities();
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
        DetailTorrentInfoFragment infoFrag = (DetailTorrentInfoFragment)adapter.getItem(INFO_FRAG_POS);
        DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)adapter.getItem(FILE_FRAG_POS);

        if (infoFrag == null || fileFrag == null)
            return;

        String name = infoFrag.getTorrentName();
        String path = infoFrag.getDownloadPath();
        boolean sequential = infoFrag.isSequentialDownload();

        if (FileIOUtils.getFreeSpace(path) < fileFrag.getSelectedFileSize()) {
            Snackbar snackbar = Snackbar.make(coordinatorLayout,
                    R.string.error_free_space,
                    Snackbar.LENGTH_LONG);
            snackbar.show();

            return;
        }

        if (service != null) {
            if (!name.equals(torrent.getName())) {
                service.setTorrentName(torrentId, name);
                torrent.setName(name);
            }

            if (!path.equals(torrent.getDownloadPath())) {
                ArrayList<String> list = new ArrayList<>();
                list.add(torrentId);

                service.setTorrentDownloadPath(list, path);
                torrent.setDownloadPath(path);
                infoFrag.setDownloadPath(path);
            }

            if (sequential != torrent.isSequentialDownload()) {
                service.setSequentialDownload(torrentId, sequential);
                torrent.setSequentialDownload(sequential);
                infoFrag.setSequentialDownload(sequential);
            }
        }
    }

    private void applyFilesChanges()
    {
        DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)adapter.getItem(FILE_FRAG_POS);

        if (fileFrag == null)
            return;

        Priority[] priorities = fileFrag.getPriorities();
        if (priorities != null) {
            fileFrag.disableSelectedFiles();
            changeFilesPriorityRequest(priorities);
        }
    }

    /*
     * Repaint TabLayout in ActionMode color if it created.
     */

    private void setTabLayoutColor(int colorId)
    {
        if (tabLayout == null)
            return;

        Utils.setBackground(tabLayout, ContextCompat.getDrawable(activity.getApplicationContext(), colorId));
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
        if (relativePath == null)
            return;

        String path = torrent.getDownloadPath() + File.separator + relativePath;
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        /* Give user a choice than to open file (without determining MIME type) */
        intent.setDataAndType(FileProvider.getUriForFile(activity,
                activity.getApplicationContext().getPackageName() + ".provider", new File(path)), "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
            if (!Utils.isTwoPane(activity.getApplicationContext()))
                pauseResume.setIcon(R.drawable.ic_pause_white_24dp);

        } else {
            pauseResume.setTitle(R.string.resume_torrent);
            if (!Utils.isTwoPane(activity.getApplicationContext()))
                pauseResume.setIcon(R.drawable.ic_play_arrow_white_24dp);
        }

        MenuItem saveTorrentFile = menu.findItem(R.id.save_torrent_file_menu);
        if (downloadingMetadata)
            saveTorrentFile.setVisible(false);
        else
            saveTorrentFile.setVisible(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        FragmentManager fm = getFragmentManager();
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.pause_resume_torrent_menu:
                pauseResumeTorrentRequest();
                break;
            case R.id.delete_torrent_menu:
                if (fm != null && fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
                    BaseAlertDialog deleteTorrentDialog = BaseAlertDialog.newInstance(
                            getString(R.string.deleting),
                            getString(R.string.delete_selected_torrent),
                            R.layout.dialog_delete_torrent,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            DetailTorrentFragment.this);

                    deleteTorrentDialog.show(fm, TAG_DELETE_TORRENT_DIALOG);
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
                if (fm != null && fm.findFragmentByTag(TAG_ADD_TRACKERS_DIALOG) == null) {
                    BaseAlertDialog addTrackersDialog = BaseAlertDialog.newInstance(
                            getString(R.string.add_trackers),
                            getString(R.string.dialog_add_trackers),
                            R.layout.dialog_multiline_text_input,
                            getString(R.string.add),
                            getString(R.string.replace),
                            getString(R.string.cancel),
                            DetailTorrentFragment.this);

                    addTrackersDialog.show(fm, TAG_ADD_TRACKERS_DIALOG);
                }
                break;
            case R.id.torrent_speed_limit:
                if (fm != null && fm.findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) == null) {
                    BaseAlertDialog speedLimitDialog = BaseAlertDialog.newInstance(
                            getString(R.string.speed_limit_title),
                            getString(R.string.speed_limit_dialog),
                            R.layout.dialog_speed_limit,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            DetailTorrentFragment.this);

                    speedLimitDialog.show(fm, TAG_SPEED_LIMIT_DIALOG);
                }
                break;
        }

        return true;
    }

    @Override
    public void onShow(final AlertDialog dialog)
    {
        if (dialog == null)
            return;

        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (fm.findFragmentByTag(TAG_ADD_TRACKERS_DIALOG) != null) {
            final TextInputEditText field =
                    dialog.findViewById(R.id.multiline_text_input_dialog);
            final TextInputLayout fieldLayout =
                    dialog.findViewById(R.id.layout_multiline_text_input_dialog);

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

            addButton.setOnClickListener((View v) -> {
                if (field != null && fieldLayout != null) {
                    String text = field.getText().toString();
                    List<String> urls = Arrays.asList(text.split(Utils.getLineSeparator()));
                    if (checkEditTextField(urls, fieldLayout, field)) {
                        addTrackersRequest(new ArrayList<>(urls), false);

                        dialog.dismiss();
                    }
                }
            });

            replaceButton.setOnClickListener((View v) -> {
                if (field != null && field.getText() != null && fieldLayout != null) {
                    String text = field.getText().toString();
                    List<String> urls = Arrays.asList(text.split(Utils.getLineSeparator()));

                    if (checkEditTextField(urls, fieldLayout, field)) {
                        addTrackersRequest(new ArrayList<>(urls), true);

                        dialog.dismiss();
                    }
                }
            });

            /* Inserting links from the clipboard */
            String clipboard = Utils.getClipboard(activity.getApplicationContext());
            if (clipboard != null && field != null) {
                List<String> urls = Arrays.asList(clipboard.split(Utils.getLineSeparator()));
                ArrayList<String> validUrls = new ArrayList<>();

                for (String url : urls)
                    if (Utils.isValidTrackerUrl(url))
                        validUrls.add(url);
                field.setText(TextUtils.join(Utils.getLineSeparator(), validUrls));
            }

        } else if (fm.findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) != null) {
            TextInputEditText upload = dialog.findViewById(R.id.upload_limit);
            TextInputEditText download = dialog.findViewById(R.id.download_limit);

            if (upload != null && download != null) {
                int minSpeedLimit = 0;
                int maxSpeedLimit = Integer.MAX_VALUE;
                InputFilter[] filter = new InputFilter[]{ new InputFilterMinMax(minSpeedLimit, maxSpeedLimit) };

                upload.setFilters(filter);
                if (TextUtils.isEmpty(upload.getText()))
                    upload.setText((uploadSpeedLimit != -1 ?
                            Integer.toString(uploadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));

                download.setFilters(filter);
                if (TextUtils.isEmpty(download.getText()))
                    download.setText((downloadSpeedLimit != -1 ?
                            Integer.toString(downloadSpeedLimit / 1024) : Integer.toString(minSpeedLimit)));
            }
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        FragmentManager fm = getFragmentManager();
        if (v != null && fm != null) {
            if (fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
                CheckBox withFiles = v.findViewById(R.id.dialog_delete_torrent_with_downloaded_files);
                deleteTorrentRequest(withFiles.isChecked());

                finish(new Intent(), FragmentCallback.ResultCode.CANCEL);

            } else if (fm.findFragmentByTag(TAG_SPEED_LIMIT_DIALOG) != null) {
                TextInputEditText upload = v.findViewById(R.id.upload_limit);
                TextInputEditText download = v.findViewById(R.id.download_limit);

                Editable uploadEditable = upload.getText();
                Editable downloadEditable = download.getText();
                if (uploadEditable != null && downloadEditable != null &&
                    !(TextUtils.isEmpty(uploadEditable.toString()) || TextUtils.isEmpty(uploadEditable.toString())))
                {
                    uploadSpeedLimit = Integer.parseInt(uploadEditable.toString()) * 1024;
                    downloadSpeedLimit = Integer.parseInt(downloadEditable.toString()) * 1024;
                    setSpeedLimitRequest(uploadSpeedLimit, downloadSpeedLimit);
                }

            } else if (fm.findFragmentByTag(TAG_SAVE_ERR_TORRENT_FILE_DIALOG) != null) {
                if (sentError != null) {
                    EditText editText = v.findViewById(R.id.comment);
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
        if (strings == null || layout == null)
            return false;

        if (strings.isEmpty()) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        boolean valid = true;
        int curLineStartIndex = 0;
        for (String s : strings) {
            if (!Utils.isValidTrackerUrl(s) && field.getText() != null) {
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

        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_SAVE_ERR_TORRENT_FILE_DIALOG) == null) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_save_torrent_file),
                    (e != null ? Log.getStackTraceString(e) : null),
                    this);

            errDialog.show(fm, TAG_SAVE_ERR_TORRENT_FILE_DIALOG);
        }
    }

    private void initRequest()
    {
        if (!bound || service == null)
            return;

        infoCache = service.getTorrentMetaInfo(torrentId);
        uploadSpeedLimit = service.getUploadSpeedLimit(torrentId);
        downloadSpeedLimit = service.getDownloadSpeedLimit(torrentId);
        basicStateCache = service.makeBasicStateParcel(torrentId);
    }

    private void changeFilesPriorityRequest(Priority[] priorities)
    {
        if (!bound || service == null || priorities == null || torrentId == null)
            return;

        service.changeFilesPriority(torrentId, priorities);
    }

    private void addTrackersRequest(ArrayList<String> trackers, boolean replace)
    {
        if (!bound || service == null || trackers == null || torrentId == null)
            return;

        if (replace)
            service.replaceTrackers(torrentId, trackers);
        else
            service.addTrackers(torrentId, trackers);
    }

    private void deleteTorrentRequest(boolean withFiles)
    {
        if (!bound || service == null)
            return;

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        service.deleteTorrents(list, withFiles);
    }

    private void forceRecheckRequest()
    {
        if (!bound || service == null)
            return;

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        service.forceRecheckTorrents(list);
    }

    private void forceAnnounceRequest()
    {
        if (!bound || service == null)
            return;

        ArrayList<String> list = new ArrayList<>();
        list.add(torrentId);

        service.forceAnnounceTorrents(list);
    }

    private void pauseResumeTorrentRequest()
    {
        if (!bound || service == null)
            return;

        service.pauseResumeTorrent(torrentId);
    }

    private void getMagnetRequest()
    {
        if (!bound || service == null)
            return;

        String magnet = service.getMagnet(torrentId);
        if (magnet != null) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "magnet");
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, magnet);
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
        }
    }

    private void setSpeedLimitRequest(int uploadSpeedLimit, int downloadSpeedLimit)
    {
        if (!bound || service == null)
            return;

        service.setUploadSpeedLimit(torrentId, uploadSpeedLimit);
        service.setDownloadSpeedLimit(torrentId, downloadSpeedLimit);
    }

    private void startUpdateTorrentState()
    {
        if (!bound)
            return;

        updateTorrentStateHandler.post(updateTorrentState);
    }

    private void stopUpdateTorrentState()
    {
        updateTorrentStateHandler.removeCallbacks(updateTorrentState);
    }

    private void getAdvancedStateRequest()
    {
        AdvanceStateParcel advanceStateParcel = service.makeAdvancedState(torrentId);
        if (advanceStateParcel == null)
            return;

        advanceStateCache = advanceStateParcel;
        switch (viewPager.getCurrentItem()) {
            case STATE_FRAG_POS:
                DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)adapter.getItem(STATE_FRAG_POS);
                if (stateFrag != null)
                    stateFrag.setAdvanceState(advanceStateCache);
                break;
            case FILE_FRAG_POS:
                DetailTorrentFilesFragment fileFrag = (DetailTorrentFilesFragment)adapter.getItem(FILE_FRAG_POS);
                if (fileFrag != null)
                    fileFrag.updateFiles(advanceStateCache.filesReceivedBytes,
                                         advanceStateCache.filesAvailability);
                break;
            case PIECES_FRAG_POS:
                DetailTorrentPiecesFragment piecesFrag = (DetailTorrentPiecesFragment)adapter.getItem(PIECES_FRAG_POS);
                if (piecesFrag != null)
                    piecesFrag.setDownloadedPiecesCount(advanceStateCache.downloadedPieces);
                break;
        }
    }

    private void getTrackersStatesRequest()
    {
        ArrayList<TrackerStateParcel> states = service.getTrackerStatesList(torrentId);
        if (states == null)
            return;

        if (!trackersCache.containsAll(states)) {
            trackersCache.clear();
            trackersCache.putAll(states);
            if (viewPager.getCurrentItem() == TRACKERS_FRAG_POS) {
                DetailTorrentTrackersFragment trackersFrag =
                        (DetailTorrentTrackersFragment)adapter.getItem(TRACKERS_FRAG_POS);
                if (trackersFrag != null)
                    trackersFrag.setTrackersList(states);
            }
        }
    }

    private void getPeerStatesRequest()
    {
        ArrayList<PeerStateParcel> states = service.getPeerStatesList(torrentId);
        if (states == null)
            return;

        if (!peersCache.containsAll(states) || states.isEmpty()) {
            peersCache.clear();
            peersCache.putAll(states);
            if (viewPager.getCurrentItem() == PEERS_FRAG_POS) {
                DetailTorrentPeersFragment peersFrag =
                        (DetailTorrentPeersFragment)adapter.getItem(PEERS_FRAG_POS);
                if (peersFrag != null)
                    peersFrag.setPeerList(states);
            }
        }
    }

    private void getPiecesRequest()
    {
        boolean[] pieces = service.getPieces(torrentId);
        if (pieces == null)
            return;

        if (!Arrays.equals(piecesCache, pieces)) {
            piecesCache = pieces;
            if (viewPager.getCurrentItem() == PIECES_FRAG_POS) {
                DetailTorrentPiecesFragment piecesFrag =
                        (DetailTorrentPiecesFragment)adapter.getItem(PIECES_FRAG_POS);
                if (piecesFrag != null)
                    piecesFrag.setPieces(pieces);
            }
        }
    }

    TorrentTaskServiceReceiver.Callback serviceReceiver = new TorrentTaskServiceReceiver.Callback()
    {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onReceive(Bundle b)
        {
            if (b != null) {
                switch ((TorrentStateMsg.Type)b.getSerializable(TorrentStateMsg.TYPE)) {
                    case UPDATE_TORRENT: {
                        BasicStateParcel state = b.getParcelable(TorrentStateMsg.STATE);
                        if (state != null && state.torrentId.equals(torrentId))
                            handleTorrentState(state);
                        break;
                    }
                    case UPDATE_TORRENTS: {
                        Bundle states = b.getParcelable(TorrentStateMsg.STATES);
                        if (states != null && states.containsKey(torrentId))
                            handleTorrentState(states.getParcelable(torrentId));
                        break;
                    }
                }
            }
        }
    };

    private void handleTorrentState(final BasicStateParcel state)
    {
        if (state == null)
            return;

        basicStateCache = state;
        if (downloadingMetadata && basicStateCache.stateCode != TorrentStateCode.DOWNLOADING_METADATA) {
            downloadingMetadata = false;

            if (torrentId != null)
                torrent = repo.getTorrentByID(torrentId);

            TorrentMetaInfo info = service.getTorrentMetaInfo(torrentId);
            if (info != null && (infoCache == null || !infoCache.equals(info))) {
                infoCache = info;
                DetailTorrentInfoFragment infoFrag =
                        (DetailTorrentInfoFragment)adapter.getItem(INFO_FRAG_POS);
                if (infoFrag != null)
                    infoFrag.setInfo(infoCache);

                DetailTorrentStateFragment stateFrag =
                        (DetailTorrentStateFragment)adapter.getItem(STATE_FRAG_POS);
                if (stateFrag != null)
                    stateFrag.setInfo(infoCache);

                DetailTorrentFilesFragment fileFrag =
                        (DetailTorrentFilesFragment)adapter.getItem(FILE_FRAG_POS);
                if (fileFrag != null)
                    fileFrag.setFilesAndPriorities(getFileList(), getPrioritiesList());

                DetailTorrentPiecesFragment piecesFrag =
                        (DetailTorrentPiecesFragment)adapter.getItem(PIECES_FRAG_POS);
                if (piecesFrag != null)
                    piecesFrag.setPiecesCountAndSize(infoCache.numPieces, infoCache.pieceLength);
            }
            activity.invalidateOptionsMenu();
        }

        if (torrent != null) {
            if (state.stateCode == TorrentStateCode.PAUSED || torrent.isPaused()) {
                torrent.setPaused(state.stateCode == TorrentStateCode.PAUSED);
                /* Redraw pause/resume menu */
                if (Utils.isTwoPane(activity.getApplicationContext()))
                    prepareOptionsMenu(toolbar.getMenu());
                else
                    activity.invalidateOptionsMenu();
            }
        }

        if (viewPager.getCurrentItem() == STATE_FRAG_POS) {
            DetailTorrentStateFragment stateFrag = (DetailTorrentStateFragment)adapter.getItem(STATE_FRAG_POS);
            if (stateFrag != null)
                stateFrag.setBasicState(state);
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
                                                         torrentId, path, torrent.getName() + ".torrent"))
                            showSnackbar(getString(R.string.save_torrent_file_successfully),
                                         Snackbar.LENGTH_SHORT);
                        else
                            saveErrorTorrentFileDialog(null);

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
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
