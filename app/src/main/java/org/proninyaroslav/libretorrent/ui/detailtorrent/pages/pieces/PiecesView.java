/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.Arrays;

/*
 * A widget for display pieces map.
 */

public class PiecesView extends View {
    private static final float CELL_SIZE_BIG_DP = 20f;
    private static final float CELL_SIZE_SMALL_DP = 10f;
    private static final float BORDER_SIZE_DP = 1f;

    private static final boolean[] UNINITIALIZED_VIEW_PIECES = new boolean[10];

    private boolean[] pieces;
    private int borderSize;
    private MeasureResult measureResult;
    private final Paint empty = new Paint();
    private final Paint complete = new Paint();

    public PiecesView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        create();
    }

    public PiecesView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        Arrays.fill(UNINITIALIZED_VIEW_PIECES, false);
        borderSize = Utils.dpToPx(getContext(), BORDER_SIZE_DP);
        pieces = UNINITIALIZED_VIEW_PIECES;

        complete.setColor(MaterialColors.getColor(this, R.attr.colorPrimaryInverse));
        empty.setColor(MaterialColors.getColor(this, R.attr.colorSurfaceVariant));
    }

    public void setPieces(boolean[] pieces) {
        if (pieces == null || Arrays.equals(this.pieces, pieces)) {
            return;
        }

        int prevLength = this.pieces != null ? this.pieces.length : 0;
        this.pieces = pieces;

        if (prevLength == pieces.length) {
            invalidate();
        } else {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        var result = measure(width, pieces.length, CELL_SIZE_BIG_DP);
        if (result.height >= result.width) {
            result = measure(width, pieces.length, CELL_SIZE_SMALL_DP);
        }
        measureResult = result;

        setMeasuredDimension(result.width, result.height);
    }

    private MeasureResult measure(int width, int piecesLength, float cellSizeDp) {
        int cellSize = Utils.dpToPx(getContext(), cellSizeDp);
        int stepSize = cellSize + borderSize;
        int cols = width / stepSize;
        /* We don't limit rows in the smaller side, thereby preventing cuts display cells */
        int rows = (int) Math.ceil((float) piecesLength / (float) cols);
        int margin = (width - cols * stepSize) / 2;
        int height = rows * stepSize;

        return new MeasureResult(stepSize, width, height, rows, cols, margin);
    }


    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int position = 0;
        for (int r = 0; r < measureResult.rows; r++) {
            for (int c = 0; c < measureResult.cols && position < pieces.length; c++) {
                var paint = pieces[position] ? complete : empty;
                int left = c * measureResult.stepSize + borderSize + measureResult.margin;
                int right = left + measureResult.stepSize - borderSize * 2;
                int top = r * measureResult.stepSize + borderSize;
                int bottom = top + measureResult.stepSize - borderSize * 2;

                canvas.drawRect(
                        left + borderSize,
                        top + borderSize,
                        right + borderSize,
                        bottom + borderSize,
                        paint
                );
                ++position;
            }
        }
    }

    private record MeasureResult(int stepSize, int width, int height,
                                 int rows, int cols, int margin) {
    }
}