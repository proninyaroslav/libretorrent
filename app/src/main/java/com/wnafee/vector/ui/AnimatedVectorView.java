package com.wnafee.vector.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.ImageView;

import com.wnafee.vector.compat.DrawableCompat;
import com.wnafee.vector.compat.ResourcesCompat;
import com.wnafee.vector.compat.Tintable;

import org.proninyaroslav.libretorrent.R;

import java.lang.ref.WeakReference;

public class AnimatedVectorView extends ImageView {

    public enum MorphState {
        START,
        END
    }

    public interface OnStateChangedListener {
        void onStateChanged(MorphState changedTo, boolean isAnimating);
    }

    private static class TintInfo {
        ColorStateList mTintList;
        PorterDuff.Mode mTintMode;
        boolean mHasTintMode;
        boolean mHasTintList;
    }

    private TintInfo mBackgroundTint;
    private TintInfo mForegroundTint;

    private MorphState mState = MorphState.START;

    private Drawable mStartDrawable = null;
    private Drawable mEndDrawable = null;
    private Drawable mCurrentDrawable;

    private int mStartDrawableWidth;
    private int mStartDrawableHeight;

    private int mEndDrawableWidth;
    private int mEndDrawableHeight;

    private int mCurrentDrawableWidth;
    private int mCurrentDrawableHeight;

    private boolean mStartCanMorph = false;
    private boolean mEndCanMorph = false;

    private boolean mHasStarted = false;

    private boolean mCropToPadding = false;

    private boolean mAdjustViewBounds = false;
    private boolean mAdjustViewBoundsCompat = Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1;

    private boolean mHaveFrame = false;

    private Matrix mMatrix;
    private Matrix mDrawMatrix = null;

    private ScaleType mScaleType;

    private RectF mTempSrc = new RectF();
    private RectF mTempDst = new RectF();

    private WeakReference<OnStateChangedListener> mStateListener;

    public AnimatedVectorView(Context context) {
        this(context, null);
    }

    public AnimatedVectorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AnimatedVectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initImageView();

        final Resources.Theme theme = context.getTheme();
        TypedArray a =
            theme.obtainStyledAttributes(
                attrs,
                R.styleable.MorphButton,
                defStyleAttr,
                0);

        int startResId =
            a.getResourceId(R.styleable.MorphButton_vc_startDrawable, -1);
        int endResId =
            a.getResourceId(R.styleable.MorphButton_vc_endDrawable, -1);
        boolean autoStart =
            a.getBoolean(R.styleable.MorphButton_vc_autoStartAnimation, false);

        final int st = a.getInt(R.styleable.MorphButton_android_scaleType, -1);
        if (st >= 0) {
            setScaleType(getScaleTypeFromInt(st));
        }

        readTintAttributes(a);
        a.recycle();

        applyBackgroundTint();
        setClickable(true);

        setStartDrawable(startResId, false);
        setEndDrawable(endResId, false);

