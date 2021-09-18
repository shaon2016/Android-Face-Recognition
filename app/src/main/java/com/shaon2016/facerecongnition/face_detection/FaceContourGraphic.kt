package com.shaon2016.facerecongnition.face_detection

import android.graphics.*
import android.util.Log
import androidx.core.graphics.toRectF
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.camera.GraphicOverlay

class FaceContourGraphic(
    private val overlay: GraphicOverlay,
    private val face: Face
) : GraphicOverlay.Graphic(overlay) {
    private var output2OverlayTransform: Matrix = Matrix()
    var frameHeight = 0
    var frameWidth = 0


    private val facePositionPaint: Paint
    private val idPaint: Paint
    private val boxPaint: Paint
    private val titlePaint: Paint

    var recognizedTitle = ""

    init {
        val selectedColor = Color.WHITE

        facePositionPaint = Paint()
        facePositionPaint.color = selectedColor

        idPaint = Paint()
        idPaint.color = selectedColor

        boxPaint = Paint()
        boxPaint.color = selectedColor
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = BOX_STROKE_WIDTH

        titlePaint = Paint()
        titlePaint.color = Color.YELLOW
        titlePaint.textSize = 60f
    }

    override fun draw(canvas: Canvas?) {
        if (!overlay.areDimsInit) {
            val viewWidth = canvas!!.width.toFloat()
            val viewHeight = canvas.height.toFloat()
            val xFactor: Float = viewWidth / frameWidth.toFloat()
            val yFactor: Float = viewHeight / frameHeight.toFloat()
            // Scale and mirror the coordinates ( required for front lens )
            output2OverlayTransform.preScale(xFactor, yFactor)
            output2OverlayTransform.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
            overlay.areDimsInit = true
        } else {
            val bBox = face.boundingBox.toRectF()
            output2OverlayTransform.mapRect(bBox)

            canvas?.drawRect(bBox, boxPaint)

            canvas?.drawText(recognizedTitle, bBox.left, bBox.top, titlePaint)
        }
    }

    companion object {
        private const val BOX_STROKE_WIDTH = 5.0f
    }

}