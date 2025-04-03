/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addfeed;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogAddFeedChannelBinding;
import org.proninyaroslav.libretorrent.ui.ClipboardDialog;
import org.proninyaroslav.libretorrent.ui.feeds.DeleteFeedDialog;

import io.reactivex.disposables.CompositeDisposable;

public class AddFeedDialog extends DialogFragment {
    private static final String TAG = AddFeedDialog.class.getSimpleName();

    private static final String REQUEST_CLIPBOARD_DIALOG_KEY = TAG + "_clipboard_dialog";
    private static final String REQUEST_DELETE_FEED_DIALOG_KEY = TAG + "_delete_feed_dialog";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private AddFeedViewModel viewModel;
    private DialogAddFeedChannelBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private void subscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(clipListener);
    }

    private void unsubscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.removePrimaryClipChangedListener(clipListener);
    }

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::switchClipboardButton;

    private final ViewTreeObserver.OnWindowFocusChangeListener onFocusChanged =
            (__) -> switchClipboardButton();

    private void switchClipboardButton() {
        ClipData clip = Utils.getClipData(activity.getApplicationContext());
        viewModel.showClipboardButton.set(clip != null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    private void initParams(Uri uri, long feedId) {
        if (uri != null) {
            viewModel.initAddMode(uri);
        } else if (feedId != -1) {
            viewModel.initEditMode(feedId);
        } else {
            viewModel.initAddModeFromClipboard();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        unsubscribeClipboardManager();
        disposables.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeClipboardManager();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setClipboardDialogListener();
        setDeleteFeedDialogListener();
    }

    private void setClipboardDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                REQUEST_CLIPBOARD_DIALOG_KEY,
                this,
                (requestKey, result) -> {
                    var item = result.getString(ClipboardDialog.KEY_RESULT_CLIPBOARD_ITEM);
                    if (item != null) {
                        handleUrlClipItem(item);
                    }
                });
    }

    private void setDeleteFeedDialogListener() {
        getParentFragmentManager().setFragmentResultListener(
                REQUEST_DELETE_FEED_DIALOG_KEY,
                this,
                (requestKey, result) -> {
                    var isDelete = result.getBoolean(DeleteFeedDialog.KEY_RESULT_VALUE);
                    if (isDelete) {
                        deleteChannel();
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(AddFeedViewModel.class);

        var args = AddFeedDialogArgs.fromBundle(getArguments());
        initParams(args.getUri(), args.getFeedId());

        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_add_feed_channel, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();

        binding.getRoot().getViewTreeObserver().addOnWindowFocusChangeListener(onFocusChanged);

        return alert;
    }

    @Override
    public void onDestroyView() {
        binding.getRoot().getViewTreeObserver().removeOnWindowFocusChangeListener(onFocusChanged);

        super.onDestroyView();
    }

    private void initLayoutView() {
        binding.url.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutUrl.setErrorEnabled(false);
                binding.layoutUrl.setError(null);
            }
        });
        binding.filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutFilter.setErrorEnabled(false);
                binding.layoutFilter.setError(null);
            }
        });
        binding.layoutFilter.setEndIconOnClickListener((v) -> binding.expandableLayout.toggle());

        binding.clipboardButton.setOnClickListener((v) -> showClipboardDialog());
        switchClipboardButton();

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view) {
        var builder = new MaterialAlertDialogBuilder(activity)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        if (viewModel.getMode() == AddFeedViewModel.Mode.EDIT) {
            builder.setIcon(R.drawable.ic_edit_24px);
            builder.setTitle(R.string.edit_feed_channel);
            builder.setPositiveButton(R.string.edit, null);
            builder.setNeutralButton(R.string.delete, null);
        } else {
            builder.setIcon(R.drawable.ic_add_24px);
            builder.setTitle(R.string.add_feed_channel);
            builder.setPositiveButton(R.string.add, null);
        }

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            if (viewModel.getMode() == AddFeedViewModel.Mode.EDIT) {
                Button editButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                Button deleteButton = alert.getButton(AlertDialog.BUTTON_NEUTRAL);
                editButton.setOnClickListener((v) -> updateChannel());
                deleteButton.setOnClickListener((v) -> deleteFeedDialog());
            } else {
                Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                addButton.setOnClickListener((v) -> addChannel());
            }
            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelButton.setOnClickListener((v) -> dismiss());
        });
    }

    private void showClipboardDialog() {
        if (!isAdded()) {
            return;
        }

        var action = AddFeedDialogDirections.actionClipboardDialog(REQUEST_CLIPBOARD_DIALOG_KEY);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void handleUrlClipItem(String item) {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.mutableParams.setUrl(item);
    }

    private void deleteFeedDialog() {
        if (!isAdded()) {
            return;
        }

        var action = AddFeedDialogDirections.actionDeleteFeedDialog(
                REQUEST_DELETE_FEED_DIALOG_KEY,
                1
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private boolean checkUrlField(Editable s) {
        if (s == null) {
            return false;
        }

        if (TextUtils.isEmpty(s)) {
            binding.layoutUrl.setErrorEnabled(true);
            binding.layoutUrl.setError(getString(R.string.error_empty_link));
            binding.layoutUrl.requestFocus();

            return false;
        }

        binding.layoutUrl.setErrorEnabled(false);
        binding.layoutUrl.setError(null);

        return true;
    }

    private void addChannel() {
        if (!checkUrlField(binding.url.getText())) {
            return;
        }

        if (!viewModel.addChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_add_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        dismiss();
    }

    private void updateChannel() {
        if (!checkUrlField(binding.url.getText())) {
            return;
        }

        if (!viewModel.updateChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_edit_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        dismiss();
    }

    private void deleteChannel() {
        if (!viewModel.deleteChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_delete_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        dismiss();
    }
}
