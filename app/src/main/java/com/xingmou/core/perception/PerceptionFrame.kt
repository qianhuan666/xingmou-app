package com.xingmou.core.perception

/** 单帧原始感知结果，由 PerceptionManager 输出。 */
data class PerceptionFrame(
    val timestampMs: Long,
    val faceBlendshapes: Map<String, Float> = emptyMap(),
    val headRotation: HeadRotation = HeadRotation(),
    val poseLandmarks: List<Point3D> = emptyList(),
    val leftHandLandmarks: List<Point3D> = emptyList(),
    val rightHandLandmarks: List<Point3D> = emptyList(),
    val faceDetected: Boolean = false,
    val poseDetected: Boolean = false,
    val leftHandDetected: Boolean = false,
    val rightHandDetected: Boolean = false
)

/** 头部旋转角度（度）。 */
data class HeadRotation(val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f)

/** 3D 关键点坐标（归一化 0~1）。 */
data class Point3D(val x: Float, val y: Float, val z: Float = 0f)
