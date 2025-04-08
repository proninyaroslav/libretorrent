/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.files;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsFilesBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsViewModel;

import java.util.Collections;
import java.util.Iterator;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for list files of torrent. Part of TorrentDetailsFragment.
 */

public class TorrentsDetailsFilesFragment extends Fragment
        implements TorrentContentFilesAdapter.ClickListener {
    private static final String TAG = TorrentsDetailsFilesFragment.class.getSimpleName();

    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String SELECTION_TRACKER_ID = "selection_tracker_0";
    private static final String KEY_CHANGE_PRIORITY_DIALOG_REQUEST = TAG + "_change_priority_dialog";

    private AppCompatActivity activity;
    private FragmentTorrentDetailsFilesBinding binding;
    private TorrentDetailsViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private SelectionTracker<TorrentContentFileItem> selectionTracker;
    private ActionMode actionMode;
    private TorrentContentFilesAdapter adapter;
    /* Save state scrolling */
    private Parcelable listFilesState;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static TorrentsDetailsFilesFragment newInstance() {
        var fragment = new TorrentsDetailsFilesFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setChangePriorityDialogListener();
    }

    private void setChangePriorityDialogListener() {
        requireParentFragment().getParentFragmentManager().setFragmentResultListener(
                KEY_CHANGE_PRIORITY_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var priorityType = (FilePriority.Type) result.getSerializable(
                            ChangePriorityDialog.KEY_RESULT_PRIORITY
                    );
                    if (priorityType != null) {
                        changePriority(priorityType);
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_torrent_details_files, container, false);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.fileList,
                    WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.fileList,
                    WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (actionMode != null) {
            actionMode.finish();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
    }

    private void subscribeAdapter() {
        disposables.add(viewModel.getDirChildren()
                .subscribeOn(Schedulers.computation())
                .flatMapSingle((children) ->
                        Flowable.fromIterable(children)
                                .map(TorrentContentFileItem::new)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((children) -> {
                    adapter.submitList(children);
                    updateFileSize();
                }));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        var provider = new ViewModelProvider(requireParentFragment());
        viewModel = provider.get(TorrentDetailsViewModel.class);
        binding.setViewModel(viewModel);

        layoutManager = new LinearLayoutManager(activity);
        binding.fileList.setLayoutManager(layoutManager);
        adapter = new TorrentContentFilesAdapter(this);
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
        binding.fileList.setItemAnimator(animator);
        binding.fileList.setAdapter(adapter);

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.fileList,
                new TorrentContentFilesAdapter.KeyProvider(adapter),
                new TorrentContentFilesAdapter.ItemLookup(binding.fileList),
                StorageStrategy.createParcelableStorage(TorrentContentFileItem.class))
                .withSelectionPredicate(new SelectionTracker.SelectionPredicate<>() {
                    @Override
                    public boolean canSetStateForKey(@NonNull TorrentContentFileItem key, boolean nextState) {
                        return !key.name.equals(BencodeFileTree.PARENT_DIR);
                    }

                    @Override
                    public boolean canSetStateAtPosition(int position, boolean nextState) {
                        return true;
                    }

                    @Override
                    public boolean canSelectMultiple() {
                        return true;
                    }
                })
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<>() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();

                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = activity.startSupportActionMode(actionModeCallback);
                    setActionModeTitle(selectionTracker.getSelection().size());

                } else if (!selectionTracker.hasSelection()) {
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                    actionMode = null;

                } else {
                    setActionModeTitle(selectionTracker.getSelection().size());

                    /* Show/hide menu items after change selected files */
                    int size = selectionTracker.getSelection().size();
                    if (size == 1 || size == 2) {
                        actionMode.invalidate();
                    }
                }
            }

            @Override
            public void onSelectionRestored() {
                super.onSelectionRestored();

                actionMode = activity.startSupportActionMode(actionModeCallback);
                setActionModeTitle(selectionTracker.getSelection().size());
            }
        });

        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }
        adapter.setSelectionTracker(selectionTracker);
    }

    private void updateFileSize() {
        if (viewModel.fileTree == null) {
            return;
        }

        binding.filesSize.setText(getString(R.string.files_size,
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.nonIgnoreFileSize()),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.size())));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            listFilesState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);
        }
        if (selectionTracker != null) {
            selectionTracker.onSaveInstanceState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            listFilesState = savedInstanceState.getParcelable(TAG_LIST_FILES_STATE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (listFilesState != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(listFilesState);
        }
    }

    @Override
    public void onItemClicked(@NonNull TorrentContentFileItem item) {
        if (item.name.equals(BencodeFileTree.PARENT_DIR)) {
            viewModel.upToParentDirectory();
        } else if (!item.isFile) {
            viewModel.chooseDirectory(item.name);
        } else {
            disposables.add(viewModel.getFilePath(item.name)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            (path) -> openFile(item.name, path),
                            this::handleOpenFileError
                    ));
        }
    }

    @Override
    public void onItemCheckedChanged(@NonNull TorrentContentFileItem item, boolean selected) {
        viewModel.applyPriority(Collections.singletonList(item.name),
                new FilePriority(selected ? FilePriority.Type.NORMAL : FilePriority.Type.IGNORE));
        updateFileSize();
    }

    private void setActionModeTitle(int itemCount) {
        actionMode.setTitle(String.valueOf(itemCount));
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem shareStreamUrl = menu.findItem(R.id.share_stream_url_menu);
            shareStreamUrl.setVisible(false);

            Selection<TorrentContentFileItem> selection = selectionTracker.getSelection();
            if (selection.size() != 1) {
                return true;
            }
            Iterator<TorrentContentFileItem> it = selection.iterator();
            if (it.hasNext() && !viewModel.isFile(it.next().name)) {
                return true;
            }

            shareStreamUrl.setVisible(true);

            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.torrent_details_files_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.change_priority_menu) {
                showPriorityDialog();
            } else if (itemId == R.id.share_stream_url_menu) {
                shareStreamUrl();
                mode.finish();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionTracker.clearSelection();
            Utils.showActionModeStatusBar(activity, false);
        }
    };

    private void showPriorityDialog() {
        if (!isAdded()) {
            return;
        }
        var selections = new MutableSelection<TorrentContentFileItem>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.name))
                .toList()
                .subscribe((fileNames) -> {
                    var priority = viewModel.getFilesPriority(fileNames);
                    var action = TorrentDetailsFragmentDirections.actionOpenChangePriorityDialog(
                            KEY_CHANGE_PRIORITY_DIALOG_REQUEST,
                            priority
                    );
                    NavHostFragment.findNavController(this).navigate(action);
                })
        );
    }

    private void changePriority(FilePriority.Type priorityType) {
        var priority = new FilePriority(priorityType);
        MutableSelection<TorrentContentFileItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        disposables.add(Observable.fromIterable(selections)
                .map((selection -> selection.name))
                .toList()
                .subscribe((fileNames) -> viewModel.applyPriority(fileNames, priority)));

        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void shareStreamUrl() {
        MutableSelection<TorrentContentFileItem> selections = new MutableSelection<>();
        selectionTracker.copySelection(selections);

        Iterator<TorrentContentFileItem> it = selections.iterator();
        if (!it.hasNext()) {
            return;
        }
        int fileIndex = it.next().index;

        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "url");
        sharingIntent.putExtra(Intent.EXTRA_TEXT, viewModel.getStreamUrl(fileIndex));

        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }

    private void openFile(String fileName, Uri path) {
        if (fileName == null || path == null) {
            return;
        }

        startActivity(viewModel.makeOpenFileIntent(fileName, path));
    }

    private void handleOpenFileError(Throwable err) {
        Log.e(TAG, "Unable to open file: " + Log.getStackTraceString(err));
        Snackbar.make(
                activity,
                binding.coordinatorLayout,
                getString(R.string.unable_to_open_file),
                Snackbar.LENGTH_SHORT
        ).show();
    }
}
