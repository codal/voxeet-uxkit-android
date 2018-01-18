package sdk.voxeet.com.toolkit.views.uitookit.sdk.overlays.abstracts;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;

import com.voxeet.toolkit.R;

import sdk.voxeet.com.toolkit.utils.CornerHelper;
import voxeet.com.sdk.utils.ScreenHelper;

/**
 * Created by romainbenmansour on 11/08/16.
 */
public abstract class AbstractVoxeetOverlayView extends AbstractVoxeetExpandableView {

    private final String TAG = AbstractVoxeetOverlayView.class.getSimpleName();

    private final int defaultWidth = getResources().getDimensionPixelSize(R.dimen.conference_view_width);

    private final int defaultHeight = getResources().getDimensionPixelSize(R.dimen.conference_view_height);

    private boolean isMaxedOut;

    private ImageView action_button;

    private AnimationHandler animationHandler;

    private ViewGroup container;

    private GestureDetector gestureDetector;

    private DisplayMetrics dm;

    private WindowManager windowManager;
    private AbstractVoxeetExpandableView mSubView;
    private ViewGroup sub_container;

    /**
     * Instantiates a new Voxeet conference view.
     *
     * @param context the context
     */
    public AbstractVoxeetOverlayView(Context context) {
        super(context);
    }

    /**
     * Instantiates a new Voxeet conference view.
     *
     * @param context the context
     * @param attrs   the attrs
     */
    public AbstractVoxeetOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int previousWidth = dm.widthPixels;
        int previousHeight = dm.heightPixels;

        windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(dm);

