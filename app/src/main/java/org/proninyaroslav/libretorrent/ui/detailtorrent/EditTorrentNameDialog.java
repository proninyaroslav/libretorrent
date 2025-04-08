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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogTextInputBinding;

public class EditTorrentNameDialog extends DialogFragment {
    public static final String KEY_RESULT_NAME = "name";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = EditTorrentNameDialogArgs.fromBundle(getArguments());
        var binding = DialogTextInputBinding.inflate(getLayoutInflater(), null, false);

        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_edit_24px)
                .setTitle(R.string.edit_torrent_name)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    var e = binding.textInput.getText();
                    if (TextUtils.isEmpty(e)) {
                        return;
                    }
                    var name = e.toString();
                    var bundle = new Bundle();
                    bundle.putString(KEY_RESULT_NAME, name);
                    getParentFragmentManager().setFragmentResult(args.getFragmentRequestKey(), bundle);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        if (TextUtils.isEmpty(binding.textInput.getText())) {
            binding.textInput.setText(args.getInitialName());
        }

        binding.textInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkNameField(s, binding.layoutTextInput);
            }
        });

        return builder.create();
    }

    private void checkNameField(Editable s, TextInputLayout layoutEditText) {
        if (TextUtils.isEmpty(s)) {
            layoutEditText.setErrorEnabled(true);
            layoutEditText.setError(getString(R.string.error_field_required));
            layoutEditText.requestFocus();
        } else {
            layoutEditText.setErrorEnabled(false);
            layoutEditText.setError(null);
        }
    }
}
