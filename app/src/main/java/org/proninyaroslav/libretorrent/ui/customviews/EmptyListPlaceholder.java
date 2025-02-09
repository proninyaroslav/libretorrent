/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.proninyaroslav.libretorrent.R;

public class EmptyListPlaceholder extends FrameLayout {
    private TextView textView;

    public EmptyListPlaceholder(Context context) {
        super(context);

        init(context, null, 0);
    }

    public EmptyListPlaceholder(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0);
    }

    public EmptyListPlaceholder(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        View.inflate(context, R.layout.empty_list_placeholder, this);

        try (var a = context.obtainStyledAttributes(attrs, R.styleable.EmptyListPlaceholder, defStyleAttr, 0)) {
            textView = findViewById(R.id.text);
            ImageView icon = findViewById(R.id.icon);

            var textRes = a.getResourceId(R.styleable.EmptyListPlaceholder_text, -1);
            if (textRes == -1) {
                var text = a.getString(R.styleable.EmptyListPlaceholder_text);
                textView.setText(text);
            } else {
                textView.setText(textRes);
            }

            var iconRes = a.getResourceId(R.styleable.EmptyListPlaceholder_icon, -1);
            if (iconRes != -1) {
                icon.setImageResource(iconRes);
            }
        }
    }

    public void setText(@StringRes int text) {
        textView.setText(text);
    }
}
