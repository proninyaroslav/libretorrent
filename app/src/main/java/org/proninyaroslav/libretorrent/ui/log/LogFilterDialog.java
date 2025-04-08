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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogLogFilterBinding;

public class LogFilterDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        var viewModel = new ViewModelProvider(requireParentFragment()).get(LogViewModel.class);

        var i = getLayoutInflater();
        DialogLogFilterBinding binding = DataBindingUtil.inflate(i, R.layout.dialog_log_filter, null, false);
        binding.setViewModel(viewModel);

        var builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.filter)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setView(binding.getRoot());

        return builder.create();
    }
}
