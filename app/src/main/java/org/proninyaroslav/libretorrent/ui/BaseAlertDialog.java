/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/*
 * The base alert dialog fragment. Support customizing the layout, text, buttons, title and style.
 * Don't dismiss when changing the device configuration, unlike the AlertDialog.
 */

public class BaseAlertDialog extends DialogFragment
{
    private static final String TAG = BaseAlertDialog.class.getSimpleName();

    protected static final String TAG_TITLE = "title";
    protected static final String TAG_MESSAGE = "message";
    protected static final String TAG_POS_TEXT = "positive_test";
    protected static final String TAG_NEG_TEXT = "negative_text";
    protected static final String TAG_NEUTRAL_BUTTON = "neutral_button";
    protected static final String TAG_RES_ID_VIEW = "res_id_view";
    protected static final String TAG_AUTO_DISMISS = "auto_dismiss";
    protected SharedViewModel viewModel;

    public static class SharedViewModel extends androidx.lifecycle.ViewModel
    {
        private final PublishSubject<Event> dialogEvents = PublishSubject.create();

        public Observable<Event> observeEvents()
        {
            return dialogEvents;
        }

        public void sendEvent(Event event)
        {
            dialogEvents.onNext(event);
        }
    }

    public enum EventType
    {
        POSITIVE_BUTTON_CLICKED,
        NEGATIVE_BUTTON_CLICKED,
        NEUTRAL_BUTTON_CLICKED,
        DIALOG_SHOWN
    }

    public static class Event
    {
        @Nullable
        public String dialogTag;
        public EventType type;

        public Event(@Nullable String dialogTag, EventType type)
        {
            this.dialogTag = dialogTag;
            this.type = type;
        }
    }

    /* In the absence of any parameter need set 0 or null */

    public static BaseAlertDialog newInstance(String title, String message, int resIdView,
                                              String positiveText, String negativeText,
                                              String neutralText, boolean autoDismiss)
    {
        BaseAlertDialog frag = new BaseAlertDialog();

        Bundle args = new Bundle();
        args.putString(TAG_TITLE, title);
        args.putString(TAG_MESSAGE, message);
        args.putString(TAG_POS_TEXT, positiveText);
        args.putString(TAG_NEG_TEXT, negativeText);
        args.putString(TAG_NEUTRAL_BUTTON, neutralText);
        args.putInt(TAG_RES_ID_VIEW, resIdView);
        args.putBoolean(TAG_AUTO_DISMISS, autoDismiss);

        frag.setArguments(args);

        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        viewModel = new ViewModelProvider(getActivity()).get(SharedViewModel.class);

        Bundle args = getArguments();
        String title = args.getString(TAG_TITLE);
        String message = args.getString(TAG_MESSAGE);
        String positiveText = args.getString(TAG_POS_TEXT);
        String negativeText = args.getString(TAG_NEG_TEXT);
        String neutralText = args.getString(TAG_NEUTRAL_BUTTON);
        int resIdView = args.getInt(TAG_RES_ID_VIEW);
        boolean autoDismiss = args.getBoolean(TAG_AUTO_DISMISS);

        LayoutInflater i = LayoutInflater.from(getActivity());
        View v = null;
        if (resIdView != 0)
            v = i.inflate(resIdView, null);

        return buildDialog(title, message, v, positiveText,
                negativeText, neutralText, autoDismiss);
    }

    private Event makeEvent(EventType type)
    {
        return new Event(getTag(), type);
    }

    protected AlertDialog buildDialog(String title, String message,
                                      View view, String positiveText,
                                      String negativeText, String neutralText,
                                      boolean autoDismiss)
    {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        if (title != null)
            dialog.setTitle(title);

        if (message != null)
            dialog.setMessage(message);

        if (view != null)
            dialog.setView(view);

        if (positiveText != null)
            dialog.setPositiveButton(positiveText, null);

        if (negativeText != null)
            dialog.setNegativeButton(negativeText, null);

        if (neutralText != null)
            dialog.setNeutralButton(neutralText, null);

        final AlertDialog alert = dialog.create();
        alert.setOnShowListener((DialogInterface dialogInterface) -> {
            Button positiveButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutralButton = alert.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (positiveButton != null) {
                positiveButton.setOnClickListener((v) -> {
                    viewModel.sendEvent(makeEvent(EventType.POSITIVE_BUTTON_CLICKED));
                    if (autoDismiss)
                        dismiss();
                });
            }
            if (negativeButton != null) {
                negativeButton.setOnClickListener((v) -> {
                    viewModel.sendEvent(makeEvent(EventType.NEGATIVE_BUTTON_CLICKED));
                    if (autoDismiss)
                        dismiss();
                });
            }
            if (neutralButton != null) {
                neutralButton.setOnClickListener((v) -> {
                    viewModel.sendEvent(makeEvent(EventType.NEUTRAL_BUTTON_CLICKED));
                    if (autoDismiss)
                        dismiss();
                });
            }

            viewModel.sendEvent(makeEvent(EventType.DIALOG_SHOWN));
        });

        return alert;
    }
}
