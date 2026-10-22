package com.framecoach.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Matrix
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.framecoach.core.*
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors

data class FrameReading(val sample: FloatArray, val light: Light, val atMs: Long)

/** CameraX owns image lifetimes via MlKitAnalyzer. All public callbacks run on the UI thread. */
class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val preview: PreviewView,
    private val onFrame: (FrameReading) -> Unit,
    private val onPeople: (List<Face>, Body?, Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val controller = LifecycleCameraController(context)
    private val faces = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).enableTracking().build())
    private val poses = PoseDetection.getClient(PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    @Volatile var portrait = true
    @Volatile private var closed = false
    private var front = false
    private var lastFrame = 0L
    private val mlAnalyzer = MlKitAnalyzer(listOf(faces, poses), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
        if (!closed && portrait && preview.width > 0 && preview.height > 0) {
            val w = preview.width.toFloat()
            val h = preview.height.toFloat()
            val detectedFaces = result.getValue(faces).orEmpty().map { face ->
                val rect = face.boundingBox
                Face(Point(rect.exactCenterX() / w, rect.exactCenterY() / h), rect.width() / w, rect.height() / h, face.headEulerAngleY, null)
            }.filter { it.center.x in 0f..1f && it.center.y in 0f..1f }
            val pose = result.getValue(poses)
            val joints = jointIds.mapNotNull { (joint, id) ->
                pose?.getPoseLandmark(id)?.let { joint to Point(it.position.x / w, it.position.y / h, it.inFrameLikelihood) }
            }.toMap()
            onPeople(detectedFaces, Body(joints).takeIf { joints.isNotEmpty() }, result.getThrowable(faces) != null || result.getThrowable(poses) != null)
        }
    }
    private val analyzer = object : ImageAnalysis.Analyzer {
        override fun getTargetCoordinateSystem(): Int = mlAnalyzer.targetCoordinateSystem
        override fun getDefaultTargetResolution(): Size = Size(640, 480)
        override fun updateTransform(matrix: Matrix?) = mlAnalyzer.updateTransform(matrix)

        override fun analyze(image: ImageProxy) {
            val now = SystemClock.elapsedRealtime()
            if (closed || now - lastFrame < 200) { image.close(); return }
            lastFrame = now
            try {
                val reading = readLuma(image, now, portrait)
                main.execute { if (!closed) onFrame(reading) }
            } catch (_: Exception) {
                image.close()
                main.execute { if (!closed) onError("读取相机画面失败，请重新进入取景。") }
                return
            }
            if (portrait) {
                // This delegate closes the ImageProxy after both asynchronous detectors finish.
                mlAnalyzer.analyze(image)
            } else image.close()
        }
    }

    fun start() {
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        preview.controller = controller
        controller.initializationFuture.addListener({
            if (!closed) {
                try {
                    controller.initializationFuture.get()
                    controller.bindToLifecycle(owner)
                    controller.cameraInfo?.cameraState?.observe(owner) { state ->
                        if (!closed && state.error != null) onError("相机暂不可用，请检查权限或其他相机应用后重试。")
                    }
                } catch (_: Exception) { onError("相机启动失败，可切换到演示；请检查设备相机是否被其他应用占用。") }
            }
        }, main)
    }

    fun flip() {
        val candidate = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            if (!controller.hasCamera(candidate)) { onError("此设备没有可切换的镜头。"); return }
            controller.cameraSelector = candidate
            front = !front
        } catch (_: Exception) { onError("切换镜头失败，请重试。") }
    }

    fun capture(onComplete: (Boolean) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FrameCoach-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FrameCoach")
        }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        try {
            controller.takePicture(options, main, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { onComplete(true) }
                override fun onError(exception: androidx.camera.core.ImageCaptureException) { onComplete(false) }
            })
        } catch (_: Exception) { onComplete(false) }
    }

    fun close() {
        closed = true
        controller.clearImageAnalysisAnalyzer()
        controller.unbind()
        preview.controller = null
        faces.close(); poses.close()
        analysisExecutor.shutdown()
    }

    companion object {
        private val jointIds = mapOf(
            Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER, Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW, Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
            Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST, Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
            Joint.LEFT_HIP to PoseLandmark.LEFT_HIP, Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
            Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE, Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
            Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE, Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE
        )

        private fun readLuma(image: ImageProxy, now: Long, ignorePerson: Boolean): FrameReading {
            val crop = image.cropRect
            val plane = image.planes.first()
            val buffer = plane.buffer.duplicate()
            val base = buffer.position()
            val all = ArrayList<Float>(48 * 36)
            val background = ArrayList<Float>(48 * 36)
            for (y in 0 until 36) for (x in 0 until 48) {
                val px = (crop.left + (x + .5f) * crop.width() / 48).toInt().coerceIn(0, image.width - 1)
                val py = (crop.top + (y + .5f) * crop.height() / 36).toInt().coerceIn(0, image.height - 1)
                val value = (buffer.get(base + py * plane.rowStride + px * plane.pixelStride).toInt() and 255) / 255f
                all.add(value)
                // Use the frame border for portrait stability so the subject can move at the center.
                if (!ignorePerson || x < 7 || x >= 41 || y < 5 || y >= 31) {
                    // Average a cell-sized neighborhood; single pixels exaggerate subpixel tremor.
                    val radiusX = (crop.width() / 96).coerceAtLeast(1)
                    val radiusY = (crop.height() / 72).coerceAtLeast(1)
                    var total = 0f
                    for (oy in -1..1) for (ox in -1..1) {
                        val sx = (px + ox * radiusX).coerceIn(crop.left, crop.right - 1)
                        val sy = (py + oy * radiusY).coerceIn(crop.top, crop.bottom - 1)
                        total += (buffer.get(base + sy * plane.rowStride + sx * plane.pixelStride).toInt() and 255) / 255f
                    }
                    background.add(total / 9f)
                }
            }
            return FrameReading(background.toFloatArray(), PreviewLight.fromLuma(all.toFloatArray()), now)
        }
    }
}
