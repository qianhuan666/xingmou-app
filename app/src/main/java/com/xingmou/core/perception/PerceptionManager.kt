package com.xingmou.core.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.Executors

/**
 * 摄像头采集 + MediaPipe 三模型推理。
 * 每帧输出 PerceptionFrame，通过 callback 回调。
 * 低端机 FPS < 10 时自动降级，只保留 FaceLandmarker。
 */
class PerceptionManager(
    private val context: Context,
    private val onFrame: (PerceptionFrame) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var faceLandmarker: FaceLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    var previewView: PreviewView? = null
        set(value) { field = value; bindCamera() }

    // 降级控制
    private var degradedMode = false
    private var lastFpsCheckTime = 0L
    private var fpsInWindow = 0

    fun start() {
        runCatching { initLandmarkers() }.onFailure { Log.e(TAG, "init failed", it) }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        faceLandmarker?.close()
        poseLandmarker?.close()
        handLandmarker?.close()
        faceLandmarker = null
        poseLandmarker = null
        handLandmarker = null
    }

    private fun initLandmarkers() {
        val faceOpts = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, faceOpts)

        if (!degradedMode) {
            val poseOpts = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOpts)

            val handOpts = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(2)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, handOpts)
        }
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, FrameAnalyzer()) }

        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        runCatching {
            provider.unbindAll()
            val useCases = mutableListOf<androidx.camera.core.UseCase>(analysis)
            previewView?.let { pv ->
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                useCases.add(0, preview)
            }
            provider.bindToLifecycle(
                ProcessLifecycleOwner.get() as LifecycleOwner,
                selector,
                *useCases.toTypedArray()
            )
        }.onFailure { Log.e(TAG, "bindCamera failed", it) }
    }

    private fun checkDegrade() {
        val now = System.currentTimeMillis()
        fpsInWindow++
        if (lastFpsCheckTime == 0L) lastFpsCheckTime = now
        if (now - lastFpsCheckTime >= 5000L) {
            val fps = fpsInWindow * 1000f / (now - lastFpsCheckTime)
            if (fps < 10f && !degradedMode) {
                Log.w(TAG, "FPS=$fps < 10, degrading to face-only mode")
                degradedMode = true
                poseLandmarker?.close()
                handLandmarker?.close()
                poseLandmarker = null
                handLandmarker = null
            }
            fpsInWindow = 0
            lastFpsCheckTime = now
        }
    }

    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val frame = runCatching { analyzeFrame(imageProxy) }.getOrNull()
            imageProxy.close()
            checkDegrade()
            frame?.let { onFrame(it) }
        }

        private fun analyzeFrame(imageProxy: ImageProxy): PerceptionFrame {
            val ts = System.currentTimeMillis()
            val mpImage = imageProxy.toMpImage()
            val faceResult = faceLandmarker?.detect(mpImage)
            val poseResult = if (!degradedMode) poseLandmarker?.detect(mpImage) else null
            val handResult = if (!degradedMode) handLandmarker?.detect(mpImage) else null

            val blendshapes = faceResult?.faceBlendshapes()
                ?.orElse(emptyList())
                ?.firstOrNull()
                ?.associate { it.categoryName() to it.score() } ?: emptyMap()
            val headRot = faceResult?.facialTransformationMatrixes()
                ?.orElse(emptyList())
                ?.firstOrNull()?.let { matrixToHeadRotation(it) } ?: HeadRotation()
            val poseLm = poseResult?.landmarks()
                ?.firstOrNull()
                ?.map { lm: NormalizedLandmark -> Point3D(lm.x(), lm.y(), lm.z()) } ?: emptyList()
            val (leftHand, rightHand) = parseHands(handResult)
            val faceDetected = !faceResult?.faceLandmarks().isNullOrEmpty()

            return PerceptionFrame(
                timestampMs = ts,
                faceBlendshapes = blendshapes,
                headRotation = headRot,
                poseLandmarks = poseLm,
                leftHandLandmarks = leftHand,
                rightHandLandmarks = rightHand,
                faceDetected = faceDetected,
                poseDetected = poseLm.isNotEmpty(),
                leftHandDetected = leftHand.isNotEmpty(),
                rightHandDetected = rightHand.isNotEmpty()
            )
        }

        private fun ImageProxy.toMpImage(): MPImage {
            val bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            use { bitmapBuffer.copyPixelsFromBuffer(planes[0].buffer) }
            val matrix = Matrix().apply {
                postRotate(imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f, width.toFloat(), height.toFloat()) // 前置摄像头镜像
            }
            val rotated = Bitmap.createBitmap(bitmapBuffer, 0, 0, width, height, matrix, true)
            return BitmapImageBuilder(rotated).build()
        }
    }

    private fun matrixToHeadRotation(matrix: FloatArray): HeadRotation {
        // MediaPipe facialTransformationMatrixes: 4x4 row-major
        // Extract rotation angles (simplified)
        val m = matrix
        val pitch = Math.toDegrees(Math.asin((-m[8]).toDouble())).toFloat()
        val yaw = Math.toDegrees(Math.atan2(m[9].toDouble(), m[10].toDouble())).toFloat()
        val roll = Math.toDegrees(Math.atan2(m[4].toDouble(), m[0].toDouble())).toFloat()
        return HeadRotation(pitch, yaw, roll)
    }

    private fun parseHands(result: HandLandmarkerResult?): Pair<List<Point3D>, List<Point3D>> {
        var left = emptyList<Point3D>()
        var right = emptyList<Point3D>()
        result?.let {
            val handednesses = it.handednesses()
            val landmarks = it.landmarks()
            for (i in handednesses.indices) {
                if (i >= landmarks.size) break
                val handedness = handednesses[i].firstOrNull()?.categoryName() ?: continue
                val pts = landmarks[i].map { lm: NormalizedLandmark -> Point3D(lm.x(), lm.y(), lm.z()) }
                if (handedness == "Left") left = pts else right = pts
            }
        }
        return left to right
    }

    companion object {
        private const val TAG = "PerceptionManager"
    }
}
