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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;

public class DeleteFeedDialog extends DialogFragment {
    public static final String KEY_RESULT_VALUE = "value";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = DeleteFeedDialogArgs.fromBundle(getArguments());

        return new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_delete_24px)
                .setTitle(R.string.deleting)
                .setMessage(args.getFeedCount() > 1
                        ? R.string.delete_selected_channels
                        : R.string.delete_selected_channel
                )
                .setPositiveButton(R.string.ok, ((dialog, which) -> {
                    var bundle = new Bundle();
                    bundle.putBoolean(KEY_RESULT_VALUE, true);
                    getParentFragmentManager().setFragmentResult(args.getFragmentRequestKey(), bundle);
                    dialog.dismiss();
                }))
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                .create();
    }
}
