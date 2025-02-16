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

package org.proninyaroslav.libretorrent.ui.main;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogDeleteTorrentBinding;

public class DeleteTorrentDialog extends DialogFragment {
    public enum Result {
        DELETE,
        DELETE_WITH_FILES,
        CANCEL,
    }

    private static final String TAG = DeleteTorrentDialog.class.getSimpleName();

    public static final String KEY_RESULT = TAG + "_result";
    public static final String KEY_RESULT_VALUE = "result_value";

    private DialogDeleteTorrentBinding binding;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = DeleteTorrentDialogArgs.fromBundle(getArguments());
        binding = DialogDeleteTorrentBinding.inflate(getLayoutInflater(), null, false);

        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_delete_24px)
                .setTitle(R.string.deleting)
                .setMessage(args.getTorrentCount() > 1
                        ? R.string.delete_selected_torrents
                        : R.string.delete_selected_torrent)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, this::onClick)
                .setNegativeButton(R.string.cancel, this::onClick);

        return builder.create();
    }

    private void onClick(DialogInterface dialog, int which) {
        var bundle = new Bundle();
        switch (which) {
            case Dialog.BUTTON_POSITIVE -> bundle.putSerializable(
                    KEY_RESULT_VALUE,
                    binding.deleteWithDownloadedFiles.isChecked()
                            ? Result.DELETE_WITH_FILES
                            : Result.DELETE
            );
            case Dialog.BUTTON_NEGATIVE -> bundle.putSerializable(KEY_RESULT_VALUE, Result.CANCEL);
        }
        getParentFragmentManager().setFragmentResult(KEY_RESULT, bundle);
        dismiss();
    }
}
