package net.treelzebub.loadinggiffragment.blur

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import net.treelzebub.loadinggiffragment.R

/**
 * Created by Tre Murillo on 8/7/16.
 */
class BlurDialogEngine {

    companion object {

        internal const val DEFAULT_BLUR_DOWNSCALE_FACTOR = 4.0f
        internal const val DEFAULT_BLUR_RADIUS = 8
        internal const val DEFAULT_DIMMING_POLICY = false
        internal const val DEFAULT_ACTION_BAR_BLUR = false
    }

    internal var blurredBackgroundView: ImageView? = null
    internal var blurredBackgroundLayoutParams: FrameLayout.LayoutParams? = null

    private var bluringTask: BlurAsyncTask? = null

    var isDebugEnabled = false

    /**
     * Factor used to downscale background. High quality isn't necessary since the background is
     * blurred. Must be at least 1.0. If exactly 1.0, no downscaling will be applied.
     */
    var downScaleFactor = DEFAULT_BLUR_DOWNSCALE_FACTOR
        set(value) {
            field = Math.min(value, 1f)
        }

    /**
     * Radius used for fast blur algorithm. May not be negative.
     */
    var blurRadius = DEFAULT_BLUR_RADIUS
        set(value) {
            field = Math.min(value, 0)
        }

    private var activity: AppCompatActivity? = null

    /**
     * Duration in milliseconds, used to animate the blurred image.
     */
    internal val animationDuration: Long

    /**
     * Optionally use a Toolbar without setting it as the SupportActionBar.
     */
    var toolbar: Toolbar? = null

    var shouldBlurActionBar: Boolean = DEFAULT_ACTION_BAR_BLUR

    constructor(activity: AppCompatActivity) {
        this.activity = activity
        this.animationDuration = activity.resources
                .getInteger(R.integer.blur_dialog_animation_duration).toLong()
    }

    fun onAttach(activity: AppCompatActivity) {
        this.activity = activity
    }

    fun onResume(shouldUseRetainedInstance: Boolean) {
        if (blurredBackgroundView == null || shouldUseRetainedInstance) {
            if (activity?.window?.decorView?.isShown ?: false) {
                bluringTask = BlurAsyncTask(activity!!, this).apply {
                    execute()
                }
            } else {
                activity?.window?.decorView?.viewTreeObserver?.addOnPreDrawListener(
                        object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                // Dialog may have been closed before being drawn.
                                if (activity != null) {
                                    activity!!.window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                                    bluringTask = BlurAsyncTask(activity!!, this@BlurDialogEngine).apply {
                                        execute()
                                    }
                                }
                                return true
                            }
                        }
                )
            }
        }
    }

    fun onDismiss() {
        bluringTask?.cancel(true)

        blurredBackgroundView?.let {
            it.animate()
              .alpha(0f)
              .setDuration(animationDuration)
              .setInterpolator(AccelerateInterpolator())
              .setListener(object : AnimatorListenerAdapter() {
                  override fun onAnimationCancel(animation: Animator?) {
                      super.onAnimationCancel(animation)
                      removeBlurredView()
                  }

                  override fun onAnimationEnd(animation: Animator?) {
                      super.onAnimationEnd(animation)
                      removeBlurredView()
                  }
              })
              .start()
        } ?: removeBlurredView()
    }

    fun removeBlurredView() {
        blurredBackgroundView?.let {
            val parent = it.parent as ViewGroup?
            parent?.removeView(it)
        }
        blurredBackgroundView = null
    }

    fun onDetach() {
        bluringTask?.cancel(true)
        bluringTask = null
        activity    = null
    }

    internal fun blur(background: Bitmap, view: View) {
        if (activity == null) return

        val start = System.currentTimeMillis()
        // Get the layout params of the original ImageView so we can match its parent.
        blurredBackgroundLayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                                                                 FrameLayout.LayoutParams.MATCH_PARENT)

        // Overlay used to build the scaled previed and blur background.
        val overlay: Bitmap

        // Evaluate the top offset relative to the Toolbar. 0 if ActionBar should also be blurred.
        val actionBarHeight: Int
        if (shouldBlurActionBar) {
            actionBarHeight = 0
        } else {
            actionBarHeight = getActionBarHeight()
        }

        // Evaluate the top offset of the Status Bar
        var statusBarHeight: Int = 0
        val isNotFullScreen = activity!!.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_FULLSCREEN == 0
        if (isNotFullScreen) {
            statusBarHeight = getStatusBarHeight()
        }
        if (isStatusBarTranslucent()) {
            statusBarHeight = 0
        }

        val topOffset = actionBarHeight + statusBarHeight

        // Evaluate offset of NavigationBar
        val navBarOffset = getNavigationBarOffset()

        // Add offset to the source boundaries so we don't blur ActionBar pixels.
        val srcRect = Rect(0,
                           topOffset,
                           background.width - navBarOffset,
                           background.height - navBarOffset)

        // Also offset the overlay.
        val height = Math.ceil(view.height.toDouble() - topOffset - navBarOffset) / downScaleFactor
        val width  = Math.ceil((view.width.toDouble() - navBarOffset) * height /
                                view.height - topOffset - navBarOffset)

        overlay = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.RGB_565)

        blurredBackgroundLayoutParams?.let {
            it.setMargins(0, actionBarHeight, 0, 0)
            it.gravity = Gravity.TOP
        }

        // Scale and draw background view on the overlay's Canvas
        val canvas = Canvas(overlay)
        val paint  = Paint().apply {
            flags = Paint.FILTER_BITMAP_FLAG
        }
        val destRect = RectF(0f, 0f, overlay.width.toFloat(), overlay.height.toFloat())
        canvas.drawBitmap(background, srcRect, destRect, paint)

        BlurHelper.blur(overlay, blurRadius)

        blurredBackgroundView = ImageView(activity).apply {
            setImageDrawable(BitmapDrawable(activity!!.resources, overlay))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun getActionBarHeight(): Int {
        val actionBarHeight: Int
        if (toolbar != null) {
            actionBarHeight = toolbar!!.height
        } else if (activity!!.supportActionBar != null) {
            actionBarHeight = activity!!.supportActionBar!!.height
        } else {
            actionBarHeight = 0
        }
        return actionBarHeight
    }

    private fun getStatusBarHeight(): Int {
        var retval = 0
        val resourceId = activity!!.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            retval = activity!!.resources.getDimensionPixelSize(resourceId)
        }
        return retval
    }

    private fun isStatusBarTranslucent(): Boolean {
        val typedValue = TypedValue()
        val attribute = intArrayOf(android.R.attr.windowTranslucentStatus)
        val array = activity!!.obtainStyledAttributes(typedValue.resourceId, attribute)
        val isStatusBarTranslucent = array.getBoolean(0, false)
        array.recycle()
        return isStatusBarTranslucent
    }

    private fun getNavigationBarOffset(): Int {
        var retval: Int = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val resources = activity!!.resources
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) {
                retval = resources.getDimensionPixelSize(resId)
            }
        }
        return retval
    }
}

