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
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.proninyaroslav.libretorrent.*;
import org.proninyaroslav.libretorrent.adapters.ToolbarSpinnerAdapter;
import org.proninyaroslav.libretorrent.adapters.TorrentListAdapter;
import org.proninyaroslav.libretorrent.adapters.TorrentListItem;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentHelper;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.receivers.TorrentTaskServiceReceiver;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.TorrentStateMsg;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
    private static final int CREATE_TORRENT_REQUEST = 3;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private FloatingActionMenu addTorrentButton;
    private FloatingActionButton openFileButton;
    private FloatingActionButton addLinkButton;
    private FloatingActionButton createTorrentButton;
    private SearchView searchView;
    private CoordinatorLayout coordinatorLayout;
    private LinearLayoutManager layoutManager;
    private EmptyRecyclerView torrentsList;
    /* Save state scrolling */
    private Parcelable torrentsListState;
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
    private SharedPreferences pref;
    private Throwable sentError;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        coordinatorLayout = v.findViewById(R.id.main_coordinator_layout);

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
            activity = (AppCompatActivity) getActivity();

        /* Clean garbage fragments after rotate for tablets */
        /* TODO: if minSdkVersion will be >= 17, go to getChildFragmentManager() instead of manually managing the fragments */
        if (Utils.isLargeScreenDevice(activity)) {
            FragmentManager fm = activity.getSupportFragmentManager();
            if (fm != null) {
                List<Fragment> fragments = fm.getFragments();
                FragmentTransaction ft = fm.beginTransaction();
                for (Fragment f : fragments)
                    if (f != null && !(f instanceof MainFragment))
                        ft.remove(f);
                ft.commitAllowingStateLoss();
            }
        }

        pref = SettingsManager.getPreferences(activity);
        showBlankFragment();
        toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null)
            toolbar.setTitle(R.string.app_name);

        View spinnerContainer = LayoutInflater.from(activity).inflate(R.layout.toolbar_spinner,
                toolbar, false);
        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        toolbar.addView(spinnerContainer, lp);

        spinnerAdapter = new ToolbarSpinnerAdapter(activity);
        spinnerAdapter.addItems(getSpinnerList());

        spinner = spinnerContainer.findViewById(R.id.toolbar_spinner);
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

        addTorrentButton = activity.findViewById(R.id.add_torrent_button);
        addTorrentButton.setClosedOnTouchOutside(true);

        openFileButton = activity.findViewById(R.id.open_file_button);
        openFileButton.setOnClickListener((View view) -> {
            addTorrentButton.close(true);
            torrentFileChooserDialog();
        });

        addLinkButton = activity.findViewById(R.id.add_link_button);
        addLinkButton.setOnClickListener((View view) -> {
            addTorrentButton.close(true);
            addLinkDialog();
        });

        createTorrentButton = activity.findViewById(R.id.create_torrent_button);
        createTorrentButton.setOnClickListener((View view) -> {
            addTorrentButton.close(true);
            createTorrentDialog();
        });

        if (savedInstanceState != null)
            prevImplIntent = savedInstanceState.getParcelable(TAG_PREV_IMPL_INTENT);

        torrentsList = activity.findViewById(R.id.torrent_list);
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
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder)
            {
                return true;
            }
        };

        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });

        torrentsList.setItemAnimator(animator);
        torrentsList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        torrentsList.setEmptyView(activity.findViewById(R.id.empty_view_torrent_list));

        a.recycle();

        adapter = new TorrentListAdapter(new HashMap<>(), activity, R.layout.item_torrent_list, torrentListListener,
                new TorrentSortingComparator(Utils.getTorrentSorting(activity.getApplicationContext())));
        setTorrentListFilter((String)spinner.getSelectedItem());
        torrentsList.setAdapter(adapter);

        Intent i = activity.getIntent();
        if (i != null && i.getAction() != null) {
            /* If add torrent dialog has been called by an implicit intent */
            if (i.getAction().equals(AddTorrentActivity.ACTION_ADD_TORRENT)) {
                if (prevImplIntent == null || !prevImplIntent.equals(i)) {
                    prevImplIntent = i;
                    AddTorrentParams params = AddTorrentActivity.getResult();
                    if (params != null)
                        addTorrent(params);
                }

            } else {
                switch (i.getAction()) {
                    case MainActivity.ACTION_ADD_TORRENT_SHORTCUT:
                    case NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT:
                        addTorrentMenu = true;
                        /* Prevents re-reading action after device configuration changes */
                        i.setAction(null);
                        break;
                }
            }
        }

        /* Show add torrent menu (called from service) after window is displayed */
        activity.getWindow().findViewById(android.R.id.content).post(() -> {
            if (addTorrentMenu) {
                /* Hide notification bar */
                activity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                addTorrentMenu = false;

                View v = activity.getWindow().findViewById(android.R.id.content);
                registerForContextMenu(v);
                activity.openContextMenu(v);
                unregisterForContextMenu(v);
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
    public void onResume()
    {
        super.onResume();

        if (torrentsListState != null)
            layoutManager.onRestoreInstanceState(torrentsListState);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        fetchStates();
        if (!TorrentTaskServiceReceiver.getInstance().isRegistered(serviceReceiver))
            TorrentTaskServiceReceiver.getInstance().register(serviceReceiver);
    }

    @Override
    public void onStop()
    {
        super.onStop();

        TorrentTaskServiceReceiver.getInstance().unregister(serviceReceiver);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
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

        if (savedInstanceState != null)
            torrentsListState = savedInstanceState.getParcelable(TAG_TORRENTS_LIST_STATE);
    }

    TorrentTaskServiceReceiver.Callback serviceReceiver = new TorrentTaskServiceReceiver.Callback()
    {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onReceive(Bundle b)
        {
            if (b != null) {
                switch ((TorrentStateMsg.Type)b.getSerializable(TorrentStateMsg.TYPE)) {
                    case TORRENT_ADDED: {
                        Torrent torrent = b.getParcelable(TorrentStateMsg.TORRENT);
                        if (torrent != null) {
                            TorrentListItem item = new TorrentListItem();
                            item.torrentId = torrent.getId();
                            item.name = torrent.getName();
                            item.dateAdded = torrent.getDateAdded();
                            adapter.addItem(item);
                        }
                        break;
                    }
                    case UPDATE_TORRENT: {
                        BasicStateParcel state = b.getParcelable(TorrentStateMsg.STATE);
                        if (state != null)
                            adapter.updateItem(state);
                        break;
                    }
                    case UPDATE_TORRENTS: {
                        handleBasicStates(b.getBundle(TorrentStateMsg.STATES));
                        break;
                    }
                    case TORRENT_REMOVED: {
                        String id = b.getString(TorrentStateMsg.TORRENT_ID);
                        if (id != null)
                            adapter.deleteItem(id);
                        break;
                    }
                }
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

                    FragmentManager fm = getFragmentManager();
                    if (fm != null && fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
                        BaseAlertDialog deleteTorrentDialog = BaseAlertDialog.newInstance(
                                getString(R.string.deleting),
                                (indexes.size() > 1 ?
                                 getString(R.string.delete_selected_torrents) :
                                 getString(R.string.delete_selected_torrent)),
                                R.layout.dialog_delete_torrent,
                                getString(R.string.ok),
                                getString(R.string.cancel),
                                null,
                                MainFragment.this);

                        deleteTorrentDialog.show(fm, TAG_DELETE_TORRENT_DIALOG);
                    }

                    break;
                case R.id.select_all_torrent_menu:
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        if (adapter.isSelected(i))
                            continue;

                        onItemSelected(adapter.getItem(i).torrentId, i);
                    }

                    break;
                case R.id.force_recheck_torrent_menu:
                    mode.finish();
                    TorrentHelper.forceRecheckTorrents(selectedTorrents);
                    selectedTorrents.clear();
                    break;
                case R.id.force_announce_torrent_menu:
                    mode.finish();
                    TorrentHelper.forceAnnounceTorrents(selectedTorrents);
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

        if (!isAdded())
            return;

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
                activity.closeOptionsMenu();
                finish(new Intent(), FragmentCallback.ResultCode.OK);
                break;
            case R.id.feed_menu:
                startActivity(new Intent(activity, FeedActivity.class));
                break;
        }
        return true;
    }

    private void aboutDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ABOUT_DIALOG) == null) {
            BaseAlertDialog aboutDialog = BaseAlertDialog.newInstance(
                    getString(R.string.about_title),
                    null,
                    R.layout.dialog_about,
                    getString(R.string.ok),
                    getString(R.string.about_changelog),
                    null,
                    this);
            aboutDialog.show(fm, TAG_ABOUT_DIALOG);
        }
    }

    private void torrentSortingDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_TORRENT_SORTING) == null) {
            BaseAlertDialog sortingDialog = BaseAlertDialog.newInstance(
                    getString(R.string.sorting),
                    null,
                    R.layout.dialog_sorting,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);
            sortingDialog.show(fm, TAG_TORRENT_SORTING);
        }
    }

    private void addLinkDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ADD_LINK_DIALOG) == null) {
            BaseAlertDialog addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_link_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);

            addLinkDialog.show(fm, TAG_ADD_LINK_DIALOG);
        }
    }

    private boolean checkEditTextField(String s, TextInputLayout layout)
    {
        if (s == null || layout == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
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
        if (filter == null)
            return;

        if (filter.equals(getString(R.string.spinner_downloading_torrents)))
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.DOWNLOADING));
        else if (filter.equals(getString(R.string.spinner_downloaded_torrents)))
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.SEEDING));
        else if (filter.equals(getString(R.string.spinner_downloading_metadata_torrents)))
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter(TorrentStateCode.DOWNLOADING_METADATA));
        else
            adapter.setDisplayFilter(new TorrentListAdapter.DisplayFilter());
    }

    TorrentListAdapter.ViewHolder.ClickListener torrentListListener = new TorrentListAdapter.ViewHolder.ClickListener()
    {
        @Override
        public void onItemClicked(int position, TorrentListItem item)
        {
            if (actionMode == null) {
                /* Mark this torrent as open in the list */
                adapter.markAsOpen(item);
                showDetailTorrent(item.torrentId);
            } else {
                onItemSelected(item.torrentId, position);
            }
        }

        @Override
        public boolean onItemLongClicked(int position, TorrentListItem item)
        {
            if (actionMode == null)
                actionMode = activity.startActionMode(actionModeCallback);
            onItemSelected(item.torrentId, position);

            return true;
        }

        @Override
        public void onPauseButtonClicked(int position, TorrentListItem item)
        {
            TorrentHelper.pauseResumeTorrent(item.torrentId);
        }
    };

    private void onItemSelected(String id, int position)
    {
        toggleSelection(position);

        if (selectedTorrents.contains(id))
            selectedTorrents.remove(id);
        else
            selectedTorrents.add(id);
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        int count = adapter.getSelectedItemCount();

        if (actionMode != null) {
            if (count == 0) {
                actionMode.finish();
            } else {
                actionMode.setTitle(String.valueOf(count));
                actionMode.invalidate();
            }
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
            FragmentManager fm = getFragmentManager();
            if (fm == null)
                return;

            if (fm.findFragmentByTag(TAG_ADD_LINK_DIALOG) != null)
                initAddDialog(dialog);
            else if (fm.findFragmentByTag(TAG_ABOUT_DIALOG) != null)
                initAboutDialog(dialog);
            else if (fm.findFragmentByTag(TAG_TORRENT_SORTING) != null)
                initTorrentSortingDialog(dialog);
        }
    }

    private void initAddDialog(final AlertDialog dialog)
    {
        final TextInputEditText field = dialog.findViewById(R.id.text_input_dialog);
        final TextInputLayout fieldLayout = dialog.findViewById(R.id.layout_text_input_dialog);

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

        positiveButton.setOnClickListener((View v) -> {
            if (field != null && field.getText() != null && fieldLayout != null) {
                String link = field.getText().toString();
                if (checkEditTextField(link, fieldLayout)) {
                    String url;
                    if (link.toLowerCase().startsWith(Utils.MAGNET_PREFIX))
                        url = link;
                    else if (Utils.isHash(link))
                        url = Utils.normalizeMagnetHash(link);
                    else
                        url = Utils.normalizeURL(link);

                    if (url != null)
                        addTorrentDialog(Uri.parse(url));

                    dialog.dismiss();
                }
            }
        });

        /* Inserting a link from the clipboard */
        String clipboard = Utils.getClipboard(activity.getApplicationContext());
        String url = null;
        if (clipboard != null) {
            String c = clipboard.toLowerCase();
            if (c.startsWith(Utils.MAGNET_PREFIX) ||
                c.startsWith(Utils.HTTP_PREFIX) ||
                c.startsWith(Utils.HTTPS_PREFIX) ||
                Utils.isHash(clipboard)) {
                url = clipboard;
            }

            if (field != null && url != null)
                field.setText(url);
        }
    }

    private void initAboutDialog(final AlertDialog dialog)
    {
        TextView version = dialog.findViewById(R.id.about_version);
        if (version != null) {
            String versionName = Utils.getAppVersionName(activity);
            if (versionName != null)
                version.setText(versionName);
        }
    }

    private void initTorrentSortingDialog(final AlertDialog dialog)
    {
        Spinner sp = dialog.findViewById(R.id.dialog_sort_by);
        RadioGroup group = dialog.findViewById(R.id.dialog_sort_direction);

        if (sp != null && group != null) {
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
        if (v == null)
            return;

        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
            CheckBox withFiles = v.findViewById(R.id.dialog_delete_torrent_with_downloaded_files);
            DetailTorrentFragment f = getCurrentDetailFragment();
            if (f != null) {
                String id = f.getTorrentId();
                if (selectedTorrents.contains(id))
                    resetCurOpenTorrent();
            }
            TorrentHelper.deleteTorrents(activity.getApplicationContext(),
                    selectedTorrents, withFiles.isChecked());
            selectedTorrents.clear();

        } else if (fm.findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) != null ||
                   fm.findFragmentByTag(TAG_SAVE_ERROR_DIALOG) != null) {
            if (sentError != null) {
                EditText editText = v.findViewById(R.id.comment);
                String comment = editText.getText().toString();
                Utils.reportError(sentError, comment);
            }

        } else if (fm.findFragmentByTag(TAG_TORRENT_SORTING) != null) {
            Spinner sp = v.findViewById(R.id.dialog_sort_by);
            RadioGroup group = v.findViewById(R.id.dialog_sort_direction);
            String[] columns = activity.getResources().getStringArray(R.array.sort_torrent_values);

            int position = sp.getSelectedItemPosition();
            if (position != -1 && position < columns.length) {
                String column = columns[position];
                pref.edit().putString(activity.getString(R.string.pref_key_sort_torrent_by), column).apply();
            }

            int radioButtonId = group.getCheckedRadioButtonId();
            String direction = TorrentSorting.Direction.ASC.name();
            if (radioButtonId == R.id.dialog_sort_by_descending)
                direction = TorrentSorting.Direction.DESC.name();

            pref.edit().putString(activity.getString(R.string.pref_key_sort_torrent_direction), direction).apply();
            if (adapter != null)
                adapter.setSorting(new TorrentSortingComparator(
                        Utils.getTorrentSorting(activity.getApplicationContext())));
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return;

        if (fm.findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
            selectedTorrents.clear();
        } else if (fm.findFragmentByTag(TAG_ABOUT_DIALOG) != null) {
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

    private void fetchStates()
    {
        Bundle states = TorrentHelper.makeBasicStatesList();
        List<TorrentListItem> items = statesToItems(states);
        if (items.isEmpty()) {
            states = TorrentHelper.makeOfflineStatesList(activity.getApplicationContext());
            items = statesToItems(states);
        }
        reloadAdapter(items);
    }

    private void handleBasicStates(Bundle states)
    {
        if (states == null)
            return;

        reloadAdapter(statesToItems(states));
    }

    private List<TorrentListItem> statesToItems(Bundle states)
    {
        List<TorrentListItem> items = new ArrayList<>();
        for (String key : states.keySet()) {
            BasicStateParcel state = states.getParcelable(key);
            if (state != null)
                items.add(new TorrentListItem(state));
        }

        return items;
    }

    final synchronized void reloadAdapter(final List<TorrentListItem> items)
    {
        adapter.clearAll();
        if (items == null || items.size() == 0)
            adapter.notifyDataSetChanged();
        else
            adapter.addItems(items);
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
        if (uri == null)
            return;

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, uri);
        startActivityForResult(i, ADD_TORRENT_REQUEST);
    }

    private void createTorrentDialog()
    {
        startActivityForResult(new Intent(activity, CreateTorrentActivity.class), CREATE_TORRENT_REQUEST);
    }

    /* TODO: if minSdkVersion will be >= 17, go to getChildFragmentManager() instead of manually managing the fragments */
    private void showDetailTorrent(String id)
    {
        if (Utils.isTwoPane(activity)) {
            FragmentManager fm = getFragmentManager();
            if (fm == null)
                return;
            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);
            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

            if (fragment != null && fragment instanceof DetailTorrentFragment) {
                String oldId = ((DetailTorrentFragment) fragment).getTorrentId();
                if (oldId != null && id.equals(oldId))
                    return;
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
        if (Utils.isTwoPane(activity)) {
            FragmentManager fm = getFragmentManager();
            if (fm == null)
                return;
            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_torrent));
            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    public DetailTorrentFragment getCurrentDetailFragment()
    {
        if (!Utils.isTwoPane(activity))
            return null;

        FragmentManager fm = getFragmentManager();
        if (fm == null)
            return null;

        Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment) fragment : null);
    }

    private void addTorrent(AddTorrentParams params)
    {
        Intent i = new Intent(activity.getApplicationContext(), TorrentTaskService.class);
        i.setAction(TorrentTaskService.ACTION_ADD_TORRENT);
        i.putExtra(TorrentTaskService.TAG_ADD_TORRENT_PARAMS, params);
        i.putExtra(TorrentTaskService.TAG_SAVE_TORRENT_FILE, true);
        activity.startService(i);
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

                            FragmentManager fm = getFragmentManager();
                            if (fm != null && fm.findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) == null) {
                                ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                                        activity.getApplicationContext(),
                                        getString(R.string.error),
                                        getString(R.string.error_open_torrent_file),
                                        Log.getStackTraceString(e),
                                        this);

                                FragmentTransaction ft = fm.beginTransaction();
                                ft.add(errDialog, TAG_ERROR_OPEN_TORRENT_FILE_DIALOG);
                                ft.commitAllowingStateLoss();
                            }
                        }
                    }
                }
                break;
            case ADD_TORRENT_REQUEST:
            case CREATE_TORRENT_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    AddTorrentParams params;
                    params = (requestCode == ADD_TORRENT_REQUEST ? AddTorrentActivity.getResult() :
                                                                   CreateTorrentActivity.getResult());
                    if (params != null)
                        addTorrent(params);
                }
                break;
        }
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
