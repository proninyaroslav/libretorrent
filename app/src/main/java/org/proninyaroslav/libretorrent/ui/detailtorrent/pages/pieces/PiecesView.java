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

package org.proninyaroslav.libretorrent.ui.detailtorrent.pages.pieces;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.Arrays;

/*
 * A widget for display parts map.
 */

public class PiecesView extends View
{
    private static final float CELL_SIZE_DP = 20f;
    private static final float BORDER_SIZE_DP  = 1f;

    private boolean[] pieces;
    private int cells = 0;
    private int cellSize;
    private int borderSize;
    private int stepSize;
    private int cols = 0;
    private int rows = 0;
    private int margin = 0;
    Paint empty = new Paint();
    Paint complete = new Paint();

    public PiecesView(@NonNull Context context, AttributeSet attrs)
    {
        super(context, attrs);

        create(context, attrs);
    }

    public PiecesView(@NonNull Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        create(context, attrs);
    }

    void create(Context context, AttributeSet attrs)
    {
        cellSize = Utils.dpToPx(getContext(), CELL_SIZE_DP);
        borderSize = Utils.dpToPx(getContext(), BORDER_SIZE_DP);
        stepSize = cellSize + borderSize;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PiecesView);
            int color = a.getColor(R.styleable.PiecesView_pieces_cellColor, 0);
            empty.setColor(color);
            a.recycle();
        }

        TypedArray a = context.obtainStyledAttributes(new TypedValue().data, new int[] { R.attr.colorSecondary });
        complete.setColor(a.getColor(0, 0));
        a.recycle();
    }

    public void setPieces(boolean[] pieces)
    {
        if (pieces == null || Arrays.equals(this.pieces, pieces))
            return;

        int prevLength = (this.pieces != null ? this.pieces.length : 0);
        cells = pieces.length;
        this.pieces = pieces;
        if (prevLength == pieces.length)
            invalidate();
        else
            requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        cols = width / stepSize;
        /* We don't limit rows in the smaller side, thereby preventing cuts display cells */
        rows = (int)Math.ceil((float)cells / (float)cols);
        margin = (width - cols * stepSize) / 2;
        int height = rows * stepSize;

        setMeasuredDimension(width, Math.max(width, height));
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        if (pieces == null)
            return;

        int position = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols && position < pieces.length; c++) {
                Paint paint = (pieces[position] ? complete : empty);
                int left = c * stepSize + borderSize + margin;
                int right = left + stepSize - borderSize * 2;
                int top = r * stepSize + borderSize;
                int bottom = top + stepSize - borderSize * 2;

                canvas.drawRect(left + borderSize, top + borderSize,
                        right + borderSize, bottom + borderSize, paint);
                ++position;
            }
        }
    }
}