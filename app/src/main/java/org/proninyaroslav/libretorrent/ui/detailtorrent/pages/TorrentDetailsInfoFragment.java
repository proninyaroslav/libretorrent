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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.lang3.ArrayUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentTorrentDetailsInfoBinding;
import org.proninyaroslav.libretorrent.ui.detailtorrent.EditTorrentNameDialog;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.detailtorrent.TorrentDetailsViewModel;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.ui.home.NavBarFragment;
import org.proninyaroslav.libretorrent.ui.home.NavBarFragmentDirections;
import org.proninyaroslav.libretorrent.ui.tag.SelectTagDialog;
import org.proninyaroslav.libretorrent.ui.tag.TorrentTagChip;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of TorrentDetailsInfo.
 */

public class TorrentDetailsInfoFragment extends Fragment {
    public static final String TAG = TorrentDetailsInfoFragment.class.getSimpleName();
    private static final String KEY_FILE_MANAGER_DIALOG_REQUEST = TAG + "_file_manager_dialog";
    private static final String KEY_SELECT_TAG_DIALOG_REQUEST = TAG + "_select_tag_dialog";
    private static final String KEY_EDIT_TORRENT_NAME_DIALOG_REQUEST = TAG + "_edit_torrent_name_dialog";

    private MainActivity activity;
    private TorrentDetailsViewModel viewModel;
    private FragmentTorrentDetailsInfoBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static TorrentDetailsInfoFragment newInstance() {
        var fragment = new TorrentDetailsInfoFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var navBarFragment = activity.findNavBarFragment(this);

        setSelectTagDialogListener();
        setEditTorrentNameDialogListener();
        if (navBarFragment != null) {
            setFileManagerDialogListener(navBarFragment);
        }
    }

    private void setSelectTagDialogListener() {
        requireParentFragment().getParentFragmentManager().setFragmentResultListener(
                KEY_SELECT_TAG_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    TagInfo tag = result.getParcelable(SelectTagDialog.KEY_RESULT_TAG);
                    if (tag == null) {
                        return;
                    }
                    disposables.add(viewModel.addTag(tag)
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                    );
                }
        );
    }

    private void setEditTorrentNameDialogListener() {
        requireParentFragment().getParentFragmentManager().setFragmentResultListener(
                KEY_EDIT_TORRENT_NAME_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var name = result.getString(EditTorrentNameDialog.KEY_RESULT_NAME);
                    if (name == null) {
                        return;
                    }
                    viewModel.mutableParams.setName(name);
                }
        );
    }

    private void setFileManagerDialogListener(@NonNull NavBarFragment navBarFragment) {
        navBarFragment.getParentFragmentManager().setFragmentResultListener(
                KEY_FILE_MANAGER_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.DIR_CHOOSER) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if (uri == null) {
                        Snackbar.make(
                                activity,
                                binding.coordinatorLayout,
                                getString(R.string.unable_to_open_folder),
                                Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        viewModel.mutableParams.setDirPath(uri);
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_torrent_details_info, container, false);

        var provider = new ViewModelProvider(requireParentFragment());
        viewModel = provider.get(TorrentDetailsViewModel.class);
        binding.setViewModel(viewModel);

        if (Utils.isLargeScreenDevice(activity)) {
            Utils.applyWindowInsets(binding.nestedScrollView,
                    WindowInsetsSide.BOTTOM | WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        } else {
            Utils.applyWindowInsets(binding.nestedScrollView,
                    WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT);
        }

        binding.folderChooserButton.setOnClickListener((v) -> showChooseDirDialog());
        binding.editNameButton.setOnClickListener((v) -> showEditNameDialog());
        binding.addTagButton.setOnClickListener((v) -> addTag());

        return binding.getRoot();
    }

    private void addTag() {
        disposables.add(viewModel.getTags()
                .subscribeOn(Schedulers.io())
                .flatMap((tags) -> Observable.fromIterable(tags)
                        .map((tag) -> tag.id)
                        .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((ids) -> {
                    var action = TorrentDetailsFragmentDirections.actionSelectTagDialog(
                            ArrayUtils.toPrimitive(ids.toArray(new Long[0])),
                            KEY_SELECT_TAG_DIALOG_REQUEST
                    );
                    NavHostFragment.findNavController(this).navigate(action);
                })
        );
    }

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((tags) -> {
                    binding.emptyTagsPlaceholder.setVisibility(
                            tags.isEmpty() ? View.VISIBLE : View.GONE
                    );
                    addTagChips(tags);
                })
        );
    }

    private void addTagChips(List<TagInfo> tags) {
        binding.tagsChipGroup.removeAllViews();
        for (var tag : tags) {
            var chip = new TorrentTagChip(activity, tag);
            chip.setOnCloseIconClickListener((v) ->
                    disposables.add(viewModel.removeTag(tag)
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                    ));
            binding.tagsChipGroup.addView(chip);
        }
    }

    private void showChooseDirDialog() {
        var config = new FileManagerConfig(
                null,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.Mode.DIR_CHOOSER
        );
        var action = NavBarFragmentDirections
                .actionOpenDirectoryDialog(config, KEY_FILE_MANAGER_DIALOG_REQUEST);
        activity.getRootNavController().navigate(action);
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeTags();
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }

    private void showEditNameDialog() {
        if (!isAdded()) {
            return;
        }

        var action = TorrentDetailsFragmentDirections.actionOpenEditTorrentNameDialog(
                viewModel.mutableParams.getName(),
                KEY_EDIT_TORRENT_NAME_DIALOG_REQUEST
        );
        NavHostFragment.findNavController(this).navigate(action);
    }
}