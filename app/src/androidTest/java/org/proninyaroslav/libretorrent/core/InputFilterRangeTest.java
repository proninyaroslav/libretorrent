/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.text.SpannableString;

import androidx.core.util.Pair;

import org.junit.Test;

import static org.junit.Assert.*;

public class InputFilterRangeTest
{
    static class FilterCase
    {
        String description;
        Pair<Integer, Integer> range;
        CharSequence source;
        SpannableString dest;
        CharSequence output;

        FilterCase(String description, Pair<Integer, Integer> range,
                   CharSequence source, SpannableString dest, CharSequence output)
        {
            this.description = description;
            this.range = range;
            this.source = source;
            this.dest = dest;
            this.output = output;
        }
    }

    private FilterCase[] filterCaseList = new FilterCase[] {
            new FilterCase(
                    "simple range",
                    Pair.create(-10, 10),
                    "5",
                    new SpannableString(""),
                    null),
            new FilterCase(
                    "simple range (out of range)",
                    Pair.create(-10, 10),
                    "1",
                    new SpannableString("-1"),
                    ""),
            new FilterCase(
                    "simple range (out of range 2)",
                    Pair.create(-10, 10),
                    "-11",
                    new SpannableString(""),
                    ""),
            new FilterCase(
                    "only minus sign",
                    Pair.create(-10, 10),
                    "-",
                    new SpannableString(""),
                    null),
            new FilterCase(
                    "number and added minus sign",
                    Pair.create(-10, 10),
                    "5",
                    new SpannableString("-"),
                    null),
            new FilterCase(
                    "number and added minus sign (out of range)",
                    Pair.create(-10, 10),
                    "11",
                    new SpannableString("-"),
                    ""),
            new FilterCase(
                    "number and added minus sign + number",
                    Pair.create(-10, -1),
                    "0",
                    new SpannableString("-1"),
                    null),
            new FilterCase(
                    "number and added minus sign + number (out of range)",
                    Pair.create(-10, 10),
                    "10",
                    new SpannableString("-1"),
                    ""),
            new FilterCase(
                    "IP range",
                    Pair.create(0, 65535),
                    "65530",
                    new SpannableString(""),
                    null),
            new FilterCase(
                    "only with max value",
                    Pair.create(null, 10),
                    "5",
                    new SpannableString(""),
                    null),
            new FilterCase(
                    "only with min value",
                    Pair.create(5, null),
                    "10",
                    new SpannableString(""),
                    null),
    };

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRange_maxGreatMin()
    {
        new InputFilterRange.Builder()
                .setMin(10)
                .setMax(0)
                .build();
    }

    @Test
    public void testBuildRange()
    {
        InputFilterRange f = new InputFilterRange.Builder()
                .build();
        assertNull(f.getMin());
        assertNull(f.getMax());

        f = new InputFilterRange.Builder()
                .setMin(0)
                .setMax(10)
                .build();
        assertEquals(new Integer(0), f.getMin());
        assertEquals(new Integer(10), f.getMax());

        f = new InputFilterRange.Builder()
                .setMin(Integer.MIN_VALUE)
                .setMax(Integer.MAX_VALUE)
                .build();
        assertEquals(new Integer(Integer.MIN_VALUE), f.getMin());
        assertEquals(new Integer(Integer.MAX_VALUE), f.getMax());

        f = new InputFilterRange.Builder()
                .setMax(-1)
                .build();
        assertEquals(new Integer(-1), f.getMax());

        f = new InputFilterRange.Builder()
                .setMin(10)
                .build();
        assertEquals(new Integer(10), f.getMin());

        f = new InputFilterRange.Builder()
                .setMax(Integer.MAX_VALUE)
                .build();
        assertNull(f.getMin());
        assertEquals(new Integer(Integer.MAX_VALUE), f.getMax());

        f = new InputFilterRange.Builder()
                .setMin(Integer.MIN_VALUE)
                .build();
        assertEquals(new Integer(Integer.MIN_VALUE), f.getMin());
        assertNull(f.getMax());
    }

    @Test
    public void testFilter()
    {
        for (FilterCase filterCase : filterCaseList)
            runFilterCase(filterCase);
    }

    private void runFilterCase(FilterCase filterCase)
    {
        InputFilterRange.Builder builder = new InputFilterRange.Builder();
        if (filterCase.range.first != null)
            builder.setMin(filterCase.range.first);
        if (filterCase.range.second != null)
            builder.setMax(filterCase.range.second);

        InputFilterRange f = builder.build();

        assertEquals(filterCase.description,
                filterCase.output,
                f.filter(filterCase.source, 0, 0, filterCase.dest, 0, 0));
    }
}