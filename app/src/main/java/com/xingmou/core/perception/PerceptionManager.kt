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
    private var previewView: PreviewView? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 绑定预览视图；新旧页面切换时新视图先 attach，旧视图的 detach 会被身份校验拦截 */
    fun attachPreviewView(pv: PreviewView) {
        mainHandler.removeCallbacksAndMessages(null)
        if (previewView === pv) return
        previewView = pv
        bindCamera()
    }

    /** 延迟解绑，给新页面的 attach 留出时间；期间若已 attach 新视图则跳过 */
    fun detachPreviewView(pv: PreviewView) {
        mainHandler.postDelayed({
            if (previewView === pv) {
                previewView = null
                bindCamera()
            }
        }, 150)
    }

    // 降级控制
    private var degradedMode = false
    private var lastFpsCheckTime = 0L
    private var fpsInWindow = 0
    // MediaPipe 原生库不可用时的精简模式
    @Volatile var liteMode: Boolean = false
        private set

    fun start() {
        runCatching { initLandmarkers() }.onFailure {
            Log.e(TAG, "MediaPipe init failed, entering lite mode", it)
            liteMode = true
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        previewView = null
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

        runCatching {
            provider.unbindAll()
            val useCases = mutableListOf<androidx.camera.core.UseCase>(analysis)
            previewView?.let { pv ->
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setAspectRatioStrategy(
                                androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                            )
                            .build()
                    )
                    .build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                useCases.add(0, preview)
            }
            // 优先前置摄像头，没有则退回后置
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
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
            val frame = if (liteMode) {
                runCatching { analyzeFrameLite(imageProxy) }.getOrNull()
            } else {
                runCatching { analyzeFrame(imageProxy) }.getOrNull()
            }
            imageProxy.close()
            checkDegrade()
            frame?.let { onFrame(it) }
        }

        /** 精简模式：无 MediaPipe，仅用帧亮度/运动做基本感知 */
        private fun analyzeFrameLite(imageProxy: ImageProxy): PerceptionFrame {
            val ts = System.currentTimeMillis()
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            // 采样亮度（每 4 字节取一次）
            var sum = 0L
            var count = 0
            var i = 0
            while (i < bytes.size) {
                sum += bytes[i].toInt() and 0xFF
                count++
                i += 4
            }
            val brightness = if (count > 0) sum.toFloat() / count else 0f
            val faceDetected = brightness > 40f // 画面够亮，假定有人在
            return PerceptionFrame(
                timestampMs = ts,
                faceBlendshapes = emptyMap(),
                headRotation = HeadRotation(),
                poseLandmarks = emptyList(),
                leftHandLandmarks = emptyList(),
                rightHandLandmarks = emptyList(),
                faceDetected = faceDetected,
                poseDetected = false,
                leftHandDetected = false,
                rightHandDetected = false
            )
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
