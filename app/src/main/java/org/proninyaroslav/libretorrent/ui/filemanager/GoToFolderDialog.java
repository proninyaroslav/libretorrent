/*
 * Copyright (C) 2024-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogTextInputBinding;

public class GoToFolderDialog extends DialogFragment {
    @SuppressWarnings("unused")
    private static final String TAG = GoToFolderDialog.class.getSimpleName();

    public static final String KEY_RESULT = TAG + "_result";
    public static final String KEY_PATH = "path";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        var binding = DialogTextInputBinding.inflate(getLayoutInflater());
        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_folder_open_24px)
                .setTitle(R.string.go_to_folder)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    var e = binding.textInput.getText();
                    if (e == null) {
                        return;
                    }
                    var bundle = new Bundle();
                    bundle.putString(KEY_PATH, e.toString());
                    getParentFragmentManager().setFragmentResult(KEY_RESULT, bundle);
                    dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                .setView(binding.getRoot());

        return builder.create();
    }
}
