/*
 * Copyright (C) 2017 Jared Rummler
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.ui.colorpicker.ColorPickerDialog;
import org.proninyaroslav.libretorrent.ui.colorpicker.ColorPickerDialogArgs;
import org.proninyaroslav.libretorrent.ui.customviews.ColorView;

/**
 * A Preference to select a color
 */
public class ColorPickerPreference extends Preference {
    private final String KEY_COLOR_PICKER_DIALOG_REQUEST = getKey() + "_color_picker_dialog";

    private int color = Color.BLACK;

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorPickerPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setPersistent(true);
        setWidgetLayoutResource(R.layout.pref_widget_color_view);
    }

    @Override
    protected void onClick() {
        super.onClick();

        var dialog = new ColorPickerDialog();
        var args = new ColorPickerDialogArgs.Builder(KEY_COLOR_PICKER_DIALOG_REQUEST)
                .setColor(color)
                .build()
                .toBundle();
        dialog.setArguments(args);

        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .add(dialog, null)
                .commitAllowingStateLoss();
    }

    public FragmentActivity getActivity() {
        var context = getContext();
        if (context instanceof FragmentActivity activity) {
            return activity;
        } else if (context instanceof ContextWrapper contextWrapper) {
            var baseContext = contextWrapper.getBaseContext();
            if (baseContext instanceof FragmentActivity activity) {
                return activity;
            }
        }
        throw new IllegalStateException("Error getting activity from context");
    }

    @Override
    public void onAttached() {
        super.onAttached();

        getActivity().getSupportFragmentManager().setFragmentResultListener(
                KEY_COLOR_PICKER_DIALOG_REQUEST,
                getActivity(),
                (key, bundle) -> saveValue(bundle.getInt(ColorPickerDialog.KEY_RESULT_COLOR))
        );
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ColorView colorView = holder.itemView.findViewById(R.id.color_view);
        if (colorView != null) {
            colorView.setColor(color);
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        super.onSetInitialValue(defaultValue);
        if (defaultValue instanceof Integer) {
            color = (Integer) defaultValue;
            persistInt(color);
        } else {
            color = getPersistedInt(0xFF000000);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, Color.BLACK);
    }

    /**
     * Set the new color
     *
     * @param color The newly selected color
     */
    public void saveValue(@ColorInt int color) {
        this.color = color;
        persistInt(this.color);
        notifyChanged();
        callChangeListener(color);
    }
}