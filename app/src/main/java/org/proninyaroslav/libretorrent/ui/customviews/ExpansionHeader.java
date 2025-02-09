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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import org.proninyaroslav.libretorrent.R;

/*
 * Based on expansion panels described in Material Design Components
 * https://material.io/archive/guidelines/components/expansion-panels.html
 *
 * Represent only clickable header.
 */

public class ExpansionHeader extends FrameLayout {
    private static final String TAG_SUPER = "super";
    private static final String TAG_EXPANDED = "expanded";

    private static final float rotationExpanded = 180f;
    private static final float rotationCollapsed = 0;
    private static final int animationDuration = 300; /* ms */

    private boolean expanded = false;
    private MaterialCardView card;
    private Button arrowButton;
    private TextView textView;

    public ExpansionHeader(@NonNull Context context) {
        super(context);

        init(context, null);
    }

    public ExpansionHeader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    public ExpansionHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs);
    }

    public ExpansionHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context, attrs);
    }


    public void toggleExpand() {
        setExpanded(!expanded, true);
    }

    public void setExpanded(boolean expanded) {
        setExpanded(expanded, true);
    }

    public void setText(CharSequence charSequence) {
        textView.setText(charSequence);
    }

    public void setText(int resId) {
        textView.setText(resId);
    }

    public void setExpanded(boolean expanded, boolean animation) {
        this.expanded = expanded;
        if (arrowButton == null)
            return;

        if (expanded) {
            if (animation)
                createRotateAnimator(arrowButton, rotationCollapsed, rotationExpanded).start();
            else
                arrowButton.setRotation(rotationExpanded);

        } else {
            if (animation)
                createRotateAnimator(arrowButton, rotationExpanded, rotationCollapsed).start();
            else
                arrowButton.setRotation(rotationCollapsed);
        }
    }

    private ObjectAnimator createRotateAnimator(View target, float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "rotation", from, to);
        animator.setDuration(animationDuration);
        animator.setInterpolator(new LinearInterpolator());

        return animator;
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        View.inflate(context, R.layout.expansion_header, this);

        String text = null;
        boolean expanded = false;
        int textAppearanceId = -1;

        if (attrs != null) {
            try (var a = context.obtainStyledAttributes(attrs, R.styleable.ExpansionHeader)) {
                expanded = a.getBoolean(R.styleable.ExpansionHeader_expansion_expanded, false);
                text = a.getString(R.styleable.ExpansionHeader_expansion_text);
                textAppearanceId = a.getResourceId(R.styleable.ExpansionHeader_expansion_textAppearance, -1);
            }
        }

        card = findViewById(R.id._expansion_header_card);
        textView = findViewById(R.id._expansion_header_text);
        arrowButton = findViewById(R.id._expansion_header_arrow);

        textView.setText(text);
        setTextAppearance(textAppearanceId);
        setExpanded(expanded, false);
    }

    private void setTextAppearance(int resId) {
        if (resId == -1) {
            return;
        }

        textView.setTextAppearance(resId);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle outState = new Bundle();

        outState.putParcelable(TAG_SUPER, super.onSaveInstanceState());
        outState.putBoolean(TAG_EXPANDED, expanded);

        return outState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle savedInstanceState) {
            setExpanded(savedInstanceState.getBoolean(TAG_EXPANDED), false);
            super.onRestoreInstanceState(savedInstanceState.getParcelable(TAG_SUPER));
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        card.setOnClickListener(l);
        arrowButton.setOnClickListener(l);
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        card.setOnFocusChangeListener(l);
        arrowButton.setOnFocusChangeListener(l);
    }
}
