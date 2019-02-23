/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.format.Formatter;
import android.widget.ImageView;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;

import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

public class BindingAdapterUtils
{
    @BindingAdapter(value = {"fileSize", "formatFileSize"}, requireAll = false)
    public static void formatFileSize(@NonNull TextView view,
                                      long fileSize,
                                      @Nullable String formatFileSize)
    {
        Context context = view.getContext();
        String sizeStr = (fileSize >= 0 ?
                Formatter.formatFileSize(context, fileSize) :
                context.getString(R.string.not_available));
        view.setText((formatFileSize == null ? sizeStr : String.format(formatFileSize, sizeStr)));
    }

    @BindingAdapter("colorFilter")
    public static void setColorFilter(@NonNull ImageView view, int colorFilter)
    {
        view.getDrawable().setColorFilter(colorFilter, PorterDuff.Mode.SRC_IN);
    }

    @BindingAdapter({"formatDate"})
    public static void formatDate(@NonNull TextView view, long date)
    {
        view.setText(SimpleDateFormat.getDateTimeInstance()
                .format(new Date(date)));
    }
}