        if (isMaxedOut)
            animationHandler.toLandScape(250, previousWidth, dm.widthPixels, previousHeight, dm.heightPixels);
        else
            CornerHelper.sendToCorner(this, windowManager, getContext());
    }

    @Override
    public void init() {
        animationHandler = new AnimationHandler();

        dm = new DisplayMetrics();

        windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(dm);

        gestureDetector = new GestureDetector(getContext(), new SingleTapConfirm());

        setOnTouchListener(new OnTouchListener() {
            private float dX;

            private float dY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    toggleSize();
                } else if (!isMaxedOut) { // drag n drop only when minimized
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            dX = getX() - event.getRawX();
                            dY = getY() - event.getRawY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float x = event.getRawX() + dX;
                            float y = event.getRawY() + dY;
                            if (x < 0)
                                x = 0;
                            if (y < ScreenHelper.getStatusBarHeight(getContext()))
                                y = ScreenHelper.getStatusBarHeight(getContext());

                            animate().x(event.getRawX() + dX).y(event.getRawY() + dY)
                                    .setDuration(0).start();
                            break;
                        case MotionEvent.ACTION_UP:
                            CornerHelper.sendToCorner(AbstractVoxeetOverlayView.this, windowManager, getContext());
                        default:
                            return false;
                    }
                }
                return true;
            }
        });
    }

    /**
     * Toggles view's size to full screen or default size.
     */
    protected void toggleSize() {
        isMaxedOut = getWidth() > defaultWidth && getHeight() > defaultHeight;

        if (!isMaxedOut) { // maximize
            onPreExpandedView();
            expandView();
        } else { // minimize
            onPreMinizedView();
            minizeView();
        }
    }

    private void onViewToggled() {
        isMaxedOut = !isMaxedOut;

        toggleBackground();

        if(isMaxedOut) {
            onExpandedView();
        } else {
            onMinizedView();
        }
    }

    protected void expandView() {
        action_button.setVisibility(View.VISIBLE);
        ViewGroup view = (ViewGroup) getParent();
        if (view != null)
            animationHandler.expand(1000, view.getWidth(), view.getHeight());
    }

    protected void minizeView() {
        action_button.setVisibility(View.GONE);
        animationHandler.collapse(1000, defaultWidth, defaultHeight);
    }

    protected void toggleBackground() {
        if (isMaxedOut)
            container.setBackgroundResource(R.drawable.background_conference_view_maxed_out);
        else
            container.setBackgroundResource(R.drawable.background_conference_view);
    }


    @Override
    public void onPreExpandedView() {
        mSubView.onPreExpandedView();
    }

    @Override
    public void onExpandedView() {
        mSubView.onExpandedView();
    }

    @Override
    public void onPreMinizedView() {
        mSubView.onPreMinizedView();
    }

    @Override
    public void onMinizedView() {
        mSubView.onMinizedView();
    }


    @Override
    protected void bindView(View view) {
        container = view.findViewById(R.id.overlay_main_container);
        sub_container = view.findViewById(R.id.container);
        action_button = view.findViewById(R.id.action_button);


        mSubView = createSubVoxeetView();

        sub_container.addView(mSubView);

        action_button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onActionButtonClicked();
            }
        });

        //now add the subview as a listener of the current view
        addListener(mSubView);
    }

    protected abstract void onActionButtonClicked();

    @NonNull
    protected abstract AbstractVoxeetExpandableView createSubVoxeetView();

    private class SingleTapConfirm extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            return true;
        }
    }


    public class AnimationHandler {

        private final long animatonDuration = 200;

        /**
         * Animation when orientation changed to landscape.
         *
         * @param duration       the duration
         * @param previousWidth  the previous width
         * @param targetWidth    the target width
         * @param previousHeight the previous height
         * @param targetHeight   the target height
         */
        void toLandScape(int duration, final int previousWidth, final int targetWidth, final int previousHeight, final int targetHeight) {
            animate().x(0).y(0).setDuration(0).start();

            ValueAnimator height = ValueAnimator.ofInt(previousHeight, targetHeight);
            height.setDuration(duration);
            height.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().height = value;

                    container.getLayoutParams().height = value;

                    requestLayout();
                }
            });

            ValueAnimator width = ValueAnimator.ofInt(previousWidth, targetWidth);
            width.setDuration(duration);
            width.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().width = value;

                    container.getLayoutParams().width = value;

                    requestLayout();
                }
            });

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {

                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            animatorSet.setDuration(animatonDuration);
            animatorSet.setInterpolator(new AccelerateInterpolator());
            animatorSet.playTogether(width, height);
            animatorSet.start();
        }

        /**
         * Expand animation.
         *
         * @param duration     the duration
         * @param targetWidth  the target width
         * @param targetHeight the target height
         */
        void expand(int duration, final int targetWidth, final int targetHeight) {
            animate().x(0).y(0).setDuration(300).start();

            ValueAnimator height = ValueAnimator.ofInt(getHeight(), targetHeight);
            height.setDuration(duration);
            height.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().height = value;

                    container.getLayoutParams().height = value;

                    requestLayout();
                }
            });

            ValueAnimator width = ValueAnimator.ofInt(getWidth(), targetWidth);
            width.setDuration(duration);
            width.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().width = value;

                    container.getLayoutParams().width = value;

                    requestLayout();
                }
            });

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    onViewToggled();
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            animatorSet.setDuration(animatonDuration);
            animatorSet.setInterpolator(new AccelerateInterpolator());
            animatorSet.playTogether(width, height);
            animatorSet.start();
        }

        /**
         * Collapse the view to default size.
         *
         * @param duration     the duration
         * @param targetWidth  the target width
         * @param targetHeight the target height
         */
        void collapse(int duration, final int targetWidth, final int targetHeight) {
            if (isOverlay()) {
                animate().x(dm.widthPixels - defaultWidth).y(ScreenHelper.actionBar(getContext()) + ScreenHelper.getStatusBarHeight(getContext())).setDuration(300).start();
            } else if (getParent() != null) {
                ViewGroup view = (ViewGroup) getParent();
                animate().x(dm.widthPixels - defaultWidth - view.getPaddingRight()).y(view.getPaddingTop()).setDuration(200).start();
            }

            ValueAnimator height = ValueAnimator.ofInt(getHeight(), targetHeight);
            height.setDuration(duration);
            height.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().height = value;

                    container.getLayoutParams().height = value;

                    requestLayout();
                }
            });

            ValueAnimator width = ValueAnimator.ofInt(getWidth(), targetWidth);
            width.setDuration(duration);
            width.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();

                    getLayoutParams().width = value;

                    container.getLayoutParams().width = value;

                    requestLayout();
                }
            });

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    onViewToggled();
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            animatorSet.setDuration(animatonDuration);
            animatorSet.setInterpolator(new AccelerateInterpolator());
            animatorSet.playTogether(width, height);
            animatorSet.start();
        }

    }

    protected boolean isOverlay() {
        return getParent() != null && getParent() == getRootView();
    }
}