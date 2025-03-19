/*
 * Copyright 2018 The Android Open Source Project
 * Copyright 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;

import java.util.Objects;

/**
 * Preference based on android.preference.SliderPreference but uses support preference as a base
 * . It contains a title and a {@link Slider} and an optional Slider value {@link TextView}.
 * The actual preference layout is customizable by setting {@code android:layout} on the
 * preference widget layout or {@code SliderPreferenceStyle} attribute.
 *
 * <p>The {@link Slider} within the preference can be defined adjustable or not by setting {@code
 * adjustable} attribute. If adjustable, the preference will be responsive to DPAD left/right keys.
 * Otherwise, it skips those keys.
 *
 * <p>The {@link Slider} value view can be shown or disabled by setting {@code showSliderValue}
 * attribute to true or false, respectively.
 *
 * <p>Other {@link Slider} specific attributes (e.g. {@code title, summary, defaultValue, min,
 * max})
 * can be set directly on the preference widget layout.
 */
public class SliderPreference extends Preference {
    public enum Label {
        // Mode that draws the label floating above the bounds of this view.
        FLOATING(0),
        // Mode that draws the label within the bounds of the view.
        WITHIN_BOUNDS(1),
        // Mode that prevents the label from being drawn.
        GONE(2),
        // Mode that always draws the label.
        VISIBLE(3);

        Label(int value) {
            this.value = value;
        }

        private final int value;

        public static Label fromValue(int value) {
            var enumValues = Label.class.getEnumConstants();
            for (Label ev : Objects.requireNonNull(enumValues)) {
                if (ev.value == value) {
                    return ev;
                }
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    private static final String TAG = "SliderPreference";
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            float mSliderValue;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            float mMin;
    private float mMax;
    private float mSliderStep;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mTrackingTouch;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            Slider mSlider;
    // Whether the Slider should respond to the left/right keys
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mAdjustable;
    // Whether the SliderPreference should continuously save the Slider value while it is being
    // dragged.
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mUpdatesContinuously;
    // Determines the label behavior used.
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            Label mSliderLabelBehavior;

    private final Slider.OnSliderTouchListener mSliderTouchListener = new Slider.OnSliderTouchListener() {
        @Override
        public void onStartTrackingTouch(@NonNull Slider slider) {
            mTrackingTouch = true;
        }

        @Override
        public void onStopTrackingTouch(@NonNull Slider slider) {
            mTrackingTouch = false;
            if (slider.getValue() + mMin != mSliderValue) {
                syncValueInternal(slider);
            }
        }
    };
    /**
     * Listener reacting to the {@link Slider} changing value by the user
     */
    private final Slider.OnChangeListener mSliderChangeListener = new Slider.OnChangeListener() {
        @Override
        public void onValueChange(@NonNull Slider slider, float progress, boolean fromUser) {
            if (fromUser && (mUpdatesContinuously || !mTrackingTouch)) {
                syncValueInternal(slider);
            }
        }
    };

    /**
     * Listener reacting to the user pressing DPAD left/right keys if {@code
     * adjustable} attribute is set to true; it transfers the key presses to the {@link Slider}
     * to be handled accordingly.
     */
    private final View.OnKeyListener mSliderKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            if (!mAdjustable && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                // Right or left keys are pressed when in non-adjustable mode; Skip the keys.
                return false;
            }

            // We don't want to propagate the click keys down to the Slider view since it will
            // create the ripple effect for the thumb.
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                return false;
            }

            if (mSlider == null) {
                Log.e(TAG, "Slider view is null and hence cannot be adjusted.");
                return false;
            }
            return mSlider.onKeyDown(keyCode, event);
        }
    };

    public SliderPreference(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        try (TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SliderPreference, defStyleAttr, defStyleRes)) {

            // The ordering of these two statements are important. If we want to set max first, we need
            // to perform the same steps by changing min/max to max/min as following:
            // mMax = a.getInt(...) and setMin(...).
            mMin = a.getFloat(R.styleable.SliderPreference_sliderMin, 0f);
            setMax(a.getFloat(R.styleable.SliderPreference_sliderMax, 100f));
            setSliderStep(a.getFloat(R.styleable.SliderPreference_sliderStep, 0f));
            mAdjustable = a.getBoolean(R.styleable.SliderPreference_sliderAdjustable, true);
            mUpdatesContinuously = a.getBoolean(R.styleable.SliderPreference_sliderUpdatesContinuously,
                    false);
            setLabelBehavior(Label.fromValue(
                    a.getInt(R.styleable.SliderPreference_sliderLabelBehavior,
                            Label.FLOATING.value)));
        }

