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

package org.proninyaroslav.libretorrent.ui.colorpicker;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.skydoves.colorpickerview.flag.FlagMode;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.DialogColorPickerBinding;

public class ColorPickerDialog extends DialogFragment {
    public static final String KEY_RESULT_COLOR = "color";

    private DialogColorPickerBinding binding;
    private ColorPickerViewModel viewModel;
    private String requestKey;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var args = ColorPickerDialogArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();
        binding = DialogColorPickerBinding.inflate(
                getLayoutInflater(),
                null,
                false
        );
        viewModel = new ViewModelProvider(this).get(ColorPickerViewModel.class);

        var builder = new MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.drawable.ic_palette_24px)
                .setTitle(R.string.color_picker)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, ((dialog, which) -> selectColor()))
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dismiss()));

        initColorPicker(args);

        return builder.create();
    }

    private void initColorPicker(ColorPickerDialogArgs args) {
        var state = viewModel.getState();
        var color = state == null ? args.getColor() : state.color();

        var bubbleFlag = new CustomBubbleFlag(requireActivity());
        bubbleFlag.setFlagMode(FlagMode.ALWAYS);
        binding.colorPicker.setFlagView(bubbleFlag);
        binding.colorPicker.attachBrightnessSlider(binding.brightnessSlide);

        binding.colorPicker.setLifecycleOwner(this);
        binding.colorPicker.setInitialColor(color);

        binding.colorPicker.setColorListener((ColorEnvelopeListener) (envelope, fromUser) ->
                viewModel.setColor(envelope.getColor()));
    }

    private void selectColor() {
        var state = viewModel.getState();
        if (state == null) {
            return;
        }
        var bundle = new Bundle();
        bundle.putInt(KEY_RESULT_COLOR, state.color());
        getParentFragmentManager().setFragmentResult(requestKey, bundle);
        dismiss();
    }
}
