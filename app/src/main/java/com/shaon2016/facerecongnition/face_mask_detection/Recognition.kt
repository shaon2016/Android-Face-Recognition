package com.shaon2016.facerecongnition.face_mask_detection

import android.graphics.Bitmap
import android.graphics.RectF

data class Recognition(
    val id: String,
    var title: String,
    var distance: Float = -1f
) {
    var location: RectF = RectF()
    var color: Int = 0
    var crop: Bitmap? = null
    var extra: Any? = null

}
