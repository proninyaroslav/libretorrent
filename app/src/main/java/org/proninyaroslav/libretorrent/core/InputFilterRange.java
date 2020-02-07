/*
 * Copyright (C) 2016, 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

import android.text.InputFilter;
import android.text.Spanned;

import androidx.annotation.Nullable;

/*
 * Filtering numbers, which are outside the specified range.
 */

public class InputFilterRange implements InputFilter
{
    public static final InputFilterRange PORT_FILTER = new InputFilterRange.Builder()
            .setMin(0)
            .setMax(65535)
            .build();

    public static final InputFilterRange UNSIGNED_INT = new InputFilterRange.Builder()
            .setMin(0)
            .setMax(Integer.MAX_VALUE)
            .build();

    private Integer min;
    private Integer max;

    private InputFilterRange(Integer min, Integer max)
    {
        this.min = min;
        this.max = max;
    }

    @Nullable
    public Integer getMin()
    {
        return min;
    }

    @Nullable
    public Integer getMax()
    {
        return max;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend)
    {
        if (source.length() == 1 && source.charAt(0) == '-')
            return null;

        try {
            int input = Integer.parseInt(dest.toString() + source.toString());
            if (inRange(input))
                return null;

        } catch (NumberFormatException e) {
            /* Ignore */
        }

        return "";
    }

    private boolean inRange(int num)
    {
        return (min == null || num >= min) && (max == null || num <= max);
    }

    public static class Builder
    {
        private Integer min;
        private Integer max;

        public Builder setMin(int min)
        {
            this.min = min;

            return this;
        }

        public Builder setMax(int max)
        {
            this.max = max;

            return this;
        }

        public InputFilterRange build()
        {
            if (min != null && max != null && min > max)
                throw new IllegalArgumentException("max < min");

            return new InputFilterRange(min, max);
        }
    }
}
