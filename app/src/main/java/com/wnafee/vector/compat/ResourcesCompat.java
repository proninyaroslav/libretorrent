package com.wnafee.vector.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Copyright (C) 2015 Wael Nafee
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
public class ResourcesCompat {

    public static final boolean LOLLIPOP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Drawable getDrawable(Context c, int resId) {
        Drawable d;
        try {
            if (LOLLIPOP) {
                d = c.getResources().getDrawable(resId, c.getTheme());
            } else {
                d = c.getResources().getDrawable(resId);
            }
        } catch (Resources.NotFoundException e) {

            try {
                d = VectorDrawable.getDrawable(c, resId);
            } catch (IllegalArgumentException e1) {

                //We're not a VectorDrawable, try AnimatedVectorDrawable
                try {
                    d = AnimatedVectorDrawable.getDrawable(c, resId);
                } catch (IllegalArgumentException e2) {
                    //Throw NotFoundException
                    throw e;
                }
            }
        }
        return d;
    }
}
