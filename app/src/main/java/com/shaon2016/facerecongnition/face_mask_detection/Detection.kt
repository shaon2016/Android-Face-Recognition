package com.shaon2016.facerecongnition.face_mask_detection

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF

data class Detection(
    val id: String,
    var label: String,
    var distance: Float = -1f
) {
    var location: Rect = Rect()
    var color: Int = 0
    var crop: Bitmap? = null
    var extra: Any? = null

}
