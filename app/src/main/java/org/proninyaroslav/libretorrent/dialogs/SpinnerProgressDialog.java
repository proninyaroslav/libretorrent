/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;

/*
 * The simple spinner progress dialog.
 */

public class SpinnerProgressDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = SpinnerProgressDialog.class.getSimpleName();
    protected static final String TAG_TITLE = "title";
    protected static final String TAG_MESSAGE = "message";
    protected static final String TAG_PROGRESS = "progress";
    protected static final String TAG_IS_INDETERMINATE = "is_indeterminate";
    protected static final String TAG_IS_CANCELABLE = "is_cancelable";

    /* In the absence of any parameter need set 0 */

    public static SpinnerProgressDialog newInstance(int title, String message, int progress,
                                                    boolean isIndeterminate, boolean isCancelable)
    {
        SpinnerProgressDialog frag = new SpinnerProgressDialog();

        Bundle args = new Bundle();

        args.putInt(TAG_TITLE, title);
        args.putString(TAG_MESSAGE, message);
        args.putInt(TAG_PROGRESS, progress);
        args.putBoolean(TAG_IS_INDETERMINATE, isIndeterminate);
        args.putBoolean(TAG_IS_CANCELABLE, isCancelable);

        frag.setArguments(args);

        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = getArguments();

        int title = args.getInt(TAG_TITLE);
        String message = args.getString(TAG_MESSAGE);
        int progress = args.getInt(TAG_PROGRESS);
        boolean isIndeterminate = args.getBoolean(TAG_IS_INDETERMINATE);
        boolean isCancelable = args.getBoolean(TAG_IS_CANCELABLE);

        ProgressDialog dialog = new ProgressDialog(getActivity());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setIndeterminate(isIndeterminate);
        dialog.setCancelable(isCancelable);
        dialog.setProgressNumberFormat(null);
        dialog.setProgressPercentFormat(null);
        dialog.setProgress(progress);

        return dialog;
    }
}
