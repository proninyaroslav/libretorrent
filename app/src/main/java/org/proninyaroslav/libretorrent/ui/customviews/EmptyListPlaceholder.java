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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.EmptyListPlaceholderBinding;
import org.proninyaroslav.libretorrent.databinding.EmptyListPlaceholderSmallBinding;

public class EmptyListPlaceholder extends FrameLayout {
    private enum IconSize {
        BIG(0),
        SMALL(1);

        public final int value;

        IconSize(int value) {
            this.value = value;
        }

        public static IconSize fromValue(int id) {
            var enumValues = IconSize.class.getEnumConstants();
            if (enumValues == null) {
                throw new IllegalArgumentException("Unknown value: " + id);
            }
            for (var ev : enumValues) {
                if (ev.value == id) {
                    return ev;
                }
            }
            throw new IllegalArgumentException("Unknown value: " + id);
        }
    }

    private TextView textView;
    private ImageView icon;

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
        try (var a = context.obtainStyledAttributes(attrs, R.styleable.EmptyListPlaceholder, defStyleAttr, 0)) {
            var iconSize = IconSize.fromValue(
                    a.getInt(R.styleable.EmptyListPlaceholder_placeholderIconSize, IconSize.BIG.value)
            );
            switch (iconSize) {
                case BIG -> {
                    var binding = EmptyListPlaceholderBinding.inflate(LayoutInflater.from(context));
                    textView = binding.text;
                    icon = binding.icon;
                    addView(binding.getRoot());
                }
                case SMALL -> {
                    var binding = EmptyListPlaceholderSmallBinding.inflate(LayoutInflater.from(context));
                    textView = binding.text;
                    icon = binding.icon;
                    addView(binding.getRoot());
                }
            }

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
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setImageDrawable(null);
                icon.setVisibility(View.GONE);
            }
        }
    }

    public void setText(@StringRes int text) {
        textView.setText(text);
    }

    public void setIconResource(@DrawableRes int iconRes) {
        icon.setImageResource(iconRes);
    }
}
