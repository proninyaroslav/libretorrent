/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.ui.customviews;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.color.MaterialColors;

import org.proninyaroslav.libretorrent.R;

public class MaterialSwipeRefreshLayout extends SwipeRefreshLayout {
    public MaterialSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setColorSchemeColors(MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant));
        setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this, R.attr.colorSurfaceBright));
    }
}
