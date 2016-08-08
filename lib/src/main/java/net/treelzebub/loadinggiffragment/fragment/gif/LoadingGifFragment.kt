package net.treelzebub.loadinggiffragment.fragment.gif

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.view.Gravity
import net.treelzebub.loadinggiffragment.R
import net.treelzebub.loadinggiffragment.fragment.blur.BlurDialogFragment
import pl.droidsonroids.gif.GifImageView

/**
 * Created by Tre Murillo on 8/7/16.
 */
open class LoadingGifFragment : BlurDialogFragment() {

    private var _dialog: Dialog? = null

    private lateinit var gradient: GradientDrawable
    private lateinit var gifImageView: GifImageView

    private val radius = 10
    private val _downScaleFactor = 5f
    private val shouldDim = true
    private val shouldBlurToolbar = false
    private val cornerRadius = 30f

    private @ColorInt var defaultBg: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (_dialog == null) {
            _dialog = Dialog(activity, R.style.LoadingGifDialog)
            _dialog!!.let {
                it.setContentView(R.layout.dialog_loading_gif)
                it.setCanceledOnTouchOutside(true)
                it.window.setGravity(Gravity.CENTER)
            }
            gradient = GradientDrawable()
            gradient.cornerRadius = this.cornerRadius
            gifImageView = _dialog!!.findViewById(R.id.gif_image_view) as GifImageView
            val _id = getDrawableResId()
            setBackgroundColor(BitmapFactory.decodeResource(resources, _id).getPixel(5, 5))
            gifImageView.setImageResource(_id)
            gradient.setColor(ContextCompat.getColor(context, R.color.default_bg))
        }

        return _dialog!!
    }

    open fun getDrawableResId(): Int {
        return R.drawable.default_loading
    }

    fun setBackgroundColor(bgColor: Int) {
        this.defaultBg = bgColor
        gradient.setColor(bgColor)
        _dialog?.findViewById(R.id.background)?.background = gradient
    }

    override fun getBlurRadius(): Int {
        return radius
    }

    override fun shouldBlurToolbar(): Boolean {
        return shouldBlurToolbar
    }

    override fun getDownScaleFactor(): Float {
        return _downScaleFactor
    }

    override fun shouldDim(): Boolean {
        return shouldDim
    }
}
