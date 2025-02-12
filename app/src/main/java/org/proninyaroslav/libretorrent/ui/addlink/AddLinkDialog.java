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

package org.proninyaroslav.libretorrent.ui.addlink;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogAddLinkBinding;
import org.proninyaroslav.libretorrent.ui.ClipboardDialog;
import org.proninyaroslav.libretorrent.ui.main.MainActivity;
import org.proninyaroslav.libretorrent.ui.main.NavBarFragmentDirections;

public class AddLinkDialog extends DialogFragment {
    private AlertDialog alert;
    private MainActivity activity;
    private DialogAddLinkBinding binding;
    private AddLinkViewModel viewModel;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (MainActivity) context;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        unsubscribeClipboardManager();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeClipboardManager();
    }

    private void handleUrlClipItem(String item) {
        if (TextUtils.isEmpty(item))
            return;

        viewModel.link.set(item);
        binding.link.postDelayed(this::addLink, 500);
    }

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

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(AddLinkViewModel.class);

        getParentFragmentManager().setFragmentResultListener(
                ClipboardDialog.KEY_RESULT,
                this,
                (key, bundle) -> handleUrlClipItem(bundle.getString(ClipboardDialog.KEY_CLIPBOARD_ITEM))
        );

        LayoutInflater i = getLayoutInflater();
        binding = DataBindingUtil.inflate(i, R.layout.dialog_add_link, null, false);
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
        /* Dismiss error label if user has changed the text */
        binding.link.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutLink.setErrorEnabled(false);
                binding.layoutLink.setError(null);
            }
        });

        binding.clipboardButton.setOnClickListener((v) -> showClipboardDialog());
        switchClipboardButton();
        viewModel.initLinkFromClipboard();

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view) {
        var builder = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.dialog_add_link_title)
                .setIcon(R.drawable.ic_link_24px)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener((v) -> addLink());

            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelButton.setOnClickListener((v) -> alert.dismiss());
        });
    }

    private void showClipboardDialog() {
        if (isAdded()) {
            NavHostFragment.findNavController(this)
                    .navigate(AddLinkDialogDirections.actionClipboard());
        }
    }

    private void addLink() {
        if (!isAdded()) {
            return;
        }

        String s = viewModel.link.get();
        if (TextUtils.isEmpty(s)) {
            return;
        }
        if (!checkUrlField()) {
            return;
        }

        try {
            s = viewModel.normalizeUrl(s);

        } catch (NormalizeUrlException e) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(getString(R.string.invalid_url, e.getMessage()));
            binding.layoutLink.requestFocus();

            return;
        }

        var action = NavBarFragmentDirections.actionAddTorrent(Uri.parse(s));
        activity.getRootNavController().navigate(action);
        dismiss();
    }

    private boolean checkUrlField() {
        if (TextUtils.isEmpty(binding.link.getText())) {
            binding.layoutLink.setErrorEnabled(true);
            binding.layoutLink.setError(getString(R.string.error_empty_link));
            binding.layoutLink.requestFocus();

            return false;
        }

        binding.layoutLink.setErrorEnabled(false);
        binding.layoutLink.setError(null);

        return true;
    }
}
