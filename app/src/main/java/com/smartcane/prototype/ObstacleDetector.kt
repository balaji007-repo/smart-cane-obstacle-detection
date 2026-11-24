package com.smartcane.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

@TransformExperimental
internal data class DetectionFrame(val boxes: List<RectF>, val transform: OutputTransform)

@TransformExperimental
internal class ObstacleDetector(context: Context) : AutoCloseable {
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.5f)
            .build(),
    )
    private val transformFactory = ImageProxyTransformFactory().apply {
        setUsingCropRect(true)
        setUsingRotationDegrees(true)
    }

    fun detect(frame: ImageProxy): DetectionFrame {
        val transform = transformFactory.getOutputTransform(frame)
        val crop = frame.cropRect
        val rotation = Matrix().apply { setRotate(frame.imageInfo.rotationDegrees.toFloat()) }
        val buffer = frame.toBitmap()
        try {
            val upright = Bitmap.createBitmap(buffer, crop.left, crop.top, crop.width(), crop.height(), rotation, false)
            try {
                val boxes = BitmapImageBuilder(upright).build().use { image ->
                    detector.detect(image).detections().map { RectF(it.boundingBox()) }
                }
                return DetectionFrame(boxes, transform)
            } finally {
                if (upright !== buffer) upright.recycle()
            }
        } finally {
            buffer.recycle()
        }
    }

    override fun close() = detector.close()
}
