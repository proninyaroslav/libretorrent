/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.jetbrains.annotations.NotNull;
import org.proninyaroslav.libretorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.CreateTorrentActivity;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.TorrentListAdapter;
import org.proninyaroslav.libretorrent.adapters.TorrentListItem;
import org.proninyaroslav.libretorrent.core.exceptions.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.databinding.FragmentMainBinding;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.viewmodel.MainViewModel;

import java.util.Collections;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import uk.co.markormesher.android_fab.SpeedDialMenuAdapter;
import uk.co.markormesher.android_fab.SpeedDialMenuItem;

/*
 * The list of torrents.
 */

public class MainFragment extends Fragment
        implements TorrentListAdapter.ClickListener
{
    @SuppressWarnings("unused")
    private static final String TAG = MainFragment.class.getSimpleName();

    private static final int TORRENT_FILE_CHOOSE_REQUEST = 1;

    private static final String TAG_DOWNLOAD_LIST_STATE = "download_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_DELETE_TORRENTS_DIALOG = "delete_torrents_dialog";
    private static final String TAG_ADD_LINK_DIALOG = "add_link_dialog";
    private static final String TAG_OPEN_FILE_ERROR_DIALOG = "open_file_error_dialog";

    private AppCompatActivity activity;
    private TorrentListAdapter adapter;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable torrentListState;
    private SelectionTracker<TorrentListItem> selectionTracker;
    private ActionMode actionMode;
    private FragmentMainBinding binding;
    private MainViewModel viewModel;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog deleteTorrentsDialog, addLinkDialog;
    private CompositeDisposable disposables = new CompositeDisposable();

    private enum FabItem
    {
        ADD_LINK,
        OPEN_FILE,
        CREATE_TORRENT
    }

    /* Reverse order */
    private FabItem[] fabItems = new FabItem[] {
            FabItem.CREATE_TORRENT,
            FabItem.OPEN_FILE,
            FabItem.ADD_LINK
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);

        adapter = new TorrentListAdapter(this);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        };
        layoutManager = new LinearLayoutManager(activity);
        binding.torrentList.setLayoutManager(layoutManager);
        binding.torrentList.setItemAnimator(animator);
        binding.torrentList.setEmptyView(binding.emptyViewTorrentList);
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{R.attr.divider});
        binding.torrentList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        a.recycle();
        binding.torrentList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.torrentList,
                new TorrentListAdapter.KeyProvider(adapter),
                new TorrentListAdapter.ItemLookup(binding.torrentList),
                StorageStrategy.createParcelableStorage(TorrentListItem.class))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = activity.startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null)
                        actionMode.finish();
                    actionMode = null;

                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());
                }
            }

            @Override
            public void onSelectionRestored() {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null)
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        adapter.setSelectionTracker(selectionTracker);

        binding.fabButton.setContentCoverColour(Utils.getAttributeColor(activity, R.attr.background));
        binding.fabButton.getContentCoverView().getBackground().setAlpha(128);
        binding.fabButton.setSpeedDialMenuAdapter(fabAdapter);

        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(activity).get(MainViewModel.class);

        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            deleteTorrentsDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_DELETE_TORRENTS_DIALOG);
            addLinkDialog = (BaseAlertDialog)fm.findFragmentByTag(TAG_ADD_LINK_DIALOG);
        }

        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);

        Intent i = activity.getIntent();
        if (i != null && MainActivity.ACTION_ADD_TORRENT_SHORTCUT.equals(i.getAction())) {
            /* Prevents re-reading action after device configuration changes */
            i.setAction(null);
            showAddTorrentMenu();
        }
    }

    private void showAddTorrentMenu()
    {
        /* Show add torrent menu after window is displayed */
        activity.getWindow().findViewById(android.R.id.content).post(() -> {
            View v = activity.getWindow().findViewById(android.R.id.content);
            registerForContextMenu(v);
            activity.openContextMenu(v);
            unregisterForContextMenu(v);
        });
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAdapter();
        subscribeAlertDialog();
        subscribeForceSortAndFilter();
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (torrentListState != null)
            layoutManager.onRestoreInstanceState(torrentListState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            torrentListState = savedInstanceState.getParcelable(TAG_DOWNLOAD_LIST_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        torrentListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_DOWNLOAD_LIST_STATE, torrentListState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    private void subscribeAdapter()
    {
        disposables.add(observeTorrents());
    }

    private Disposable observeTorrents()
    {
        return viewModel.observeAllTorrentsState()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((stateList) ->
                        Flowable.fromIterable(stateList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> {
                            Log.e(TAG, "Getting torrent state list error: " +
                                    Log.getStackTraceString(t));
                        });
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENTS_DIALOG) && deleteTorrentsDialog != null)
                                deleteDownloads();
                            else if (event.dialogTag.equals(TAG_ADD_LINK_DIALOG) && addLinkDialog != null)
                                addLink();
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENTS_DIALOG) && deleteTorrentsDialog != null)
                                deleteTorrentsDialog.dismiss();
                            else if (event.dialogTag.equals(TAG_ADD_LINK_DIALOG) && addLinkDialog != null)
                                addLinkDialog.dismiss();
                            break;
                        case DIALOG_SHOWN:
                            if (event.dialogTag.equals(TAG_ADD_LINK_DIALOG) && addLinkDialog != null)
                                initAddLinkDialog();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void subscribeForceSortAndFilter()
    {
        disposables.add(viewModel.onForceSortAndFilter()
                .filter((force) -> force)
                .observeOn(Schedulers.io())
                .subscribe((force) -> disposables.add(getAllTorrentsSingle())));
    }

    private Disposable getAllTorrentsSingle()
    {
        return viewModel.getAllTorrentsStateSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((stateList) ->
                        Observable.fromIterable(stateList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> {
                            Log.e(TAG, "Getting torrent state list error: " +
                                    Log.getStackTraceString(t));
                        });
    }

    @Override
    public void onItemClicked(@NonNull TorrentListItem item)
    {
        if (Utils.isTwoPane(activity))
            adapter.markAsOpen(item);
        viewModel.openTorrentDetails(item.torrentId);
    }

    @Override
    public void onItemPauseClicked(@NonNull TorrentListItem item)
    {
        viewModel.pauseResumeDownload(item.torrentId);
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
                                    ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);

        activity.getMenuInflater().inflate(R.menu.main_context, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.add_link_menu:
                addLinkDialog();
                break;
            case R.id.open_file_menu:
                openTorrentFileDialog();
                break;
        }

        return true;
    }

    private final SpeedDialMenuAdapter fabAdapter = new SpeedDialMenuAdapter()
    {
        @Override
        public int getCount()
        {
            return fabItems.length;
        }

        @NotNull
        @Override
        public SpeedDialMenuItem getMenuItem(@NotNull Context context, int position)
        {
            if (position < 0 || position >= fabItems.length)
                throw new IllegalArgumentException("Invalid position: " + position);

            FabItem item = fabItems[position];
            switch (item) {
                case ADD_LINK:
                    return new SpeedDialMenuItem(context,
                            R.drawable.ic_link_white_18dp,
                            R.string.add_link);
                case OPEN_FILE:
                    return new SpeedDialMenuItem(context,
                            R.drawable.ic_file_white_18dp,
                            R.string.open_file);
                case CREATE_TORRENT:
                    return new SpeedDialMenuItem(context,
                            R.drawable.ic_mode_edit_white_18dp,
                            R.string.create_torrent);
                default:
                    throw new IllegalArgumentException("Invalid item: " + item);
            }
        }

        @Override
        public boolean onMenuItemClick(int position)
        {
            if (position < 0 || position >= fabItems.length)
                return false;

            FabItem item = fabItems[position];
            switch (item) {
                case ADD_LINK:
                    addLinkDialog();
                    break;
                case OPEN_FILE:
                    openTorrentFileDialog();
                    break;
                case CREATE_TORRENT:
                    createTorrentDialog();
                    break;
                default:
                    return false;
            }

            return true;
        }

        @Override
        public void onPrepareItemLabel(@NotNull Context context, int position, @NotNull TextView label)
        {
            label.setTextAppearance(context, R.style.TextAppearance_MaterialComponents_Subtitle2);
        }

        @Override
        public int getBackgroundColour(int position)
        {
            return ContextCompat.getColor(activity, R.color.accent);
        }

        @Override
        public float fabRotationDegrees()
        {
            return 45f;
        }
    };

    private void setActionModeTitle(int itemCount)
    {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback()
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            mode.getMenuInflater().inflate(R.menu.main_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            switch (item.getItemId()) {
                case R.id.delete_torrent_menu:
                    deleteTorrentsDialog();
                    break;
                case R.id.select_all_torrent_menu:
                    selectAllTorrents();
                    break;
                case R.id.force_recheck_torrent_menu:
                    forceRecheckTorrents();
                    mode.finish();
                    break;
                case R.id.force_announce_torrent_menu:
                    forceAnnounceTorrents();
                    mode.finish();
                    break;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            selectionTracker.clearSelection();
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void deleteTorrentsDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_DELETE_TORRENTS_DIALOG) == null) {
            deleteTorrentsDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    (selectionTracker.getSelection().size() > 1 ?
                            getString(R.string.delete_selected_torrents) :
                            getString(R.string.delete_selected_torrent)),
                    R.layout.dialog_delete_torrent,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            deleteTorrentsDialog.show(fm, TAG_DELETE_TORRENTS_DIALOG);
        }
    }

    private void addLinkDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ADD_LINK_DIALOG) == null) {
            addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_link_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            addLinkDialog.show(fm, TAG_ADD_LINK_DIALOG);
        }
    }

    private void openTorrentFileDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(null,
                getString(R.string.torrent_file_chooser_title),
                FileManagerConfig.FILE_CHOOSER_MODE);
        config.highlightFileTypes = Collections.singletonList("torrent");

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, TORRENT_FILE_CHOOSE_REQUEST);
    }

    private void openFileErrorDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_OPEN_FILE_ERROR_DIALOG) == null) {
            BaseAlertDialog openFileErrorDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_open_torrent_file),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            openFileErrorDialog.show(fm, TAG_OPEN_FILE_ERROR_DIALOG);
        }
    }

    private void createTorrentDialog()
    {
        startActivity(new Intent(activity, CreateTorrentActivity.class));
    }

    private boolean checkUrlField(Editable link, TextInputLayout layoutLink)
    {
        if (link == null)
            return false;

        if (TextUtils.isEmpty(link)) {
            layoutLink.setErrorEnabled(true);
            layoutLink.setError(getString(R.string.error_empty_link));
            layoutLink.requestFocus();

            return false;
        }

        layoutLink.setErrorEnabled(false);
        layoutLink.setError(null);

        return true;
    }

    private void addLink()
    {
        Dialog dialog = addLinkDialog.getDialog();
        if (dialog == null)
            return;

        TextInputEditText field = dialog.findViewById(R.id.text_input_dialog);
        TextInputLayout fieldLayout = dialog.findViewById(R.id.layout_text_input_dialog);
        if (field == null || TextUtils.isEmpty(field.getText()) || fieldLayout == null)
            return;
        if (!checkUrlField(field.getText(), fieldLayout))
            return;

        String url;
        try {
            url = viewModel.normalizeUrl(field.getText().toString());

        } catch (NormalizeUrlException e) {
            fieldLayout.setErrorEnabled(true);
            fieldLayout.setError(String.format(getString(R.string.invalid_url), e.getMessage()));
            fieldLayout.requestFocus();

            return;
        }

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, Uri.parse(url));
        startActivity(i);

        addLinkDialog.dismiss();
    }

    private void initAddLinkDialog()
    {
        Dialog dialog = addLinkDialog.getDialog();
        if (dialog == null)
            return;

        final TextInputEditText field = dialog.findViewById(R.id.text_input_dialog);
        final TextInputLayout fieldLayout = dialog.findViewById(R.id.layout_text_input_dialog);

        /* Dismiss error label if user has changed the text */
        if (field != null && fieldLayout != null) {
            field.addTextChangedListener(new TextWatcher()
            {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count)
                {
                    fieldLayout.setErrorEnabled(false);
                    fieldLayout.setError(null);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        /* Inserting a link from the clipboard */
        String clipboard = Utils.getClipboard(activity.getApplicationContext());
        String url = null;
        if (clipboard != null) {
            String c = clipboard.toLowerCase();
            if (c.startsWith(Utils.MAGNET_PREFIX) ||
                c.startsWith(Utils.HTTP_PREFIX) ||
                Utils.isHash(clipboard)) {
                url = clipboard;
            }

            if (field != null && url != null)
                field.setText(url);
        }
    }

    private void deleteDownloads()
    {
        Dialog dialog = deleteTorrentsDialog.getDialog();
        if (dialog == null)
            return;

        CheckBox withFiles = dialog.findViewById(R.id.delete_with_downloaded_files);

        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.deleteTorrents(ids, withFiles.isChecked())));

        if (actionMode != null)
            actionMode.finish();
        deleteTorrentsDialog.dismiss();

        if (Utils.isTwoPane(activity))
            adapter.markAsOpen(null);
    }

    private void selectAllTorrents()
    {
        int n = adapter.getItemCount();
        if (n > 0) {
            selectionTracker.startRange(0);
            selectionTracker.extendRange(adapter.getItemCount() - 1);
        }
    }

    private void forceRecheckTorrents()
    {
        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.forceRecheckTorrents(ids)));
    }

    private void forceAnnounceTorrents()
    {
        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.forceAnnounceTorrents(ids)));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        if (resultCode != TORRENT_FILE_CHOOSE_REQUEST && resultCode != Activity.RESULT_OK)
            return;

        if (data == null || data.getData() == null) {
            openFileErrorDialog();
            return;
        }

        Intent i = new Intent(activity, AddTorrentActivity.class);
        i.putExtra(AddTorrentActivity.TAG_URI, data.getData());
        startActivity(i);
    }
}
