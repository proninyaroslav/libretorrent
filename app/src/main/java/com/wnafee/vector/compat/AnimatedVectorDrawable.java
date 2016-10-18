package com.wnafee.vector.compat;

/*
 * Copyright (C) 2015 Wael Nafee
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import org.proninyaroslav.libretorrent.R;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import java.io.IOException;
import java.util.ArrayList;

public class AnimatedVectorDrawable extends DrawableCompat implements Animatable, Tintable {
    private static final String LOGTAG = AnimatedVectorDrawable.class.getSimpleName();

    private static final String ANIMATED_VECTOR = "animated-vector";
    private static final String TARGET = "target";

    private static final boolean DBG_ANIMATION_VECTOR_DRAWABLE = false;

    private AnimatedVectorDrawableState mAnimatedVectorState;

    private boolean mMutated;

    public AnimatedVectorDrawable() {
        mAnimatedVectorState = new AnimatedVectorDrawableState(null);
    }

    private AnimatedVectorDrawable(AnimatedVectorDrawableState state, Resources res,
                                   Theme theme) {
        mAnimatedVectorState = new AnimatedVectorDrawableState(state);
        if (theme != null && canApplyTheme()) {
            applyTheme(theme);
        }
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mAnimatedVectorState.mVectorDrawable.mutate();
            mMutated = true;
        }
        return this;
    }

    @Override
    public Drawable.ConstantState getConstantState() {
        mAnimatedVectorState.mChangingConfigurations = getChangingConfigurations();
        return mAnimatedVectorState;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mAnimatedVectorState.mChangingConfigurations;
    }

    @Override
    public void draw(Canvas canvas) {
        mAnimatedVectorState.mVectorDrawable.draw(canvas);
        if (isStarted()) {
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mAnimatedVectorState.mVectorDrawable.setBounds(bounds);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return mAnimatedVectorState.mVectorDrawable.setState(state);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mAnimatedVectorState.mVectorDrawable.setLevel(level);
    }

    @Override
    public int getAlpha() {
        return mAnimatedVectorState.mVectorDrawable.getAlpha();
    }

    @Override
    public void setAlpha(int alpha) {
        mAnimatedVectorState.mVectorDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mAnimatedVectorState.mVectorDrawable.setColorFilter(colorFilter);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mAnimatedVectorState.mVectorDrawable.setTintList(tint);
    }

    @Override
    public void setHotspot(float x, float y) {
        mAnimatedVectorState.mVectorDrawable.setHotspot(x, y);
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        mAnimatedVectorState.mVectorDrawable.setHotspotBounds(left, top, right, bottom);
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mAnimatedVectorState.mVectorDrawable.setTintMode(tintMode);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mAnimatedVectorState.mVectorDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    public boolean setSupportLayoutDirection(int layoutDirection) {
        return mAnimatedVectorState.mVectorDrawable.setSupportLayoutDirection(layoutDirection);
    }

    @Override
    public boolean isStateful() {
        return mAnimatedVectorState.mVectorDrawable.isStateful();
    }

    @Override
    public int getOpacity() {
        return mAnimatedVectorState.mVectorDrawable.getOpacity();
    }

    @Override
    public int getIntrinsicWidth() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicHeight();
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        mAnimatedVectorState.mVectorDrawable.getOutline(outline);
    }

    public static AnimatedVectorDrawable getDrawable(Context c, int resId) {
        return create(c, c.getResources(), resId);
    }

    public static AnimatedVectorDrawable create(Context c, Resources resources, int rid) {
        try {
            final XmlPullParser parser = resources.getXml(rid);
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }
            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }else if (!ANIMATED_VECTOR.equals(parser.getName())) {
                throw new IllegalArgumentException("root node must start with: " + ANIMATED_VECTOR);
            }

            final AnimatedVectorDrawable drawable = new AnimatedVectorDrawable();
            drawable.inflate(c, resources, parser, attrs, null);

            return drawable;
        } catch (XmlPullParserException e) {
            Log.e(LOGTAG, "parser error", e);
        } catch (IOException e) {
            Log.e(LOGTAG, "parser error", e);
        }
        return null;
    }

    public void inflate(Context c, Resources res, XmlPullParser parser, AttributeSet attrs, Theme theme)
        throws XmlPullParserException, IOException {

        int eventType = parser.getEventType();
        float pathErrorScale = 1;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                if (ANIMATED_VECTOR.equals(tagName)) {
                    final TypedArray a = obtainAttributes(res, theme, attrs,
                        R.styleable.AnimatedVectorDrawable);
                    int drawableRes = a.getResourceId(
                        R.styleable.AnimatedVectorDrawable_android_drawable, 0);
                    if (drawableRes != 0) {
                        VectorDrawable vectorDrawable = (VectorDrawable) VectorDrawable.create(res, drawableRes).mutate();
                        vectorDrawable.setAllowCaching(false);
                        pathErrorScale = vectorDrawable.getPixelSize();
                        mAnimatedVectorState.mVectorDrawable = vectorDrawable;
                    }
                    a.recycle();
                } else if (TARGET.equals(tagName)) {
                    final TypedArray a = obtainAttributes(res, theme, attrs,
                        R.styleable.AnimatedVectorDrawableTarget);
                    final String target = a.getString(
                        R.styleable.AnimatedVectorDrawableTarget_android_name);

                    int id = a.getResourceId(
                        R.styleable.AnimatedVectorDrawableTarget_android_animation, 0);
                    if (id != 0) {
                        //path animators require separate handling
                        Animator objectAnimator;
                        if (isPath(target)) {
                            objectAnimator = getPathAnimator(c, res, theme, id, pathErrorScale);
                        } else {
                            objectAnimator = AnimatorInflater.loadAnimator(c, id);
                        }
                        setupAnimatorsForTarget(target, objectAnimator);
                    }
                    a.recycle();
                }
            }

            eventType = parser.next();
        }
    }

    public boolean isPath(String target) {
        Object o = mAnimatedVectorState.mVectorDrawable.getTargetByName(target);
        return (o instanceof VectorDrawable.VFullPath);
    }

    Animator getPathAnimator(Context c, Resources res, Theme theme, int id, float pathErrorScale) {
        return PathAnimatorInflater.loadAnimator(c, res, theme, id, pathErrorScale);
    }

    @Override
    public boolean canApplyTheme() {
        return super.canApplyTheme() || mAnimatedVectorState != null
            && mAnimatedVectorState.mVectorDrawable != null
            && mAnimatedVectorState.mVectorDrawable.canApplyTheme();
    }

    @Override
    public void applyTheme(Theme t) {
        super.applyTheme(t);

        final VectorDrawable vectorDrawable = mAnimatedVectorState.mVectorDrawable;
        if (vectorDrawable != null && vectorDrawable.canApplyTheme()) {
            vectorDrawable.applyTheme(t);
        }
    }

    private static class AnimatedVectorDrawableState extends Drawable.ConstantState {
        int mChangingConfigurations;
        VectorDrawable mVectorDrawable;
        ArrayList<Animator> mAnimators;
        ArrayMap<Animator, String> mTargetNameMap;

        public AnimatedVectorDrawableState(AnimatedVectorDrawableState copy) {
            if (copy != null) {
                mChangingConfigurations = copy.mChangingConfigurations;
                if (copy.mVectorDrawable != null) {
                    mVectorDrawable = (VectorDrawable) copy.mVectorDrawable.getConstantState().newDrawable();
                    mVectorDrawable.mutate();
                    mVectorDrawable.setAllowCaching(false);
                    mVectorDrawable.setBounds(copy.mVectorDrawable.getBounds());
                }
                if (copy.mAnimators != null) {
                    final int numAnimators = copy.mAnimators.size();
                    mAnimators = new ArrayList<Animator>(numAnimators);
                    mTargetNameMap = new ArrayMap<Animator, String>(numAnimators);
                    for (int i = 0; i < numAnimators; ++i) {
                        Animator anim = copy.mAnimators.get(i);
                        Animator animClone = anim.clone();
                        String targetName = copy.mTargetNameMap.get(anim);
                        Object targetObject = mVectorDrawable.getTargetByName(targetName);
                        animClone.setTarget(targetObject);
                        mAnimators.add(animClone);
                        mTargetNameMap.put(animClone, targetName);
                    }
                }
            } else {
                mVectorDrawable = new VectorDrawable();
            }
        }

        @Override
        public Drawable newDrawable() {
            return new AnimatedVectorDrawable(this, null, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new AnimatedVectorDrawable(this, res, null);
        }

        @Override
        public Drawable newDrawable(Resources res, Theme theme) {
            return new AnimatedVectorDrawable(this, res, theme);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }

    private void setupAnimatorsForTarget(String name, Animator animator) {
        Object target = mAnimatedVectorState.mVectorDrawable.getTargetByName(name);
        animator.setTarget(target);
        if (mAnimatedVectorState.mAnimators == null) {
            mAnimatedVectorState.mAnimators = new ArrayList<Animator>();
            mAnimatedVectorState.mTargetNameMap = new ArrayMap<Animator, String>();
        }
        mAnimatedVectorState.mAnimators.add(animator);
        mAnimatedVectorState.mTargetNameMap.put(animator, name);
        if (DBG_ANIMATION_VECTOR_DRAWABLE) {
            Log.v(LOGTAG, "add animator  for target " + name + " " + animator);
        }
    }

    @Override
    public boolean isRunning() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            if (animator.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private boolean isStarted() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            if (animator.isStarted()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            if (!animator.isStarted()) {
                animator.start();
            }
        }
        invalidateSelf();
    }

    @Override
    public void stop() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            animator.end();
        }
    }

    /**
     * Reverses ongoing animations or starts pending animations in reverse.
     * <p>
     * NOTE: Only works of all animations are ValueAnimators.
     */
    public void reverse() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            if (canReverse(animator)) {
                reverse(animator);
            } else {
                Log.w(LOGTAG, "AnimatedVectorDrawable can't reverse()");
            }
        }
    }

    public boolean canReverse() {
        final ArrayList<Animator> animators = mAnimatedVectorState.mAnimators;
        final int size = animators.size();
        for (int i = 0; i < size; i++) {
            final Animator animator = animators.get(i);
            if (!canReverse(animator)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canReverse(Animator a) {
        if (a instanceof AnimatorSet) {
            final ArrayList<Animator> animators = ((AnimatorSet)a).getChildAnimations();
            for(Animator anim: animators) {
                if(!canReverse(anim)) {
                    return false;
                }
            }
        } else if (a instanceof ValueAnimator) {
            return true;
        }

        return false;
    }

    private void reverse(Animator a) {
        if (a instanceof AnimatorSet) {
            final ArrayList<Animator> animators = ((AnimatorSet)a).getChildAnimations();
            for(Animator anim: animators) {
                reverse(anim);
            }
        } else if (a instanceof ValueAnimator) {
            ((ValueAnimator)a).reverse();
        }
    }

}
