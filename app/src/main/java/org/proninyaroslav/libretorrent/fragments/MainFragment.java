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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.proninyaroslav.libretorrent.*;
import org.proninyaroslav.libretorrent.adapters.ToolbarSpinnerAdapter;
import org.proninyaroslav.libretorrent.adapters.TorrentListAdapter;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentServiceCallback;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.EmptyRecyclerView;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsActivity;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/*
 * The list of torrents.
 */

public class MainFragment extends Fragment
        implements
        BaseAlertDialog.OnClickListener,
        BaseAlertDialog.OnDialogShowListener
{
    @SuppressWarnings("unused")
    private static final String TAG = MainFragment.class.getSimpleName();

    private static final String TAG_PREV_IMPL_INTENT = "prev_impl_intent";
    private static final String TAG_SELECTABLE_ADAPTER = "selectable_adapter";
    private static final String TAG_SELECTED_TORRENTS = "selected_torrents";
    private static final String TAG_IN_ACTION_MODE = "in_action_mode";
    private static final String TAG_DELETE_TORRENT_DIALOG = "delete_torrent_dialog";
    private static final String TAG_ADD_LINK_DIALOG = "add_link_dialog";
    private static final String TAG_ERROR_OPEN_TORRENT_FILE_DIALOG = "error_open_torrent_file_dialog";
    private static final String TAG_SAVE_ERROR_DIALOG = "save_error_dialog";
    private static final String TAG_TORRENTS_LIST_STATE = "torrents_list_state";
    private static final String TAG_ABOUT_DIALOG = "about_dialog";
    private static final String TAG_TORRENT_SORTING = "torrent_sorting";

    private static final int ADD_TORRENT_REQUEST = 1;
    private static final int TORRENT_FILE_CHOOSE_REQUEST = 2;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private FloatingActionMenu addTorrentButton;
    private FloatingActionButton openFileButton;
    private FloatingActionButton addLinkButton;
    private SearchView searchView;
    private CoordinatorLayout coordinatorLayout;
    private LinearLayoutManager layoutManager;
    private EmptyRecyclerView torrentsList;
    /* Save state scrolling */
    private Parcelable torrentsListState;
    private Map<String, TorrentStateParcel> torrentStates = new HashMap<>();
    private TorrentListAdapter adapter;
    private ActionMode actionMode;
    private ActionModeCallback actionModeCallback = new ActionModeCallback();
    private boolean inActionMode = false;
    private ArrayList<String> selectedTorrents = new ArrayList<>();
    private ToolbarSpinnerAdapter spinnerAdapter;
    private Spinner spinner;
    private boolean addTorrentMenu = false;

    /* Prevents re-adding the torrent, obtained through implicit intent */
    private Intent prevImplIntent;
    private TorrentTaskService service;
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    private ReentrantLock callbackSync;
    private ReentrantLock adapterSync;
    /*
     * Torrents are added to the queue, if the client is not bounded to service.
     * Trying to add torrents will be made at the first connect.
     */
    private HashSet<Torrent> addTorrentsQueue = new HashSet<>();

    private Throwable sentError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        coordinatorLayout = (CoordinatorLayout) v.findViewById(R.id.main_coordinator_layout);

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
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        showBlankFragment();

        callbackSync = new ReentrantLock();
        adapterSync = new ReentrantLock();

        toolbar = (Toolbar) activity.findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
        }

        View spinnerContainer = LayoutInflater.from(activity).inflate(R.layout.toolbar_spinner,
                toolbar, false);
        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        toolbar.addView(spinnerContainer, lp);

        spinnerAdapter = new ToolbarSpinnerAdapter(activity);
        spinnerAdapter.addItems(getSpinnerList());

        spinner = (Spinner) spinnerContainer.findViewById(R.id.toolbar_spinner);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                setTorrentListFilter(spinnerAdapter.getItem(i));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });

        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);

        addTorrentButton = (FloatingActionMenu) activity.findViewById(R.id.add_torrent_button);
        addTorrentButton.setClosedOnTouchOutside(true);

        openFileButton = (FloatingActionButton) activity.findViewById(R.id.open_file_button);
        openFileButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                addTorrentButton.close(true);
                torrentFileChooserDialog();
            }
        });

        addLinkButton = (FloatingActionButton) activity.findViewById(R.id.add_link_button);
        addLinkButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                addTorrentButton.close(true);
                addLinkDialog();
            }
        });

        if (savedInstanceState != null) {
            prevImplIntent = savedInstanceState.getParcelable(TAG_PREV_IMPL_INTENT);
        }

        activity.bindService(new Intent(activity.getApplicationContext(), TorrentTaskService.class),
                connection, Context.BIND_AUTO_CREATE);

        torrentsList = (EmptyRecyclerView) activity.findViewById(R.id.torrent_list);
        layoutManager = new LinearLayoutManager(activity);
        torrentsList.setLayoutManager(layoutManager);

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

        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });

        torrentsList.setItemAnimator(animator);
        torrentsList.addItemDecoration(
                new RecyclerViewDividerDecoration(a.getDrawable(0)));
        torrentsList.setEmptyView(activity.findViewById(R.id.empty_view_torrent_list));

        a.recycle();

        adapter = new TorrentListAdapter(
                new ArrayList<TorrentStateParcel>(),
                activity, R.layout.item_torrent_list, torrentListListener,
                new TorrentSortingComparator(Utils.getTorrentSorting(activity.getApplicationContext())));

        setTorrentListFilter((String) spinner.getSelectedItem());

        torrentsList.setAdapter(adapter);

        Intent i = activity.getIntent();
        /* If add torrent dialog has been called by an implicit intent */
        if (i != null && i.hasExtra(AddTorrentActivity.TAG_RESULT_TORRENT)) {
            if (prevImplIntent == null || !prevImplIntent.equals(i)) {
                prevImplIntent = i;
                Torrent torrent = i.getParcelableExtra(AddTorrentActivity.TAG_RESULT_TORRENT);
                if (torrent != null) {
                    if (!bound || service == null) {
                        addTorrentsQueue.add(torrent);
                    } else {
                        try {
                            service.addTorrent(torrent, false);
                        } catch (Throwable e) {
                            Log.e(TAG, Log.getStackTraceString(e));
                            addTorrentError(e);
                        }
                    }
                }
            }

        } else if (i != null && i.getAction() != null) {
            switch (i.getAction()) {
                case MainActivity.ACTION_ADD_SHORTCUT:
                case NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT:
                    addTorrentMenu = true;
                    /* Prevents re-reading action after device configuration changes */
                    i.setAction(null);
                    break;
            }
        }

        /* Show add torrent menu (called from service) after window is displayed */
        activity.getWindow().findViewById(android.R.id.content).post(new Runnable()
        {
            @Override
            public void run()
            {
                if (addTorrentMenu) {
                    /* Hide notification bar */
                    activity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    addTorrentMenu = false;

                    View v = activity.getWindow().findViewById(android.R.id.content);
                    registerForContextMenu(v);
                    activity.openContextMenu(v);
                    unregisterForContextMenu(v);
                }
            }
        });

        if (savedInstanceState != null) {
            selectedTorrents = savedInstanceState.getStringArrayList(TAG_SELECTED_TORRENTS);
            if (savedInstanceState.getBoolean(TAG_IN_ACTION_MODE, false)) {
                actionMode = activity.startActionMode(actionModeCallback);
                adapter.setSelectedItems(savedInstanceState.getIntegerArrayList(TAG_SELECTABLE_ADAPTER));
                actionMode.setTitle(String.valueOf(adapter.getSelectedItemCount()));
            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (bound) {
            if (service != null) {
                service.removeListener(serviceCallback);
            }

            getActivity().unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (torrentsListState != null) {
            layoutManager.onRestoreInstanceState(torrentsListState);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putParcelable(TAG_PREV_IMPL_INTENT, prevImplIntent);
        outState.putIntegerArrayList(TAG_SELECTABLE_ADAPTER, adapter.getSelectedItems());
        outState.putBoolean(TAG_IN_ACTION_MODE, inActionMode);
        outState.putStringArrayList(TAG_SELECTED_TORRENTS, selectedTorrents);
        torrentsListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_TORRENTS_LIST_STATE, torrentsListState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            torrentsListState = savedInstanceState.getParcelable(TAG_TORRENTS_LIST_STATE);
        }
    }

    private ServiceConnection connection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service) {
            TorrentTaskService.LocalBinder binder = (TorrentTaskService.LocalBinder) service;
            MainFragment.this.service = binder.getService();
            MainFragment.this.service.addListener(serviceCallback);
            bound = true;

            if (!addTorrentsQueue.isEmpty()) {
                for (Torrent torrent : addTorrentsQueue) {
                    try {
                        MainFragment.this.service.addTorrent(torrent, false);
                    } catch (Throwable e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                        addTorrentError(e);
                    }
                }
                addTorrentsQueue.clear();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            MainFragment.this.service.removeListener(serviceCallback);
            bound = false;
        }
    };

    TorrentServiceCallback serviceCallback = new TorrentServiceCallback()
    {
        @Override
        public void onTorrentStateChanged(TorrentStateParcel state)
        {
            callbackSync.lock();

            try {
                if (state != null) {
                    torrentStates.put(state.torrentId, state);
                    reloadAdapterItem(state);
                }

            } finally {
                callbackSync.unlock();
            }
        }

        @Override
        public void onTorrentsStateChanged(Bundle states)
        {
            if (states == null) {
                return;
            }

            callbackSync.lock();

            try {
                torrentStates.clear();

                for (String key : states.keySet()) {
                    TorrentStateParcel state = states.getParcelable(key);
                    if (state != null) {
                        torrentStates.put(state.torrentId, state);
                    }
                }

                reloadAdapter();

            } finally {
                callbackSync.unlock();
            }
        }

        @Override
        public void onTorrentAdded(TorrentStateParcel state)
        {
            callbackSync.lock();
            try {
                if (state != null) {
                    torrentStates.put(state.torrentId, state);
                    reloadAdapter();
                }
            } finally {
                callbackSync.unlock();
            }
        }

        @Override
        public void onTorrentRemoved(TorrentStateParcel state)
        {
            callbackSync.lock();

            try {
                torrentStates.remove(state.torrentId);
                deleteAdapterItem(state);

            } finally {
                callbackSync.unlock();
            }
        }
    };

    private class ActionModeCallback implements ActionMode.Callback
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            inActionMode = true;
            mode.getMenuInflater().inflate(R.menu.main_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            ArrayList<Integer> indexes = adapter.getSelectedItems();

            switch (item.getItemId()) {
                case R.id.delete_torrent_menu:
                    mode.finish();

                    if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
                        BaseAlertDialog deleteTorrentDialog = BaseAlertDialog.newInstance(
                                getString(R.string.deleting),
                                (indexes.size() > 1 ? getString(R.string.delete_selected_torrents) : getString(R.string.delete_selected_torrent)),
                                R.layout.dialog_delete_torrent,
                                getString(R.string.ok),
                                getString(R.string.cancel),
                                null,
                                MainFragment.this);

                        deleteTorrentDialog.show(getFragmentManager(), TAG_DELETE_TORRENT_DIALOG);
                    }

                    break;
                case R.id.select_all_torrent_menu:
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        if (adapter.isSelected(i)) {
                            continue;
                        }

                        onItemSelected(adapter.getItem(i).torrentId, i);
                    }

                    break;
                case R.id.force_recheck_torrent_menu:
                    mode.finish();

                    if (bound && service != null) {
                        service.forceRecheckTorrents(selectedTorrents);
                    }
                    selectedTorrents.clear();
                    break;
                case R.id.force_announce_torrent_menu:
                    mode.finish();

                    if (bound && service != null) {
                        service.forceAnnounceTorrents(selectedTorrents);
                    }
                    selectedTorrents.clear();
                    break;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            adapter.clearSelection();
            actionMode = null;
            inActionMode = false;
            Utils.showActionModeStatusBar(activity, false);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);

        activity.getMenuInflater().inflate(R.menu.main_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.add_link_menu:
                addLinkDialog();
                break;
            case R.id.open_file_menu:
                torrentFileChooserDialog();
                break;
        }

        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        if (!isAdded()) {
            return;
        }

        inflater.inflate(R.menu.main, menu);

        searchView = (SearchView)menu.findItem(R.id.search).getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                adapter.search(query);
                /* Submit the search will hide the keyboard */
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                adapter.search(newText);

                return true;
            }
        });
        searchView.setQueryHint(getString(R.string.search));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.settings_menu:
                startActivity(new Intent(activity, SettingsActivity.class));
                break;
            case R.id.sort_torrent_menu:
                torrentSortingDialog();
                break;
            case R.id.about_menu:
                aboutDialog();
                break;
            case R.id.shutdown_app_menu:
                activity.stopService(new Intent(activity.getApplicationContext(), TorrentTaskService.class));
                /* FIXME: Fix leaking popup menu after close app */
                activity.closeOptionsMenu();
                finish(new Intent(), FragmentCallback.ResultCode.OK);
                break;
        }
        return true;
    }

    private void aboutDialog()
    {
        if (getFragmentManager().findFragmentByTag(TAG_ABOUT_DIALOG) == null) {
            BaseAlertDialog aboutDialog = BaseAlertDialog.newInstance(
                    getString(R.string.about_title),
                    null,
                    R.layout.dialog_about,
                    getString(R.string.ok),
                    getString(R.string.about_changelog),
                    null,
                    this);
            aboutDialog.show(getFragmentManager(), TAG_ABOUT_DIALOG);
        }
    }

    private void torrentSortingDialog()
    {
        if (getFragmentManager().findFragmentByTag(TAG_TORRENT_SORTING) == null) {
            BaseAlertDialog sortingDialog = BaseAlertDialog.newInstance(
                    getString(R.string.sorting),
                    null,
                    R.layout.dialog_sorting,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);
            sortingDialog.show(getFragmentManager(), TAG_TORRENT_SORTING);
        }
    }

    private void addLinkDialog()
    {
        if (getFragmentManager().findFragmentByTag(TAG_ADD_LINK_DIALOG) == null) {
            BaseAlertDialog addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_link_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);

            addLinkDialog.show(getFragmentManager(), TAG_ADD_LINK_DIALOG);
        }
    }

    private boolean checkEditTextField(String s, TextInputLayout layout)
    {
        if (s == null || layout == null) {
            return false;
        }

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        if (s.startsWith(Utils.MAGNET_PREFIX)) {
            layout.setErrorEnabled(false);
            layout.setError(null);

            return true;
        }

        if (s.startsWith(Utils.HTTP_PREFIX) || s.startsWith(Utils.HTTPS_PREFIX)) {
            if (!Utils.isValidUrl(s)) {
                layout.setErrorEnabled(true);
                layout.setError(getString(R.string.error_invalid_link));
                layout.requestFocus();

                return false;
            }
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    /*
     * Returns a list of torrents sorting categories for spinner.
     */

    private List<String> getSpinnerList()
    {
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.spinner_all_torrents));
        categories.add(getString(R.string.spinner_downloading_torrents));
        categories.add(getString(R.string.spinner_downloaded_torrents));
        categories.add(getString(R.string.spinner_downloading_metadata_torrents));

        return categories;
    }

    private void setTorrentListFilter(String filter)
    {
        if (filter == null) {
            return;
        }

        if (filter.equals(getString(R.string.spinner_downloading_torrents))) {
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.DOWNLOADING));

        } else if (filter.equals(getString(R.string.spinner_downloaded_torrents))) {
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.SEEDING));

        } else if (filter.equals(getString(R.string.spinner_downloading_metadata_torrents))) {
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.DOWNLOADING_METADATA));

        } else {
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter());
        }
    }

    TorrentListAdapter.ViewHolder.ClickListener torrentListListener = new TorrentListAdapter.ViewHolder.ClickListener()
    {
        @Override
        public void onItemClicked(int position, TorrentStateParcel torrentState)
        {
            if (actionMode == null) {
            /* Mark this torrent as open in the list */
                adapter.markAsOpen(torrentState);

                showDetailTorrent(torrentState.torrentId);
            } else {
                onItemSelected(torrentState.torrentId, position);
            }
        }

        @Override
        public boolean onItemLongClicked(int position, TorrentStateParcel torrentState)
        {
            if (actionMode == null) {
                actionMode = activity.startActionMode(actionModeCallback);
            }

            onItemSelected(torrentState.torrentId, position);

            return true;
        }

        @Override
        public void onPauseButtonClicked(int position, TorrentStateParcel torrentState)
        {
            if (!bound || service == null) {
                return;
            }

            service.pauseResumeTorrent(torrentState.torrentId);
        }
    };

    private void onItemSelected(String id, int position)
    {
        toggleSelection(position);

        if (selectedTorrents.contains(id)) {
            selectedTorrents.remove(id);
        } else {
            selectedTorrents.add(id);
        }
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        int count = adapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    /*
     * Uncheck current torrent from the list and set blank fragment.
     */

    public void resetCurOpenTorrent()
    {
        adapter.markAsOpen(null);
        showBlankFragment();
    }

    @Override
    public void onShow(final AlertDialog dialog)
    {
        if (dialog != null) {
            if (getFragmentManager().findFragmentByTag(TAG_ADD_LINK_DIALOG) != null) {
                initAddDialog(dialog);

            } else if (getFragmentManager().findFragmentByTag(TAG_ABOUT_DIALOG) != null) {
                initAboutDialog(dialog);

            } else if (getFragmentManager().findFragmentByTag(TAG_TORRENT_SORTING) != null) {
                initTorrentSortingDialog(dialog);
            }
        }
    }

    private void initAddDialog(final AlertDialog dialog)
    {
        final TextInputEditText field =
                (TextInputEditText) dialog.findViewById(R.id.text_input_dialog);
        final TextInputLayout fieldLayout =
                (TextInputLayout) dialog.findViewById(R.id.layout_text_input_dialog);

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
         * pressing positive button if the text checker gave a false result
         */
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        positiveButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (field != null && fieldLayout != null) {
                    String link = field.getText().toString();

                    if (checkEditTextField(link, fieldLayout)) {
                        String url;

                        if (link.startsWith(Utils.MAGNET_PREFIX)) {
                            url = link;
                        } else if (link.startsWith(Utils.HTTP_PREFIX)
                                || link.startsWith(Utils.HTTPS_PREFIX)) {
                            url = Utils.normalizeURL(link);
                        } else {
                            url = Utils.INFOHASH_PREFIX + link;
                        }

                        if (url != null) {
                            addTorrentDialog(Uri.parse(url));
                        }

                        dialog.dismiss();
                    }
                }
            }
        });

        /* Inserting a link from the clipboard */
        String clipboard = Utils.getClipboard(activity.getApplicationContext());
        String url;

        if (clipboard != null) {
            if (!clipboard.startsWith(Utils.MAGNET_PREFIX)) {
                url = Utils.normalizeURL(clipboard);
            } else {
                url = clipboard;
            }

            if (field != null && url != null) {
                field.setText(url);
            }
        }
    }

    private void initAboutDialog(final AlertDialog dialog)
    {
        TextView version = (TextView) dialog.findViewById(R.id.about_version);

        if (version != null) {
            try {
                PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                version.setText(info.versionName);

            } catch (PackageManager.NameNotFoundException e) {
                /* Ignore */
            }
        }
    }

    private void initTorrentSortingDialog(final AlertDialog dialog)
    {
        Spinner sp = dialog.findViewById(R.id.dialog_sort_by);
        RadioGroup group = dialog.findViewById(R.id.dialog_sort_direction);

        if (sp != null && group != null) {
            SettingsManager pref = new SettingsManager(activity);

            String[] columns = activity.getResources().getStringArray(R.array.sort_torrent_values);

            String column = pref.getString(getString(R.string.pref_key_sort_torrent_by),
                                           SettingsManager.Default.sortTorrentBy);
            String direction = pref.getString(getString(R.string.pref_key_sort_torrent_direction),
                                              SettingsManager.Default.sortTorrentDirection);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                    R.layout.spinner_item_dropdown,
                    getResources().getStringArray(R.array.sort_torrent_by));
            sp.setAdapter(adapter);
            sp.setSelection(Arrays.asList(columns).indexOf(column));

            if (TorrentSorting.Direction.fromValue(direction) == TorrentSorting.Direction.ASC)
                group.check(R.id.dialog_sort_by_ascending);
            else
                group.check(R.id.dialog_sort_by_descending);
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (v != null) {
            if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
                CheckBox withFiles = (CheckBox) v.findViewById(R.id.dialog_delete_torrent_with_downloaded_files);

                DetailTorrentFragment f = getCurrentDetailFragment();
                if (f != null) {
                    String id = f.getTorrentId();
                    if (selectedTorrents.contains(id)) {
                        resetCurOpenTorrent();
                    }
                }

                if (bound && service != null) {
                    service.deleteTorrents(selectedTorrents, withFiles.isChecked());
                }
                selectedTorrents.clear();

            } else if (getFragmentManager().findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) != null ||
                    getFragmentManager().findFragmentByTag(TAG_SAVE_ERROR_DIALOG) != null) {
                if (sentError != null) {
                    EditText editText = (EditText) v.findViewById(R.id.comment);
                    String comment = editText.getText().toString();

                    Utils.reportError(sentError, comment);
                }

            } else if (getFragmentManager().findFragmentByTag(TAG_TORRENT_SORTING) != null) {
                Spinner sp = (Spinner) v.findViewById(R.id.dialog_sort_by);
                RadioGroup group = (RadioGroup) v.findViewById(R.id.dialog_sort_direction);
                SettingsManager pref = new SettingsManager(activity);

                String[] columns = activity.getResources().getStringArray(R.array.sort_torrent_values);
                int position = sp.getSelectedItemPosition();

                if (position != -1 && position < columns.length) {
                    String column = columns[position];
                    pref.put(activity.getString(R.string.pref_key_sort_torrent_by), column);
                }

                int radioButtonId = group.getCheckedRadioButtonId();
                String direction = TorrentSorting.Direction.ASC.name();

                if (radioButtonId == R.id.dialog_sort_by_descending) {
                    direction = TorrentSorting.Direction.DESC.name();
                }
                pref.put(activity.getString(R.string.pref_key_sort_torrent_direction), direction);

                if (adapter != null) {
                    adapter.setSorting(new TorrentSortingComparator(
                            Utils.getTorrentSorting(activity.getApplicationContext())));
                }
            }
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
            selectedTorrents.clear();

        } else if (getFragmentManager().findFragmentByTag(TAG_ABOUT_DIALOG) != null) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(getString(R.string.about_changelog_link)));
            startActivity(i);
        }
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }

    private void deleteAdapterItem(final TorrentStateParcel state)
    {
        adapterSync.lock();

        try {
            activity.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    adapter.deleteItem(state);
                }
            });

        } finally {
            adapterSync.unlock();
        }
    }

    private void reloadAdapterItem(final TorrentStateParcel state)
    {
        adapterSync.lock();

        try {
            activity.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    adapter.updateItem(state);
                }
            });

        } finally {
            adapterSync.unlock();
        }
    }

    final synchronized void reloadAdapter()
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                adapter.clearAll();

                if (torrentStates == null || torrentStates.size() == 0) {
                    adapter.notifyDataSetChanged();
                } else {
                    adapter.addItems(torrentStates.values());
                }
            }
        });
    }

    private void torrentFileChooserDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        List<String> fileType = new ArrayList<>();
        fileType.add("torrent");
        FileManagerConfig config = new FileManagerConfig(null,
                getString(R.string.torrent_file_chooser_title),
                fileType,
                FileManagerConfig.FILE_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, TORRENT_FILE_CHOOSE_REQUEST);
    }

    private void addTorrentDialog(Uri uri)
    {
        if (uri == null) {
            return;
        }

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, uri);
        startActivityForResult(i, ADD_TORRENT_REQUEST);
    }

    private void showDetailTorrent(String id)
    {
        if (Utils.isTwoPane(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();

            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);

            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);
            if (fragment != null && fragment instanceof DetailTorrentFragment) {
                String oldId = ((DetailTorrentFragment) fragment).getTorrentId();

                if (oldId != null && id.equals(oldId)) {
                    return;
                }
            }

            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, detail)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

        } else {
            Intent i = new Intent(activity, DetailTorrentActivity.class);
            i.putExtra(DetailTorrentActivity.TAG_TORRENT_ID, id);
            startActivity(i);
        }
    }

    private void showBlankFragment()
    {
        if (Utils.isTablet(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();

            BlankFragment blank = BlankFragment.newInstance();

            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    public DetailTorrentFragment getCurrentDetailFragment()
    {
        if (!Utils.isTwoPane(activity.getApplicationContext())) {
            return null;
        }

        Fragment fragment = getFragmentManager()
                .findFragmentById(R.id.detail_torrent_fragmentContainer);

        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment) fragment : null);
    }

    private void addTorrentError(Throwable e)
    {
        if (e == null || !isAdded())
            return;

        sentError = e;
        if (e instanceof FileNotFoundException) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_file_not_found_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();
        } else if (e instanceof IOException) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_io_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();
        } else if (e instanceof FileAlreadyExistsException) {
            Snackbar.make(coordinatorLayout,
                    R.string.torrent_exist,
                    Snackbar.LENGTH_LONG)
                    .show();
        } else {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
            case TORRENT_FILE_CHOOSE_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                        String path = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);

                        try {
                            addTorrentDialog(Uri.fromFile(new File(path)));

                        } catch (Exception e) {
                            sentError = e;

                            Log.e(TAG, Log.getStackTraceString(e));

                            if (getFragmentManager()
                                    .findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) == null) {
                                ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                                        activity.getApplicationContext(),
                                        getString(R.string.error),
                                        getString(R.string.error_open_torrent_file),
                                        Log.getStackTraceString(e),
                                        this);

                                FragmentTransaction ft = getFragmentManager().beginTransaction();
                                ft.add(errDialog, TAG_ERROR_OPEN_TORRENT_FILE_DIALOG);
                                ft.commitAllowingStateLoss();
                            }
                        }
                    }
                }
                break;
            case ADD_TORRENT_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    if (data.hasExtra(AddTorrentActivity.TAG_RESULT_TORRENT)) {
                        Torrent torrent = data.getParcelableExtra(AddTorrentActivity.TAG_RESULT_TORRENT);
                        if (torrent != null) {
                            if (!bound || service == null) {
                                addTorrentsQueue.add(torrent);
                            } else {
                                try {
                                    service.addTorrent(torrent, false);
                                } catch (Throwable e) {
                                    Log.e(TAG, Log.getStackTraceString(e));
                                    addTorrentError(e);
                                }
                            }
                        }
                    }
                }
                break;
        }
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback) activity).fragmentFinished(intent, code);
    }
}
