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

package org.proninyaroslav.libretorrent;

import android.text.InputFilter;
import android.text.Spanned;

/*
 * Filtering numbers, which are outside the specified range.
 */

public class InputFilterMinMax implements InputFilter
{
    private int min;
    private int max;

    public InputFilterMinMax(int min, int max)
    {
        this.min = min;
        this.max = max;
    }

    public InputFilterMinMax(String min, String max)
    {
        this.min = Integer.parseInt(min);
        this.max = Integer.parseInt(max);
    }

    @Override
    public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3)
    {
        if (charSequence.length() != 0 && charSequence.charAt(0) == '-')
            return null;
        try {
            int input = Integer.parseInt(spanned.toString() + charSequence.toString());
            if (inRange(min, max, input)) {
                return null;
            }

        } catch (NumberFormatException e) {
            /* Ignore */
        }

        return "";
    }

    private boolean inRange(int a, int b, int c)
    {
        return b > a ? c >= a && c <= b : c >= b && c <= a;
    }
}
