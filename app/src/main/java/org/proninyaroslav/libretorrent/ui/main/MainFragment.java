/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.leinardi.android.speeddial.SpeedDialActionItem;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentMainBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.addlink.AddLinkActivity;
import org.proninyaroslav.libretorrent.ui.addtorrent.AddTorrentActivity;
import org.proninyaroslav.libretorrent.ui.createtorrent.CreateTorrentActivity;
import org.proninyaroslav.libretorrent.ui.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

import java.util.Collections;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The list of torrents.
 */

public class MainFragment extends Fragment
        implements TorrentListAdapter.ClickListener
{
    private static final String TAG = MainFragment.class.getSimpleName();

    private static final String TAG_TORRENT_LIST_STATE = "torrent_list_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String TAG_DELETE_TORRENTS_DIALOG = "delete_torrents_dialog";
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
    private MsgMainViewModel msgViewModel;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog deleteTorrentsDialog;
    private CompositeDisposable disposables = new CompositeDisposable();

    public CoordinatorLayout getCoordinatorLayout() {
        return binding.mainCoordinatorLayout;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(MainViewModel.class);
        msgViewModel = provider.get(MsgMainViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

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

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<TorrentListItem>() {
            @Override
            public void onSelectionChanged()
            {
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
            public void onSelectionRestored()
            {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null)
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        adapter.setSelectionTracker(selectionTracker);

        initFabSpeedDial();

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
        View v = activity.getWindow().findViewById(android.R.id.content);
        if (v == null)
            return;
        v.post(() -> {
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
        subscribeTorrentsDeleted();
        subscribeMsgViewModel();
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
            torrentListState = savedInstanceState.getParcelable(TAG_TORRENT_LIST_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        torrentListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_TORRENT_LIST_STATE, torrentListState);
        selectionTracker.onSaveInstanceState(outState);

        super.onSaveInstanceState(outState);
    }

    private void subscribeAdapter()
    {
        disposables.add(observeTorrents());
    }

    private Disposable observeTorrents()
    {
        return viewModel.observeAllTorrentsInfo()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoList) ->
                        Flowable.fromIterable(infoList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null)
                        return;

                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENTS_DIALOG) && deleteTorrentsDialog != null) {
                                deleteTorrents();
                                deleteTorrentsDialog.dismiss();
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_TORRENTS_DIALOG) && deleteTorrentsDialog != null)
                                deleteTorrentsDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void subscribeForceSortAndFilter()
    {
        disposables.add(viewModel.observeForceSortAndFilter()
                .filter((force) -> force)
                .observeOn(Schedulers.io())
                .subscribe((force) -> disposables.add(getAllTorrentsSingle())));
    }

    private Disposable getAllTorrentsSingle()
    {
        return viewModel.getAllTorrentsInfoSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((infoList) ->
                        Observable.fromIterable(infoList)
                                .filter(viewModel.getFilter())
                                .map(TorrentListItem::new)
                                .sorted(viewModel.getSorting())
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::submitList,
                        (Throwable t) -> Log.e(TAG, "Getting torrent info list error: " +
                                Log.getStackTraceString(t)));
    }

    private void subscribeTorrentsDeleted()
    {
        disposables.add(viewModel.observeTorrentsDeleted()
                .subscribeOn(Schedulers.io())
                .filter((id) -> {
                    TorrentListItem item = adapter.getOpenedItem();
                    if (item == null)
                        return false;

                    return id.equals(item.torrentId);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));
    }

    private void subscribeMsgViewModel()
    {
        disposables.add(msgViewModel.observeTorrentDetailsClosed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> {
                    if (Utils.isTwoPane(activity))
                        adapter.markAsOpen(null);
                }));
    }

    @Override
    public void onItemClicked(@NonNull TorrentListItem item)
    {
        if (Utils.isTwoPane(activity))
            adapter.markAsOpen(item);
        msgViewModel.torrentDetailsOpened(item.torrentId);
    }

    @Override
    public void onItemPauseClicked(@NonNull TorrentListItem item)
    {
        viewModel.pauseResumeTorrent(item.torrentId);
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
        int itemId = item.getItemId();
        if (itemId == R.id.add_link_menu) {
            addLinkDialog();
        } else if (itemId == R.id.open_file_menu) {
            openTorrentFileDialog();
        }

        return true;
    }

    private void initFabSpeedDial()
    {
        binding.fabButton.setOnActionSelectedListener((item) -> {
            int id = item.getId();
            if (id == R.id.main_fab_add_link) {
                addLinkDialog();
            } else if (id == R.id.main_fab_open_file) {
                openTorrentFileDialog();
            } else if (id == R.id.main_fab_create_torrent) {
                createTorrentDialog();
            } else {
                return false;
            }

            binding.fabButton.close();

            return true;
        });

        binding.fabButton.addActionItem(new SpeedDialActionItem.Builder(
                R.id.main_fab_create_torrent,
                R.drawable.ic_mode_edit_18dp)
                .setLabel(R.string.create_torrent)
                .create());

        binding.fabButton.addActionItem(new SpeedDialActionItem.Builder(
                R.id.main_fab_open_file,
                R.drawable.ic_file_18dp)
                .setLabel(R.string.open_file)
                .create());

        binding.fabButton.addActionItem(new SpeedDialActionItem.Builder(
                R.id.main_fab_add_link,
                R.drawable.ic_link_18dp)
                .setLabel(R.string.add_link)
                .create());
    }

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
            int itemId = item.getItemId();
            if (itemId == R.id.delete_torrent_menu) {
                deleteTorrentsDialog();
            } else if (itemId == R.id.select_all_torrent_menu) {
                selectAllTorrents();
            } else if (itemId == R.id.force_recheck_torrent_menu) {
                forceRecheckTorrents();
                mode.finish();
            } else if (itemId == R.id.force_announce_torrent_menu) {
                forceAnnounceTorrents();
                mode.finish();
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
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_DELETE_TORRENTS_DIALOG) == null) {
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
        startActivity(new Intent(activity, AddLinkActivity.class));
    }

    private void openTorrentFileDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(null,
                getString(R.string.torrent_file_chooser_title),
                FileManagerConfig.FILE_CHOOSER_MODE);
        config.highlightFileTypes = Collections.singletonList("torrent");

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        torrentFileChoose.launch(i);
    }

    private void openFileErrorDialog()
    {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_OPEN_FILE_ERROR_DIALOG) == null) {
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

    private void deleteTorrents()
    {
        Dialog dialog = deleteTorrentsDialog.getDialog();
        if (dialog == null)
            return;

        CheckBox withFilesCheckBox = dialog.findViewById(R.id.delete_with_downloaded_files);
        boolean withFiles = withFilesCheckBox.isChecked();

        MutableSelection<TorrentListItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.torrentId))
                .toList()
                .subscribe((ids) -> viewModel.deleteTorrents(ids, withFiles)));

        if (actionMode != null)
            actionMode.finish();
    }

    @SuppressLint("RestrictedApi")
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

    final ActivityResultLauncher<Intent> torrentFileChoose = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }

                if (data == null || data.getData() == null) {
                    openFileErrorDialog();
                    return;
                }

                Intent i = new Intent(activity, AddTorrentActivity.class);
                i.putExtra(AddTorrentActivity.TAG_URI, data.getData());
                startActivity(i);
            }
    );
}
