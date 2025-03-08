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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.lang3.ArrayUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentAddTorrentInfoBinding;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerFragment;
import org.proninyaroslav.libretorrent.ui.tag.SelectTagDialog;
import org.proninyaroslav.libretorrent.ui.tag.TorrentTagChip;

import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

/*
 * The fragment for displaying torrent meta-information,
 * taken from bencode. Part of AddTorrentFragment.
 */

public class AddTorrentInfoFragment extends Fragment {
    private static final String TAG = AddTorrentInfoFragment.class.getSimpleName();

    private static final String KEY_SELECT_TAG_DIALOG_REQUEST = TAG + "_select_tag_dialog";
    private static final String KEY_CHOOSE_DIRECTORY_DIALOG_REQUEST = TAG + "_choose_directory_dialog";

    private AppCompatActivity activity;
    private AddTorrentViewModel viewModel;
    private FragmentAddTorrentInfoBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public static AddTorrentInfoFragment newInstance() {
        var fragment = new AddTorrentInfoFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSelectTagDialogListener();
        setChooseFolderDialogListener();
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
                    viewModel.addTorrentTag(tag);
                }
        );
    }

    private void setChooseFolderDialogListener() {
        requireParentFragment().getParentFragmentManager().setFragmentResultListener(
                KEY_CHOOSE_DIRECTORY_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    FileManagerFragment.Result resultValue = result.getParcelable(FileManagerFragment.KEY_RESULT);
                    if (resultValue == null || resultValue.config().showMode != FileManagerConfig.Mode.DIR_CHOOSER) {
                        return;
                    }
                    var uri = resultValue.uri();
                    if ((uri) == null) {
                        Snackbar.make(
                                activity,
                                binding.coordinatorLayout,
                                getString(R.string.unable_to_open_folder),
                                Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        viewModel.mutableParams.getDirPath().set(uri);
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_torrent_info, container, false);
        Utils.applyWindowInsets(binding.nestedScrollView,
                WindowInsetsSide.LEFT | WindowInsetsSide.RIGHT | WindowInsetsSide.BOTTOM,
                WindowInsetsCompat.Type.ime());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireParentFragment()).get(AddTorrentViewModel.class);
        binding.setViewModel(viewModel);

        binding.folderChooserButton.setOnClickListener((v) -> showChooseDirDialog());
        binding.name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkNameField(s);
            }
        });
        binding.addTagButton.setOnClickListener((v) -> addTag());
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

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
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
            chip.setOnCloseIconClickListener((v) -> viewModel.removeTorrentTag(tag));
            binding.tagsChipGroup.addView(chip);
        }
    }

    private void showChooseDirDialog() {
        var fs = SystemFacadeHelper.getFileSystemFacade(activity.getApplicationContext());
        String path = null;
        var uri = viewModel.mutableParams.getDirPath().get();
        if (uri != null) {
            try {
                path = fs.getDirPath(uri);
            } catch (UnknownUriException e) {
                Log.e(TAG, "Unknown file Uri", e);
            }
        }
        var config = new FileManagerConfig(
                path,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.Mode.DIR_CHOOSER
        );
        var action = AddTorrentFragmentDirections.actionChooseDirectoryDialog(
                config,
                KEY_CHOOSE_DIRECTORY_DIALOG_REQUEST
        );
        NavHostFragment.findNavController(requireParentFragment()).navigate(action);
    }

    private void checkNameField(Editable s) {
        if (TextUtils.isEmpty(s)) {
            binding.layoutName.setErrorEnabled(true);
            binding.layoutName.setError(getString(R.string.error_field_required));
            binding.layoutName.requestFocus();
        } else {
            binding.layoutName.setErrorEnabled(false);
            binding.layoutName.setError(null);
        }
    }

    private void addTag() {
        var ids = viewModel.getCurrentTorrentTags()
                .stream()
                .map((tag) -> tag.id)
                .collect(Collectors.toList());
        var action = AddTorrentFragmentDirections.actionSelectTagDialog(
                ArrayUtils.toPrimitive(ids.toArray(new Long[0])),
                KEY_SELECT_TAG_DIALOG_REQUEST
        );
        NavHostFragment.findNavController(this).navigate(action);
    }
}