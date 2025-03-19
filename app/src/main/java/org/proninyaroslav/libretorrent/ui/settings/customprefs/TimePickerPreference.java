package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import org.proninyaroslav.libretorrent.R;

import java.util.Date;

/**
 * A Preference to select a specific Time with a time picker dialog.
 *
 * @author Jakob Ulbrich
 */
public class TimePickerPreference extends DialogPreference {
    /**
     * In Minutes after midnight
     */
    private int time;

    public TimePickerPreference(Context context) {
        // Delegate to other constructor
        this(context, null);
    }

    public TimePickerPreference(Context context, AttributeSet attrs) {
        // Delegate to other constructor
        // Use the preferenceStyle as the default style
        this(context, attrs, R.attr.preferenceStyle);
    }

    public TimePickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        // Delegate to other constructor
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public TimePickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // Du custom stuff here
        // ...
        // read attributes etc.
    }

    /**
     * Gets the time from the Shared Preferences
     *
     * @return The current preference value
     */
    public int getTime() {
        return time;
    }

    /**
     * Saves the time to the SharedPreferences
     *
     * @param time The time to save
     */
    public void setTime(int time) {
        this.time = time;

        // Save to SharedPreference
        persistInt(time);
        notifyChanged();
    }

    /**
     * Called when a Preference is being inflated and the default value attribute needs to be read
     */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // The type of this preference is Int, so we read the default value from the attributes
        // as Int. Fallback value is set to 0.
        return a.getInt(index, 0);
    }

    /**
     * Implement this to set the initial value of the Preference.
     */
    @Override
    protected void onSetInitialValue(Object defaultValue) {
        setTime((int) defaultValue);
    }

    @Override
    public CharSequence getSummary() {
        String prefix = (TextUtils.isEmpty(super.getSummary()) ? "" : super.getSummary() + "\n\n");

        return prefix + formatTime();
    }

    private String formatTime() {
        return DateFormat.getTimeFormat(getContext()).format(new Date(0, 0, 0, time / 60, time % 60));
    }
}
