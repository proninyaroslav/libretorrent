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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.sorting.BaseSorting;
import org.proninyaroslav.libretorrent.core.sorting.FileTreeSorting;
import org.proninyaroslav.libretorrent.databinding.FragmentAddTorrentFilesBinding;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/*
 * The fragment for list files of torrent. Part of AddTorrentFragment.
 */

public class AddTorrentFilesFragment extends Fragment implements DownloadableFilesAdapter.ClickListener {
    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final long SEARCH_DEBOUNCE_MS = 200;

    private AppCompatActivity activity;
    private FragmentAddTorrentFilesBinding binding;
    private AddTorrentViewModel viewModel;
    private LinearLayoutManager layoutManager;
    private DownloadableFilesAdapter adapter;
    /* Save state scrolling */
    private Parcelable listFilesState;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private final PublishSubject<String> searchQueryChanges = PublishSubject.create();

    public static AddTorrentFilesFragment newInstance() {
        AddTorrentFilesFragment fragment = new AddTorrentFilesFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_torrent_files, container, false);

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
    public void onStop() {
        super.onStop();

        disposable.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAdapter();
        subscribeSearch();
    }

    private void subscribeSearch() {
        disposable.add(searchQueryChanges
                .debounce(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(viewModel::setSearchQuery));
    }

    private void subscribeAdapter() {
        disposable.add(viewModel.children
                .subscribeOn(Schedulers.computation())
                .flatMapSingle((state) ->
                        Flowable.fromIterable(state.items())
                                .map((tree) -> new DownloadableFileItem(tree, state.searchActive()))
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

        viewModel = new ViewModelProvider(requireParentFragment()).get(AddTorrentViewModel.class);
        binding.setViewModel(viewModel);

        layoutManager = new LinearLayoutManager(activity);
        binding.fileList.setLayoutManager(layoutManager);
        adapter = new DownloadableFilesAdapter(this);
        binding.fileList.setAdapter(adapter);

        initSearchAndSort();
    }

    private void initSearchAndSort() {
        var query = viewModel.getSearchQuery();
        if (!query.isEmpty()) {
            binding.searchEditText.setText(query);
            setSearchExpanded(true);
        }
        binding.searchToggleButton.setOnClickListener((v) ->
                setSearchExpanded(binding.searchInputLayout.getVisibility() != View.VISIBLE));
        binding.filterToggleButton.setOnClickListener((v) ->
                binding.sortRow.setVisibility(binding.sortRow.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
        binding.searchInputLayout.setStartIconOnClickListener((v) -> {
            binding.searchEditText.setText("");
            setSearchExpanded(false);
        });
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQueryChanges.onNext(s.toString());
            }
        });

        switch (viewModel.getSortColumn()) {
            case name -> binding.sortByName.setChecked(true);
            case size -> binding.sortBySize.setChecked(true);
        }
        switch (viewModel.getSortDirection()) {
            case ASC -> binding.sortDirectionToggleButton.check(R.id.sort_asc_button);
            case DESC -> binding.sortDirectionToggleButton.check(R.id.sort_desc_button);
        }

        binding.sortAscButton.setOnClickListener((v) ->
                viewModel.setSort(viewModel.getSortColumn(), BaseSorting.Direction.ASC));
        binding.sortDescButton.setOnClickListener((v) ->
                viewModel.setSort(viewModel.getSortColumn(), BaseSorting.Direction.DESC));

        binding.sortChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            var column = checkedIds.contains(R.id.sort_by_size)
                    ? FileTreeSorting.SortingColumns.size
                    : FileTreeSorting.SortingColumns.name;
            viewModel.setSort(column, viewModel.getSortDirection());
        });
    }

    private void setSearchExpanded(boolean expanded) {
        var imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        if (expanded) {
            binding.searchInputLayout.setVisibility(View.VISIBLE);
            binding.searchEditText.requestFocus();
            if (imm != null) {
                imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            binding.searchInputLayout.setVisibility(View.GONE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.searchEditText.getWindowToken(), 0);
            }
        }
    }

    private void updateFileSize() {
        if (viewModel.fileTree == null) {
            return;
        }

        binding.filesSize.setText(getString(R.string.files_size,
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.selectedFileSize()),
                Formatter.formatFileSize(activity.getApplicationContext(),
                        viewModel.fileTree.size())));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (layoutManager != null) {
            listFilesState = layoutManager.onSaveInstanceState();
            outState.putParcelable(TAG_LIST_FILES_STATE, listFilesState);
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
    public void onItemClicked(@NonNull DownloadableFileItem item) {
        if (item.name.equals(BencodeFileTree.PARENT_DIR)) {
            viewModel.upToParentDirectory();
        } else if (!item.isFile) {
            viewModel.chooseDirectory(item.name);
        }
    }

    @Override
    public void onItemCheckedChanged(@NonNull DownloadableFileItem item, boolean selected) {
        if (item.path != null) {
            viewModel.selectFileByIndex(item.index, selected);
        } else {
            viewModel.selectFile(item.name, selected);
        }
        updateFileSize();
    }
}