        setLayoutResource(R.layout.pref_widget_slider);
    }

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SliderPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnKeyListener(mSliderKeyListener);
        mSlider = (Slider) holder.findViewById(R.id.slider);

        if (mSlider == null) {
            Log.e(TAG, "Slider view is null in onBindViewHolder.");
            return;
        }
        mSlider.addOnChangeListener(mSliderChangeListener);
        mSlider.addOnSliderTouchListener(mSliderTouchListener);
        mSlider.setValueFrom(mMin);
        mSlider.setValueTo(mMax);
        // If the Step is not zero, use that. Otherwise, use the default mKeyProgressStep
        // in AbsSlider when it's zero. This default Step value is set by AbsSlider
        // after calling setMax. That's why it's important to call setKeyProgressStep after
        // calling setMax() since setMax() can change the Step value.
        if (mSliderStep != 0) {
            mSlider.setStepSize(mSliderStep);
        } else {
            mSliderStep = mSlider.getStepSize();
        }

        int labelBehavior = -1;
        switch (mSliderLabelBehavior) {
            case FLOATING -> labelBehavior = LabelFormatter.LABEL_FLOATING;
            case WITHIN_BOUNDS -> labelBehavior = LabelFormatter.LABEL_WITHIN_BOUNDS;
            case GONE -> labelBehavior = LabelFormatter.LABEL_GONE;
            case VISIBLE -> labelBehavior = LabelFormatter.LABEL_VISIBLE;
        }
        mSlider.setLabelBehavior(labelBehavior);

        mSlider.setValue(mSliderValue - mMin);
        mSlider.setEnabled(isEnabled());
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = 0;
        }
        setValue(getPersistedFloat((Float) defaultValue));
    }

    @Override
    protected @Nullable Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getFloat(index, 0);
    }

    /**
     * Gets the lower bound set on the {@link Slider}.
     *
     * @return The lower bound set
     */
    public float getMin() {
        return mMin;
    }

    /**
     * Sets the lower bound on the {@link Slider}.
     *
     * @param min The lower bound to set
     */
    public void setMin(float min) {
        if (min > mMax) {
            min = mMax;
        }
        if (min != mMin) {
            mMin = min;
            notifyChanged();
        }
    }

    /**
     * Returns the amount of Step change via each arrow key click. This value is derived from
     * user's specified Step value if it's not zero. Otherwise, the default value is picked
     * from the default mKeyProgressStep value in {@link Slider}.
     *
     * @return The amount of Step on the {@link Slider} performed after each user's arrow
     * key press
     */
    public final float getSliderStep() {
        return mSliderStep;
    }

    /**
     * Sets the Step amount on the {@link Slider} for each arrow key press.
     *
     * @param sliderStep The amount to Step or decrement when the user presses an
     *                   arrow key.
     */
    public final void setSliderStep(float sliderStep) {
        if (sliderStep != mSliderStep) {
            mSliderStep = Math.min(mMax - mMin, Math.abs(sliderStep));
            notifyChanged();
        }
    }

    /**
     * Returns the label behavior used.
     *
     * @see #setLabelBehavior(Label)
     */
    public Label getLabelBehavior() {
        return mSliderLabelBehavior;
    }

    /**
     * Determines the label behavior used.
     *
     * @see Label
     */
    public void setLabelBehavior(Label labelBehavior) {
        if (labelBehavior != mSliderLabelBehavior) {
            mSliderLabelBehavior = labelBehavior;
            notifyChanged();
        }
    }

    /**
     * Gets the upper bound set on the {@link Slider}.
     *
     * @return The upper bound set
     */
    public float getMax() {
        return mMax;
    }

    /**
     * Sets the upper bound on the {@link Slider}.
     *
     * @param max The upper bound to set
     */
    public final void setMax(float max) {
        if (max < mMin) {
            max = mMin;
        }
        if (max != mMax) {
            mMax = max;
            notifyChanged();
        }
    }

    /**
     * Gets whether the {@link Slider} should respond to the left/right keys.
     *
     * @return Whether the {@link Slider} should respond to the left/right keys
     */
    public boolean isAdjustable() {
        return mAdjustable;
    }

    /**
     * Sets whether the {@link Slider} should respond to the left/right keys.
     *
     * @param adjustable Whether the {@link Slider} should respond to the left/right keys
     */
    public void setAdjustable(boolean adjustable) {
        mAdjustable = adjustable;
    }

    /**
     * Gets whether the {@link SliderPreference} should continuously save the {@link Slider} value
     * while it is being dragged. Note that when the value is true,
     * {@link Preference.OnPreferenceChangeListener} will be called continuously as well.
     *
     * @return Whether the {@link SliderPreference} should continuously save the {@link Slider}
     * value while it is being dragged
     * @see #setUpdatesContinuously(boolean)
     */
    public boolean getUpdatesContinuously() {
        return mUpdatesContinuously;
    }

    /**
     * Sets whether the {@link SliderPreference} should continuously save the {@link Slider} value
     * while it is being dragged.
     *
     * @param updatesContinuously Whether the {@link SliderPreference} should continuously save
     *                            the {@link Slider} value while it is being dragged
     * @see #getUpdatesContinuously()
     */
    public void setUpdatesContinuously(boolean updatesContinuously) {
        mUpdatesContinuously = updatesContinuously;
    }

    private void setValueInternal(float sliderValue, boolean notifyChanged) {
        if (sliderValue < mMin) {
            sliderValue = mMin;
        }
        if (sliderValue > mMax) {
            sliderValue = mMax;
        }

        if (sliderValue != mSliderValue) {
            mSliderValue = sliderValue;
            persistFloat(sliderValue);
            if (notifyChanged) {
                notifyChanged();
            }
        }
    }

    /**
     * Gets the current progress of the {@link Slider}.
     *
     * @return The current progress of the {@link Slider}
     */
    public float getValue() {
        return mSliderValue;
    }

    /**
     * Sets the current progress of the {@link Slider}.
     *
     * @param sliderValue The current progress of the {@link Slider}
     */
    public void setValue(float sliderValue) {
        setValueInternal(sliderValue, true);
    }

    /**
     * Persist the {@link Slider}'s Slider value if callChangeListener returns true, otherwise
     * set the {@link Slider}'s value to the stored value.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void syncValueInternal(@NonNull Slider slider) {
        float sliderValue = mMin + slider.getValue();
        if (sliderValue != mSliderValue) {
            if (callChangeListener(sliderValue)) {
                setValueInternal(sliderValue, false);
            } else {
                slider.setValue(mSliderValue - mMin);
            }
        }
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.mSliderValue = mSliderValue;
        myState.mMin = mMin;
        myState.mMax = mMax;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(@Nullable Parcelable state) {
        if (state == null || !state.getClass().equals(SliderPreference.SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SliderPreference.SavedState myState = (SliderPreference.SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mSliderValue = myState.mSliderValue;
        mMin = myState.mMin;
        mMax = myState.mMax;
        notifyChanged();
    }

    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state of this preference.
     *
     * <p>It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SliderPreference.SavedState> CREATOR =
                new Parcelable.Creator<>() {
                    @Override
                    public SliderPreference.SavedState createFromParcel(Parcel in) {
                        return new SliderPreference.SavedState(in);
                    }

                    @Override
                    public SliderPreference.SavedState[] newArray(int size) {
                        return new SliderPreference.SavedState[size];
                    }
                };

        float mSliderValue;
        float mMin;
        float mMax;

        SavedState(Parcel source) {
            super(source);

            // Restore the click counter
            mSliderValue = source.readInt();
            mMin = source.readInt();
            mMax = source.readInt();
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            // Save the click counter
            dest.writeFloat(mSliderValue);
            dest.writeFloat(mMin);
            dest.writeFloat(mMax);
        }
    }
}
