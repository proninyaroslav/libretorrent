/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogLogFilterBinding;

public class SessionLogFilterDialog extends DialogFragment {
    private AlertDialog alert;
    private AppCompatActivity activity;
    private DialogLogFilterBinding binding;
    private LogViewModel viewModel;

    public static SessionLogFilterDialog newInstance() {
        SessionLogFilterDialog frag = new SessionLogFilterDialog();

        Bundle args = new Bundle();
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        viewModel = new ViewModelProvider(activity).get(LogViewModel.class);

        LayoutInflater i = getLayoutInflater();
        binding = DataBindingUtil.inflate(i, R.layout.dialog_log_filter, null, false);
        binding.setViewModel(viewModel);

        initLayoutView(binding.getRoot());

        return alert;
    }

    private void initLayoutView(View view) {
        var builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.filter)
                .setNegativeButton(R.string.cancel, (dialog, which) -> alert.dismiss())
                .setView(view);

        alert = builder.create();
    }
}
