package com.shaon2016.facerecongnition.face_detection

import android.graphics.Bitmap
import android.graphics.RectF

data class Recognition(
    val id: String,
    val title: String,
    val distance: Float = -1f,
    val extra: Any
) {
    var location: RectF = RectF()
    var color: Int = 0
    var crop: Bitmap? = null
}
