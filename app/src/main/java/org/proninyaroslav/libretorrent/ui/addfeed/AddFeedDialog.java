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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogAddFeedChannelBinding;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.ClipboardDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class AddFeedDialog extends DialogFragment {
    private static final String TAG = AddFeedDialog.class.getSimpleName();

    private static final String TAG_URI = "uri";
    private static final String TAG_FEED_ID = "feed_id";
    private static final String TAG_DELETE_FEED_DIALOG = "delete_feed_dialog";
    private static final String TAG_CLIPBOARD_DIALOG = "clipboard_dialog";

    private AlertDialog alert;
    private AppCompatActivity activity;
    private AddFeedViewModel viewModel;
    private DialogAddFeedChannelBinding binding;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog deleteFeedDialog;
    private ClipboardDialog clipboardDialog;

    public static AddFeedDialog newInstance(Uri uri) {
        AddFeedDialog frag = new AddFeedDialog();

        Bundle args = new Bundle();
        args.putParcelable(TAG_URI, uri);
        frag.setArguments(args);

        return frag;
    }

    public static AddFeedDialog newInstance(long feedId) {
        AddFeedDialog frag = new AddFeedDialog();

        Bundle args = new Bundle();
        args.putLong(TAG_FEED_ID, feedId);
        frag.setArguments(args);

        return frag;
    }

    private void subscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(clipListener);
    }

    private void unsubscribeClipboardManager() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.removePrimaryClipChangedListener(clipListener);
    }

    private ClipboardManager.OnPrimaryClipChangedListener clipListener = this::switchClipboardButton;

    private final ViewTreeObserver.OnWindowFocusChangeListener onFocusChanged =
            (__) -> switchClipboardButton();

    private void switchClipboardButton() {
        ClipData clip = Utils.getClipData(activity.getApplicationContext());
        viewModel.showClipboardButton.set(clip != null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
            activity.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish(new Intent(), FragmentCallback.ResultCode.BACK);
                }
            });
        }
    }

    private void initParams(Uri uri, long feedId) {
        if (uri != null)
            viewModel.initAddMode(uri);
        else if (feedId != -1)
            viewModel.initEditMode(feedId);
        else
            viewModel.initAddModeFromClipboard();
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Back button handle */
        getDialog().setOnKeyListener((DialogInterface dialog, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                } else {
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    return true;
                }
            } else {
                return false;
            }
        });
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

        subscribeAlertDialog();
        subscribeClipboardManager();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(AddFeedViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        FragmentManager fm = getChildFragmentManager();
        deleteFeedDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_DELETE_FEED_DIALOG);
        clipboardDialog = (ClipboardDialog) fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG);

        long feedId = getArguments().getLong(TAG_FEED_ID, -1);
        Uri uri = getArguments().getParcelable(TAG_URI);
        /* Clean */
        getArguments().putLong(TAG_FEED_ID, -1);
        getArguments().putParcelable(TAG_URI, null);
        initParams(uri, feedId);

        LayoutInflater i = getLayoutInflater();
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_feed_channel, null, false);
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
            builder.setTitle(R.string.edit_feed_channel);
            builder.setPositiveButton(R.string.edit, null);
            builder.setNeutralButton(R.string.delete, null);
        } else {
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
            cancelButton.setOnClickListener((v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
        });
    }

    private void showClipboardDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG) == null) {
            clipboardDialog = new ClipboardDialog();
            clipboardDialog.show(fm, TAG_CLIPBOARD_DIALOG);
        }
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null)
                        return;

                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_FEED_DIALOG) && deleteFeedDialog != null) {
                                deleteChannel();
                                deleteFeedDialog.dismiss();
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_DELETE_FEED_DIALOG) && deleteFeedDialog != null)
                                deleteFeedDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void handleUrlClipItem(String item) {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.mutableParams.setUrl(item);
    }

    private void deleteFeedDialog() {
        if (!isAdded())
            return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_DELETE_FEED_DIALOG) == null) {
            deleteFeedDialog = BaseAlertDialog.newInstance(
                    getString(R.string.deleting),
                    getString(R.string.delete_selected_channel),
                    0,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);

            deleteFeedDialog.show(fm, TAG_DELETE_FEED_DIALOG);
        }
    }

    private boolean checkUrlField(Editable s) {
        if (s == null)
            return false;

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
        if (!checkUrlField(binding.url.getText()))
            return;

        if (!viewModel.addChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_add_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void updateChannel() {
        if (!checkUrlField(binding.url.getText()))
            return;

        if (!viewModel.updateChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_edit_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void deleteChannel() {
        if (!viewModel.deleteChannel()) {
            Toast.makeText(activity,
                            R.string.error_cannot_delete_channel,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        alert.dismiss();
        ((FragmentCallback) activity).onFragmentFinished(this, intent, code);
    }
}
