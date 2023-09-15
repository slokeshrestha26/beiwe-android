package org.beiwe.app.survey

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.SeekBar

/** A customized slider ui element so that we can have a grayed out effect for untouched sliders.
 * Based on http://stackoverflow.com/a/19008611
 * @author Josh Zagorsky */

class SeekBarEditableThumb : SeekBar {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private var compatThumb: Drawable? = null

    /** Return a Boolean of whether or not the user has touched the SeekBar yet
     * @return */
    var hasBeenTouched: Boolean? = null
        private set
    private var min = 0
    override fun setThumb(thumb: Drawable) {
        super.setThumb(thumb)
        compatThumb = thumb
    }

    /** Make the SeekBar's "Thumb" invisible, and mark it as "user hasn't touched this yet" */
    fun markAsUntouched() {
        compatThumb!!.mutate().alpha = 0
        hasBeenTouched = false
    }

    /** Make the SeekBar's "Thumb" visible, and mark it as "user has touched this" */
    fun markAsTouched() {
        compatThumb!!.mutate().alpha = 255
        hasBeenTouched = true
    }

    /** The minimum doesn't have to be zero; the SeekBar can start at another number (even negative)
     * @param min */
    override fun setMin(min: Int) {
        this.min = min
    }

    /** The minimum doesn't have to be zero; the SeekBar can start at another number (even negative)
     * @param min */
    override fun getMin(): Int {
        return min
    }
}