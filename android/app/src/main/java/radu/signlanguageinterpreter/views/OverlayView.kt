package radu.signlanguageinterpreter.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import radu.signlanguageinterpreter.ai.Landmarker
import radu.signlanguageinterpreter.globals.mirror
import kotlin.math.max

@Suppress("PrivatePropertyName")
class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val STROKE_WIDTH = 8f

    private val leftHandPointPaint = Paint()
    private val leftHandStrokePaint = Paint()
    private val rightHandPointPaint = Paint()
    private val rightHandStrokePaint = Paint()
    private val posePointPaint = Paint()
    private val poseStrokePaint = Paint()

    private var leftHandLandmarks: List<NormalizedLandmark>? = null
    private var rightHandLandmarks: List<NormalizedLandmark>? = null
    private var poseLandmarks: List<NormalizedLandmark>? = null
    private var scaleFactor = 1f
    private var imageWidth = 1
    private var imageHeight = 1

    init {
        leftHandPointPaint.color = Color.rgb(0, 0, 128)
        leftHandPointPaint.strokeWidth = STROKE_WIDTH
        leftHandPointPaint.style = Paint.Style.STROKE

        leftHandStrokePaint.color = Color.rgb(65, 105, 225)
        leftHandStrokePaint.strokeWidth = STROKE_WIDTH
        leftHandStrokePaint.style = Paint.Style.STROKE

        rightHandPointPaint.color = Color.rgb(128, 0, 0)
        rightHandPointPaint.strokeWidth = STROKE_WIDTH
        rightHandPointPaint.style = Paint.Style.STROKE

        rightHandStrokePaint.color = Color.rgb(225, 105, 65)
        rightHandStrokePaint.strokeWidth = STROKE_WIDTH
        rightHandStrokePaint.style = Paint.Style.STROKE

        posePointPaint.color = Color.rgb(0, 128, 0)
        posePointPaint.strokeWidth = STROKE_WIDTH
        posePointPaint.style = Paint.Style.STROKE

        poseStrokePaint.color = Color.rgb(105, 225, 65)
        poseStrokePaint.strokeWidth = STROKE_WIDTH
        poseStrokePaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (leftHandLandmarks != null) {
            for (landmark in leftHandLandmarks!!) {
                canvas.drawCircle(
                    landmark.x() * imageWidth * scaleFactor,
                    landmark.y() * imageHeight * scaleFactor,
                    STROKE_WIDTH,
                    leftHandPointPaint
                )
            }
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                canvas.drawLine(
                    leftHandLandmarks!![connection.start()].x() * imageWidth * scaleFactor,
                    leftHandLandmarks!![connection.start()].y() * imageHeight * scaleFactor,
                    leftHandLandmarks!![connection.end()].x() * imageWidth * scaleFactor,
                    leftHandLandmarks!![connection.end()].y() * imageHeight * scaleFactor,
                    leftHandStrokePaint
                )
            }
        }
        if (rightHandLandmarks != null) {
            for (landmark in rightHandLandmarks!!) {
                canvas.drawCircle(
                    landmark.x() * imageWidth * scaleFactor,
                    landmark.y() * imageHeight * scaleFactor,
                    STROKE_WIDTH,
                    rightHandPointPaint
                )
            }
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                canvas.drawLine(
                    rightHandLandmarks!![connection.start()].x() * imageWidth * scaleFactor,
                    rightHandLandmarks!![connection.start()].y() * imageHeight * scaleFactor,
                    rightHandLandmarks!![connection.end()].x() * imageWidth * scaleFactor,
                    rightHandLandmarks!![connection.end()].y() * imageHeight * scaleFactor,
                    rightHandStrokePaint
                )
            }
        }
        if (poseLandmarks != null) {
            for (landmark in poseLandmarks!!) {
                canvas.drawCircle(
                    landmark.x() * imageWidth * scaleFactor,
                    landmark.y() * imageHeight * scaleFactor,
                    STROKE_WIDTH,
                    posePointPaint
                )
            }
            PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                canvas.drawLine(
                    poseLandmarks!![connection.start()].x() * imageWidth * scaleFactor,
                    poseLandmarks!![connection.start()].y() * imageHeight * scaleFactor,
                    poseLandmarks!![connection.end()].x() * imageWidth * scaleFactor,
                    poseLandmarks!![connection.end()].y() * imageHeight * scaleFactor,
                    poseStrokePaint
                )
            }
        }
    }

    fun setResult(
        result: Landmarker.Result, mirror: Boolean = false
    ) {
        this.imageHeight = result.imageHeight
        this.imageWidth = result.imageWidth
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

        if (mirror) {
            this.leftHandLandmarks = result.landmarkerResult.leftHandLandmarks?.map { it.mirror() }
            this.rightHandLandmarks =
                result.landmarkerResult.rightHandLandmarks?.map { it.mirror() }
            this.poseLandmarks = result.landmarkerResult.poseLandmarks?.map { it.mirror() }
        } else {
            this.leftHandLandmarks = result.landmarkerResult.leftHandLandmarks
            this.rightHandLandmarks = result.landmarkerResult.rightHandLandmarks
            this.poseLandmarks = result.landmarkerResult.poseLandmarks
        }

        invalidate()
    }
}