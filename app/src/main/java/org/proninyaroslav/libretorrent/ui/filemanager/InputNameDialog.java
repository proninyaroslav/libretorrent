/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogTextInputBinding;
import org.proninyaroslav.libretorrent.ui.ClipboardDialog;

public class InputNameDialog extends DialogFragment {
    private static final String TAG = ClipboardDialog.class.getSimpleName();
    public static final String KEY_RESULT = TAG + "_result";
    public static final String KEY_NAME = "name";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var binding = DialogTextInputBinding.inflate(getLayoutInflater(), null, false);
        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_folder_24px)
                .setTitle(R.string.dialog_new_folder_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    var e = binding.textInput.getText();
                    if (e == null) {
                        return;
                    }
                    var name = e.toString();
                    var bundle = new Bundle();
                    bundle.putString(KEY_NAME, name);
                    getParentFragmentManager().setFragmentResult(KEY_RESULT, bundle);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        return builder.create();
    }
}
