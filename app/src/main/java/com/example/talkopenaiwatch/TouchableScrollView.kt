package com.example.talkopenaiwatch

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.widget.NestedScrollView

class TouchableScrollView : NestedScrollView {

    var onClickListener: ((View) -> Unit)? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            onClickListener?.invoke(this)
        }
        return super.onTouchEvent(ev)
    }
}