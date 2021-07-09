/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;

/*
 * Fixes crash on Android 8.0 if hint is inside TextInputEditText
 * See https://issuetracker.google.com/issues/62834931
 */

public class FixHintTextInputEditText extends TextInputEditText {

    public FixHintTextInputEditText(@NonNull Context context) {
        super(context);
    }

    public FixHintTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FixHintTextInputEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int getAutofillType() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            return AUTOFILL_TYPE_NONE;
        } else {
            return super.getAutofillType();
        }
    }
}