        setState(mState);
        if (autoStart) {
            mHasStarted = true;
            setState(MorphState.END, true);
        }
    }

    private void initImageView() {
        mMatrix = new Matrix();
        mScaleType = ScaleType.FIT_XY;
    }

    private boolean isMorphable(Drawable d) {
        return d != null && d instanceof Animatable;
    }

    @SuppressWarnings("unused")
    public void setOnStateChangedListener(OnStateChangedListener l) {
        if (l != null && (mStateListener == null || l != mStateListener.get())) {
            mStateListener = new WeakReference<>(l);
        }
    }

    @SuppressWarnings("unused")
    public void animateVector() {
        mHasStarted = true;
        setState(mState == MorphState.START ? MorphState.END : MorphState.START, true);
    }

    private void updateDrawable(Drawable d, MorphState state) {
        Drawable oldD = state == MorphState.START ? mStartDrawable : mEndDrawable;

        if (oldD != null) {
            oldD.setCallback(null);
            unscheduleDrawable(oldD);
        }

        if (state == MorphState.START) {
            mStartDrawable = d;
            mStartCanMorph = isMorphable(d);
        } else {
            mEndDrawable = d;
            mEndCanMorph = isMorphable(d);
        }

        if (d != null) {
            d.setCallback(this);
            // Adjust layout direction
            // d.setLayoutDirection(getLayoutDirection());
            if (d.isStateful()) {
                d.setState(getDrawableState());
            }
            d.setVisible(getVisibility() == VISIBLE, true);
            d.setLevel(0); // Not supporting layerlist drawables for now

            // Setting width and height;
            int width;
            int height;
            if (state == MorphState.START) {
                width = mStartDrawableWidth = d.getIntrinsicWidth();
                height = mStartDrawableHeight = d.getIntrinsicHeight();
            } else {
                width = mEndDrawableWidth = d.getIntrinsicWidth();
                height = mEndDrawableHeight = d.getIntrinsicHeight();
            }

            applyForegroundTint(d);
            configureBounds(d, width, height);
        } else {
            if (state == MorphState.START) {
                mStartDrawableWidth = mStartDrawableHeight = -1;
            } else {
                mEndDrawableWidth = mEndDrawableHeight = -1;
            }
        }
    }

    @Override
    public void refreshDrawableState() {
        super.refreshDrawableState();
        refreshCurrentDrawable();
    }

    private void refreshCurrentDrawable() {
        if (mCurrentDrawable != null) {
            mCurrentDrawable.setState(getDrawableState());
        }
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mCurrentDrawable != null) {
            mCurrentDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        resizeFromDrawable(mState);
    }

    private void resizeFromDrawable(MorphState state) {
        int width = state == MorphState.START ? mStartDrawableWidth : mEndDrawableWidth;
        int height = state == MorphState.START ? mStartDrawableHeight : mEndDrawableHeight;
        Drawable d = state == MorphState.START ? mStartDrawable : mEndDrawable;
        if (d != null) {
            int w = d.getIntrinsicWidth();
            if (w < 0) w = width;
            int h = d.getIntrinsicHeight();
            if (h < 0) h = height;
            if (w != width || h != height) {
                if (state == MorphState.START) {
                    mStartDrawableWidth = w;
                    mStartDrawableHeight = h;
                } else {
                    mEndDrawableWidth = w;
                    mEndDrawableHeight = h;
                }
                requestLayout();
            }
        }
    }

    @SuppressWarnings("unused")
    public void setStartDrawable(int rId) {
        setStartDrawable(rId, true);
    }

    private void setStartDrawable(int rId, boolean refreshState) {
        if (rId > 0) {
            setStartDrawable(ResourcesCompat.getDrawable(getContext(), rId), refreshState);
        }
    }

    @SuppressWarnings("unused")
    public void setStartDrawable(Drawable d) {
        setStartDrawable(d, true);
    }

    private void setStartDrawable(Drawable d, boolean refreshState) {
        if (mStartDrawable == d)
            return;

        updateDrawable(d, MorphState.START);

        if (refreshState)
            setState(mState);
    }

    @SuppressWarnings("unused")
    public void setEndDrawable(int rId) {
        setEndDrawable(rId, true);
    }

    private void setEndDrawable(int rId, boolean refreshState) {
        if (rId > 0) {
            setEndDrawable(ResourcesCompat.getDrawable(getContext(), rId), refreshState);
        }
    }

    @SuppressWarnings("unused")
    public void setEndDrawable(Drawable d) {
        setEndDrawable(d, true);
    }

    private void setEndDrawable(Drawable d, boolean refreshState) {
        if (mEndDrawable == d)
            return;

        updateDrawable(d, MorphState.END);

        if (refreshState)
            setState(mState);
    }

    public MorphState getState() {
        return mState;
    }

    private void setCurrentDrawable(Drawable d, int width, int height) {
        if (mCurrentDrawable != d) {
            mCurrentDrawable = d;

            // Check that drawable has had its bounds set
            Rect r = d.getBounds();
            int boundsWidth = r.right - r.left;
            int boundsHeight = r.bottom - r.top;
            if (mCurrentDrawableWidth != width || mCurrentDrawableHeight != height
                || boundsWidth != width || boundsHeight != height) {
                requestLayout();
            }

            mCurrentDrawableWidth = width;
            mCurrentDrawableHeight = height;
        }

    }

    /**
     * Same as {@link MorphButton#setState(MorphButton.MorphState, boolean)} with no animation
     *
     * @param state requested state
     */
    public void setState(MorphState state) {
        setState(state, false);
    }

    /**
     * Choose button state
     *
     * @param state   a {@link MorphButton.MorphState} to set button to
     * @param animate should we animated to get to this state or not
     */
    public void setState(MorphState state, boolean animate) {
        if (state == MorphState.START) {
            if (mCurrentDrawable != mStartDrawable) {
                setCurrentDrawable(mStartDrawable, mStartDrawableWidth, mStartDrawableHeight);
            }

            if (mStartCanMorph) {
                beginStartAnimation();
                if (!animate) {
                    endStartAnimation();
                }
            }
        } else {
            if (mCurrentDrawable != mEndDrawable) {
                setCurrentDrawable(mEndDrawable, mEndDrawableWidth, mEndDrawableHeight);
            }

            if (mEndCanMorph) {
                beginEndAnimation();
                if (!animate) {
                    endEndAnimation();
                }
            }
        }

        // Only allow state listeners to change if actually changing state
        if (mState == state && mHasStarted) {
            return;
        }

        mState = state;
        if (mStateListener != null) {
            OnStateChangedListener onStateChangedListener = mStateListener.get();
            if (onStateChangedListener != null) {
                onStateChangedListener.onStateChanged(state, animate);
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (mCurrentDrawable == null) {
            return; //not set yet
        }

        if (mCurrentDrawableWidth == 0 || mCurrentDrawableHeight == 0) {
            return; // nothing to draw (empty bounds)
        }

        final int paddingTop = getPaddingTop();
        final int paddingLeft = getPaddingLeft();
        final int paddingBottom = getPaddingBottom();
        final int paddingRight = getPaddingRight();
        final int top = getTop();
        final int bottom = getBottom();
        final int left = getLeft();
        final int right = getRight();
        if (mDrawMatrix == null && paddingTop == 0 && paddingLeft == 0) {
            mCurrentDrawable.draw(canvas);
        } else {
            int saveCount = canvas.getSaveCount();
            canvas.save();

            if (mCropToPadding) {
                final int scrollX = getScrollX();
                final int scrollY = getScrollY();
                canvas.clipRect(scrollX + paddingLeft, scrollY + paddingTop,
                    scrollX + right - left - paddingRight,
                    scrollY + bottom - top - paddingBottom);
            }

            canvas.translate(paddingLeft, paddingTop);

            if (mDrawMatrix != null) {
                canvas.concat(mDrawMatrix);
            }
            mCurrentDrawable.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setBackground(Drawable background) {
        if (ResourcesCompat.LOLLIPOP) {
            if (mBackgroundTint != null) {
                // Set tint parameters for superclass View to apply
                if (mBackgroundTint.mHasTintList)
                    super.setBackgroundTintList(mBackgroundTint.mTintList);
                if (mBackgroundTint.mHasTintMode)
                    super.setBackgroundTintMode(mBackgroundTint.mTintMode);
            }
            super.setBackground(background);
        } else {
            super.setBackground(background);

            // Need to apply tint ourselves
            applyBackgroundTint();
        }
    }

    public ColorStateList getBackgroundTintList() {
        if (ResourcesCompat.LOLLIPOP) {
            return getBackgroundTintList();
        }
        return mBackgroundTint != null ? mBackgroundTint.mTintList : null;
    }

    public ColorStateList getForegroundTintList() {
        return mForegroundTint != null ? mForegroundTint.mTintList : null;
    }

    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        if (ResourcesCompat.LOLLIPOP) {
            super.setBackgroundTintList(tint);
        }

        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintList = tint;
        mBackgroundTint.mHasTintList = true;

        if (!ResourcesCompat.LOLLIPOP) {
            applyBackgroundTint();
        }
    }

    public void setForegroundTintList(@Nullable ColorStateList tint) {
        if (mForegroundTint == null) {
            mForegroundTint = new TintInfo();
        }

        mForegroundTint.mTintList = tint;
        mForegroundTint.mHasTintList = true;

        // Apply to all current foreground drawables
        applyForegroundTint();
    }

    public PorterDuff.Mode getForegroundTintMode() {
        return mForegroundTint != null ? mForegroundTint.mTintMode : null;
    }

    public PorterDuff.Mode getBackgroundTintMode() {
        if (ResourcesCompat.LOLLIPOP) {
            return getBackgroundTintMode();
        }
        return mBackgroundTint != null ? mBackgroundTint.mTintMode : null;
    }

    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (ResourcesCompat.LOLLIPOP) {
            super.setBackgroundTintMode(tintMode);
        }
        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintMode = tintMode;
        mBackgroundTint.mHasTintMode = true;

        if (!ResourcesCompat.LOLLIPOP) {
            applyBackgroundTint();
        }
    }

    public void setForegroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (mForegroundTint == null) {
            mForegroundTint = new TintInfo();
        }
        mForegroundTint.mTintMode = tintMode;
        mForegroundTint.mHasTintMode = true;

        // Apply to all current foreground drawables
        applyForegroundTint();
    }

    private void setDrawableColorFilter(Drawable d, int color, PorterDuff.Mode mode) {
        if (d != null) {
            d.setColorFilter(color, mode);
        }
    }

    @SuppressWarnings("unused")
    public void setForegroundColorFilter(int color, PorterDuff.Mode mode) {
        if (mStartDrawable != null) {
            mStartDrawable.setColorFilter(color, mode);
        }

        if (mEndDrawable != null) {
            mEndDrawable.setColorFilter(color, mode);
        }
    }

    @SuppressWarnings("unused")
    public void setBackgroundColorFilter(int color, PorterDuff.Mode mode) {
        setDrawableColorFilter(getBackground(), color, mode);
    }

    /**
     * Apply tint to the drawable
     *
     * @param d drawable
     */
    private void applyForegroundTint(Drawable d) {
        applyTint(d, mForegroundTint);
    }

    /**
     * Apply tint to all foreground drawables
     */
    private void applyForegroundTint() {
        applyTint(mStartDrawable, mForegroundTint);
        applyTint(mEndDrawable, mForegroundTint);
    }

    /**
     * Apply tint to our background drawable
     */
    private void applyBackgroundTint() {
        applyTint(getBackground(), mBackgroundTint);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void applyTint(Drawable d, TintInfo t) {
        if (d != null && t != null) {
            if (ResourcesCompat.LOLLIPOP) {
                if (t.mHasTintList || t.mHasTintMode) {
                    d = d.mutate();
                    if (t.mHasTintList) {
                        d.setTintList(t.mTintList);
                    }
                    if (t.mHasTintMode) {
                        d.setTintMode(t.mTintMode);
                    }
                }
            } else if (d instanceof Tintable) {
                // Our VectorDrawable and AnimatedVectorDrawable implementation
                if (t.mHasTintList || t.mHasTintMode) {
                    d = d.mutate();
                    Tintable tintable = (Tintable) d;
                    if (t.mHasTintList) {
                        tintable.setTintList(t.mTintList);
                    }
                    if (t.mHasTintMode) {
                        tintable.setTintMode(t.mTintMode);
                    }
                }
            } else {
                if (t.mHasTintList) {
                    int color = t.mTintList.getColorForState(getDrawableState(), Color.TRANSPARENT);
                    setDrawableColorFilter(d, color, PorterDuff.Mode.SRC_IN);
                }
            }
        }
    }

    private void readTintAttributes(TypedArray a) {
        mBackgroundTint = new TintInfo();
        mForegroundTint = new TintInfo();

        mBackgroundTint.mTintList =
            a.getColorStateList(R.styleable.MorphButton_vc_backgroundTint);
        mBackgroundTint.mHasTintList = mBackgroundTint.mTintList != null;

        mBackgroundTint.mTintMode = DrawableCompat.parseTintMode(a.getInt(
            R.styleable.MorphButton_vc_backgroundTintMode, -1), null);
        mBackgroundTint.mHasTintMode = mBackgroundTint.mTintMode != null;

        mForegroundTint.mTintList =
            a.getColorStateList(R.styleable.MorphButton_vc_foregroundTint);
        mForegroundTint.mHasTintList = mForegroundTint.mTintList != null;

        mForegroundTint.mTintMode = DrawableCompat.parseTintMode(a.getInt(
            R.styleable.MorphButton_vc_foregroundTintMode, -1), null);
        mForegroundTint.mHasTintMode = mForegroundTint.mTintMode != null;
    }

    static class SavedState extends BaseSavedState {
        MorphState state;

        /**
         * Constructor called from {@link CompoundButton#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            state = (MorphState) in.readValue(null);
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(state);
        }

        @Override
        public String toString() {
            return "MorphButton.SavedState{"
                + Integer.toHexString(System.identityHashCode(this))
                + " state=" + state + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
            = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @NonNull
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.state = getState();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setState(ss.state, false);
        requestLayout();
    }

    private boolean beginStartAnimation() {
        if (mStartDrawable != null && mStartCanMorph) {
            if (!isInEditMode()) {
                ((Animatable) mStartDrawable).start();
            }
            return true;
        }
        return false;
    }

    private boolean endStartAnimation() {
        if (mStartDrawable != null && mStartCanMorph) {
            if (!isInEditMode()) {
                ((Animatable) mStartDrawable).stop();
            }
            return true;
        }
        return false;
    }

    private boolean beginEndAnimation() {
        if (mEndDrawable != null && mEndCanMorph) {
            if (!isInEditMode()) {
                ((Animatable) mEndDrawable).start();
            }
            return true;
        }
        return false;
    }

    private boolean endEndAnimation() {
        if (mEndDrawable != null && mEndCanMorph) {
            if (!isInEditMode()) {
                ((Animatable) mEndDrawable).stop();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w;
        int h;

        // Desired aspect ratio of the view's contents (not including padding)
        float desiredAspect = 0.0f;

        // We are allowed to change the view's width
        boolean resizeWidth = false;

        // We are allowed to change the view's height
        boolean resizeHeight = false;

        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);

        if (mCurrentDrawable == null) {
            // If no drawable, its intrinsic size is 0.
            mCurrentDrawableWidth = -1;
            mCurrentDrawableHeight = -1;
            w = h = 0;
        } else {
            w = mCurrentDrawableWidth;
            h = mCurrentDrawableHeight;
            if (w <= 0) w = 1;
            if (h <= 0) h = 1;

            // We are supposed to adjust view bounds to match the aspect
            // ratio of our drawable. See if that is possible.
            if (mAdjustViewBounds) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;

                desiredAspect = (float) w / (float) h;
            }
        }

        int pleft = getPaddingLeft();
        int pright = getPaddingRight();
        int ptop = getPaddingTop();
        int pbottom = getPaddingBottom();

        int widthSize;
        int heightSize;

        if (resizeWidth || resizeHeight) {
            /* If we get here, it means we want to resize to match the
                drawables aspect ratio, and we have the freedom to change at
                least one dimension.
            */

            // Get the max possible width given our constraints
            widthSize =
                resolveAdjustedSize(w + pleft + pright, Integer.MAX_VALUE, widthMeasureSpec);

            // Get the max possible height given our constraints
            heightSize =
                resolveAdjustedSize(h + ptop + pbottom, Integer.MAX_VALUE, heightMeasureSpec);

            if (desiredAspect != 0.0f) {
                // See what our actual aspect ratio is
                float actualAspect = (float) (widthSize - pleft - pright) /
                    (heightSize - ptop - pbottom);

                if (Math.abs(actualAspect - desiredAspect) > 0.0000001) {

                    boolean done = false;

                    // Try adjusting width to be proportional to height
                    if (resizeWidth) {
                        int newWidth = (int) (desiredAspect * (heightSize - ptop - pbottom)) +
                            pleft + pright;

                        // Allow the width to outgrow its original estimate if height is fixed.
                        if (!resizeHeight && !mAdjustViewBoundsCompat) {
                            widthSize =
                                resolveAdjustedSize(newWidth, Integer.MAX_VALUE, widthMeasureSpec);
                        }

                        if (newWidth <= widthSize) {
                            widthSize = newWidth;
                            done = true;
                        }
                    }

                    // Try adjusting height to be proportional to width
                    if (!done && resizeHeight) {
                        int newHeight = (int) ((widthSize - pleft - pright) / desiredAspect) +
                            ptop + pbottom;

                        // Allow the height to outgrow its original estimate if width is fixed.
                        if (!resizeWidth && !mAdjustViewBoundsCompat) {
                            heightSize = resolveAdjustedSize(newHeight, Integer.MAX_VALUE,
                                heightMeasureSpec);
                        }

                        if (newHeight <= heightSize) {
                            heightSize = newHeight;
                        }
                    }
                }
            }
        } else {
            /* We are either don't want to preserve the drawables aspect ratio,
               or we are not allowed to change view dimensions. Just measure in
               the normal way.
            */
            w += pleft + pright;
            h += ptop + pbottom;

            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());

            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    private int resolveAdjustedSize(int desiredSize, int maxSize,
                                    int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                /* Parent says we can be as big as we want. Just don't be larger
                   than max size imposed on ourselves.
                */
                result = Math.min(desiredSize, maxSize);
                break;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be larger than
                // the max size imposed on ourselves.
                result = Math.min(Math.min(desiredSize, specSize), maxSize);
                break;
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        mHaveFrame = true;
        configureBounds();
        return changed;
    }

    private void configureBounds() {
        configureBounds(mCurrentDrawable, mCurrentDrawableWidth, mCurrentDrawableHeight);

    }

    private void configureBounds(Drawable d, int dwidth, int dheight) {
        if (d == null || !mHaveFrame) {
            return;
        }

        int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int vheight = getHeight() - getPaddingTop() - getPaddingBottom();

        boolean fits = (dwidth < 0 || vwidth == dwidth) &&
            (dheight < 0 || vheight == dheight);

        if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == mScaleType) {
            /* If the drawable has no intrinsic size, or we're told to
                scaletofit, then we just fill our entire view.
            */
            d.setBounds(0, 0, vwidth, vheight);
            mDrawMatrix = null;
        } else {
            // We need to do the scaling ourself, so have the drawable
            // use its native size.
            d.setBounds(0, 0, dwidth, dheight);

            if (ScaleType.MATRIX == mScaleType) {
                // Use the specified matrix as-is.
                if (mMatrix.isIdentity()) {
                    mDrawMatrix = null;
                } else {
                    mDrawMatrix = mMatrix;
                }
            } else if (fits) {
                // The bitmap fits exactly, no transform needed.
                mDrawMatrix = null;
            } else if (ScaleType.CENTER == mScaleType) {
                // Center bitmap in view, no scaling.
                mDrawMatrix = mMatrix;
                mDrawMatrix.setTranslate((int) ((vwidth - dwidth) * 0.5f + 0.5f),
                    (int) ((vheight - dheight) * 0.5f + 0.5f));
            } else if (ScaleType.CENTER_CROP == mScaleType) {
                mDrawMatrix = mMatrix;

                float scale;
                float dx = 0, dy = 0;

                if (dwidth * vheight > vwidth * dheight) {
                    scale = (float) vheight / (float) dheight;
                    dx = (vwidth - dwidth * scale) * 0.5f;
                } else {
                    scale = (float) vwidth / (float) dwidth;
                    dy = (vheight - dheight * scale) * 0.5f;
                }

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
            } else if (ScaleType.CENTER_INSIDE == mScaleType) {
                mDrawMatrix = mMatrix;
                float scale;
                float dx;
                float dy;

                if (dwidth <= vwidth && dheight <= vheight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) vwidth / (float) dwidth,
                        (float) vheight / (float) dheight);
                }

                dx = (int) ((vwidth - dwidth * scale) * 0.5f + 0.5f);
                dy = (int) ((vheight - dheight * scale) * 0.5f + 0.5f);

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
            } else {
                // Generate the required transform.
                mTempSrc.set(0, 0, dwidth, dheight);
                mTempDst.set(0, 0, vwidth, vheight);

                mDrawMatrix = mMatrix;
                mDrawMatrix.setRectToRect(mTempSrc, mTempDst, scaleTypeToScaleToFit(mScaleType));
            }
        }
    }

    private static final Matrix.ScaleToFit[] sS2FArray = {
        Matrix.ScaleToFit.FILL,
        Matrix.ScaleToFit.START,
        Matrix.ScaleToFit.CENTER,
        Matrix.ScaleToFit.END
    };

    private static Matrix.ScaleToFit scaleTypeToScaleToFit(ScaleType st) {
        // ScaleToFit enum to their corresponding Matrix.ScaleToFit values
        return sS2FArray[st.nativeInt - 1];
    }

    public enum ScaleType {
        MATRIX(0),
        FIT_XY(1),
        FIT_START(2),
        FIT_CENTER(3),
        FIT_END(4),
        CENTER(5),
        CENTER_CROP(6),
        CENTER_INSIDE(7);

        ScaleType(int ni) {
            nativeInt = ni;
        }

        final int nativeInt;
    }

    private ScaleType getCustomScaleType() {
        return mScaleType;
    }

    /**
     * Controls how the image should be resized or moved to match the size
     * of this ImageView.
     *
     * @param scaleType The desired scaling mode.
     */
    public void setScaleType(ScaleType scaleType) {
        if (scaleType == null) {
            throw new NullPointerException();
        }

        if (mScaleType != scaleType) {
            mScaleType = scaleType;

            setWillNotCacheDrawing(mScaleType == ScaleType.CENTER);

            requestLayout();
            invalidate();
        }
    }

    private ScaleType getScaleTypeFromInt(int i) {
        switch (i) {
            case 0:
                return ScaleType.MATRIX;
            case 1:
                return ScaleType.FIT_XY;
            case 2:
                return ScaleType.FIT_START;
            case 3:
                return ScaleType.FIT_CENTER;
            case 4:
                return ScaleType.FIT_END;
            case 5:
                return ScaleType.CENTER;
            case 6:
                return ScaleType.CENTER_CROP;
            case 7:
                return ScaleType.CENTER_INSIDE;
            default:
                return ScaleType.FIT_CENTER;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mStartDrawable || who == mEndDrawable || super.verifyDrawable(who);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable dr) {
        if (dr == mStartDrawable || dr == mEndDrawable) {
            /* we invalidate the whole view in this case because it's very
             * hard to know where the drawable actually is. This is made
             * complicated because of the offsets and transformations that
             * can be applied. In theory we could get the drawable's bounds
             * and run them through the transformation and offsets, but this
             * is probably not worth the effort.
             */
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }
}
