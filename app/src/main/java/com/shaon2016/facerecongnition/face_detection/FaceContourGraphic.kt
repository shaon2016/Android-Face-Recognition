package com.shaon2016.facerecongnition.face_detection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.camera.GraphicOverlay

class FaceContourGraphic(
    private val overlay: GraphicOverlay,
    private val face: Face,
    private val imageRect: Rect
) : GraphicOverlay.Graphic(overlay) {

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
        val rect = calculateRect(
            imageRect.height().toFloat(),
            imageRect.width().toFloat(),
            face.boundingBox
        )
        canvas?.drawRect(rect, boxPaint)

        canvas?.drawText(recognizedTitle, rect.left, rect.top, titlePaint)
    }

    companion object {
        private const val BOX_STROKE_WIDTH = 5.0f
    }

}