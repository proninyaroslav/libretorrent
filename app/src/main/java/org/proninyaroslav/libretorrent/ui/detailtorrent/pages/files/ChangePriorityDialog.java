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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.files;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.databinding.DialogChangePriorityBinding;

public class ChangePriorityDialog extends DialogFragment {
    public static final String KEY_RESULT_PRIORITY = "priority";

    private DialogChangePriorityBinding binding;
    private String requestKey;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = ChangePriorityDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        binding = DialogChangePriorityBinding.inflate(getLayoutInflater(), null, false);
        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_arrow_upward_alt_24px)
                .setTitle(R.string.dialog_change_priority_title)
                .setPositiveButton(R.string.ok, (dialog, which) -> onPositiveClick())
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                .setView(binding.getRoot());

        int resId = switch (args.getInitialPriority().getType()) {
            case IGNORE -> R.id.priority_low;
            case HIGH -> R.id.priority_high;
            case NORMAL -> R.id.priority_normal;
            default -> -1;
        };

        if (resId == -1) {
            binding.prioritiesGroup.clearCheck();
        } else {
            RadioButton button = binding.getRoot().findViewById(resId);
            button.setChecked(true);
        }

        return builder.create();
    }

    private void onPositiveClick() {
        int radioButtonId = binding.prioritiesGroup.getCheckedRadioButtonId();

        FilePriority.Type priorityType = null;
        if (radioButtonId == R.id.priority_low) {
            priorityType = FilePriority.Type.IGNORE;
        } else if (radioButtonId == R.id.priority_normal) {
            priorityType = FilePriority.Type.NORMAL;
        } else if (radioButtonId == R.id.priority_high) {
            priorityType = FilePriority.Type.HIGH;
        }

        var bundle = new Bundle();
        bundle.putSerializable(KEY_RESULT_PRIORITY, priorityType);
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        dismiss();
    }
}
