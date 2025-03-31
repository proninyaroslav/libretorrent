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

package org.proninyaroslav.libretorrent.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentPreferenceBinding;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.MaterialEditTextPreferenceDialog;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.MaterialListPreferenceDialog;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePickerPreference;

public abstract class CustomPreferenceFragment extends PreferenceFragmentCompat {
    protected FragmentPreferenceBinding binding;
    private AppCompatActivity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        binding = FragmentPreferenceBinding.bind(view);
        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());
        binding.fab.hide();

        var context = requireContext();
        var res = context.getResources();
        var list = getListView();
        list.setHasFixedSize(true);
        list.setNestedScrollingEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(
                0,
                Utils.dpToPx(context, 16),
                0,
                (int) (res.getDimension(R.dimen.list_fab_padding) / res.getDisplayMetrics().density)
        );

        if (Utils.isTwoPane(context) && getParentFragmentManager().getBackStackEntryCount() < 2) {
            binding.appBar.setNavigationIcon(null);
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof ListPreference p) {
            showListPreferenceDialog(p);
        } else if (preference instanceof EditTextPreference p) {
            showEditTextPreferenceDialog(p);
        } else if (preference instanceof TimePickerPreference p) {
            showMaterialTimePicker(p);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void showListPreferenceDialog(ListPreference preference) {
        var dialogFragment = MaterialListPreferenceDialog.newInstance(preference.getKey());
        //noinspection deprecation
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
    }

    private void showEditTextPreferenceDialog(EditTextPreference preference) {
        var dialogFragment = MaterialEditTextPreferenceDialog.newInstance(preference.getKey());
        //noinspection deprecation
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
    }

    private void showMaterialTimePicker(TimePickerPreference timePickerPreference) {
        final String tag = timePickerPreference.getKey() + "_time_picker";
        var builder = new MaterialTimePicker.Builder();

        var minutesAfterMidnight = timePickerPreference.getTime();
        int hours = minutesAfterMidnight / 60;
        int minutes = minutesAfterMidnight % 60;
        boolean is24hour = DateFormat.is24HourFormat(getContext());

        builder.setTimeFormat(is24hour ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(hours)
                .setMinute(minutes)
                .setPositiveButtonText(timePickerPreference.getPositiveButtonText())
                .setNegativeButtonText(timePickerPreference.getNegativeButtonText());

        var picker = builder.build();
        picker.addOnPositiveButtonClickListener((v) -> {
            var dialog = (MaterialTimePicker) getChildFragmentManager().findFragmentByTag(tag);
            if (dialog == null) {
                return;
            }
            var curHours = dialog.getHour();
            var curMinutes = dialog.getMinute();
            int curMinutesAfterMidnight = (curHours * 60) + curMinutes;
            // This allows the client to ignore the user value.
            if (timePickerPreference.callChangeListener(curMinutesAfterMidnight)) {
                timePickerPreference.setTime(curMinutesAfterMidnight);
            }
        });
        picker.show(getChildFragmentManager(), tag);
    }

    @NonNull
    @Override
    protected CustomPreferenceGroupAdapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new CustomPreferenceGroupAdapter(preferenceScreen);
    }
}
