/*
 * Copyright (C) 2024 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.filemanager;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogGoToFolderBinding;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;

public class GoToFolderDialog extends DialogFragment {
    @SuppressWarnings("unused")
    private static final String TAG = GoToFolderDialog.class.getSimpleName();

    private AlertDialog alert;
    private AppCompatActivity activity;
    private DialogGoToFolderBinding binding;
    private GoToFolderViewModel viewModel;

    public static GoToFolderDialog newInstance() {
        GoToFolderDialog frag = new GoToFolderDialog();

        Bundle args = new Bundle();
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity) context;
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Back button handle */
        getDialog().setOnKeyListener((DialogInterface dialog, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    onBackPressed();
                }
                return true;
            } else {
                return false;
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        ViewModelProvider provider = new ViewModelProvider(activity);
        viewModel = provider.get(GoToFolderViewModel.class);

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_go_to_folder, null, false);
        binding.setViewModel(viewModel);

        initLayoutView();

        return alert;
    }

    private void initLayoutView() {
        /* Dismiss error label if user has changed the text */
        binding.path.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.layoutPath.setErrorEnabled(false);
                binding.layoutPath.setError(null);
            }
        });

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view) {
        var builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.go_to_folder)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view);

        alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener((v) -> goToDir());

            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancelButton.setOnClickListener((v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
        });
    }

    private void goToDir() {
        if (!isAdded()) {
            return;
        }

        Intent i = new Intent();
        i.putExtra(FileManagerDialog.TAG_URI, Uri.parse(viewModel.path.get()));

        finish(i, FragmentCallback.ResultCode.OK);
    }

    public void onBackPressed() {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        alert.dismiss();
        ((FragmentCallback) activity).onFragmentFinished(this, intent, code);
    }
}
