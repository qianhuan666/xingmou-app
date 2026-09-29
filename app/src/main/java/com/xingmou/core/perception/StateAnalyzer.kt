package com.xingmou.core.perception

import kotlin.math.abs

/**
 * 将 PerceptionFrame（原始关键点）转换为 PerceptionState（语义状态）。
 * 纯启发式规则，无 ML 模型，便于调参。
 */
object StateAnalyzer {

    // ---------- 情感 ----------
    fun analyzeEmotion(bs: Map<String, Float>): Pair<Emotion, Float> {
        if (bs.isEmpty()) return Emotion.NEUTRAL to 0f
        fun avg(vararg keys: String) = keys.mapNotNull { bs[it] }.average().toFloat()

        val smile = avg("mouthSmileLeft", "mouthSmileRight")
        val frown = avg("mouthFrownLeft", "mouthFrownRight")
        val browDown = avg("browDownLeft", "browDownRight")
        val browInnerUp = bs["browInnerUp"] ?: 0f
        val browOuterUp = avg("browOuterUpLeft", "browOuterUpRight")
        val jawOpen = bs["jawOpen"] ?: 0f
        val eyeWide = avg("eyeWideLeft", "eyeWideRight")
        val eyeSquint = avg("eyeSquintLeft", "eyeSquintRight")
        val eyeBlink = avg("eyeBlinkLeft", "eyeBlinkRight")
        val mouthPress = avg("mouthPressLeft", "mouthPressRight")
        val mouthStretch = avg("mouthStretchLeft", "mouthStretchRight")

        // 评分
        val happyScore = smile * 2f - frown
        val sadScore = frown * 2f + browInnerUp + eyeSquint
        val angryScore = browDown * 2f + mouthPress
        val surprisedScore = eyeWide * 2f + jawOpen + browOuterUp
        val fearfulScore = browInnerUp + eyeWide + mouthStretch
        val cryingScore = eyeSquint + browDown + frown + eyeBlink * 0.5f
        val laughingScore = smile + jawOpen * 1.5f

        val scores: Map<Emotion, Float> = mapOf(
            Emotion.HAPPY to happyScore,
            Emotion.SAD to sadScore,
            Emotion.ANGRY to angryScore,
            Emotion.SURPRISED to surprisedScore,
            Emotion.FEARFUL to fearfulScore,
            Emotion.CRYING to cryingScore,
            Emotion.LAUGHING to laughingScore
        )
        val max = scores.maxByOrNull { it.value }
        val emotion = max?.key ?: Emotion.NEUTRAL
        val score = max?.value ?: 0f
        return if (score > 0.5f) emotion to score else Emotion.NEUTRAL to 0f
    }

    // ---------- 专注度 ----------
    fun analyzeFocus(frame: PerceptionFrame): FocusLevel {
        if (!frame.faceDetected) return FocusLevel.DISTRACTED
        val (pitch, yaw) = frame.headRotation.pitch to frame.headRotation.yaw
        val blinkL = frame.faceBlendshapes["eyeBlinkLeft"] ?: 0f
        val blinkR = frame.faceBlendshapes["eyeBlinkRight"] ?: 0f
        val blink = (blinkL + blinkR) / 2f
        if (blink > 0.85f) return FocusLevel.SLEEPING
        if (abs(yaw) > 30f || abs(pitch) > 30f) return FocusLevel.DISTRACTED
        return FocusLevel.FOCUSED
    }

    // ---------- 视线 ----------
    fun analyzeGaze(frame: PerceptionFrame): GazeDirection {
        val (pitch, yaw) = frame.headRotation.pitch to frame.headRotation.yaw
        return when {
            abs(yaw) > abs(pitch) -> if (yaw > 15f) GazeDirection.LEFT else if (yaw < -15f) GazeDirection.RIGHT else GazeDirection.CENTER
            pitch > 15f -> GazeDirection.DOWN
            pitch < -15f -> GazeDirection.UP
            else -> GazeDirection.CENTER
        }
    }

    // ---------- 人体动作 ----------
    fun analyzeBodyAction(frame: PerceptionFrame): BodyAction {
        val lm = frame.poseLandmarks
        if (lm.size < 25) return BodyAction.NONE
        // MediaPipe pose 索引: 11左肩 12右肩 13左肘 14右肘 15左腕 16右腕 23左髋 24右髋
        val lShoulder = lm[11]; val rShoulder = lm[12]
        val lElbow = lm[13]; val rElbow = lm[14]
        val lWrist = lm[15]; val rWrist = lm[16]
        val shoulderY = (lShoulder.y + rShoulder.y) / 2f

        val leftRaised = lWrist.y < shoulderY - 0.05f
        val rightRaised = rWrist.y < shoulderY - 0.05f

        if (leftRaised && rightRaised) return BodyAction.BOTH_HANDS_RAISE
        if (leftRaised) return BodyAction.HAND_RAISE_LEFT
        if (rightRaised) return BodyAction.HAND_RAISE_RIGHT

        // 摸脸/头：手腕接近头部区域
        val faceY = (lm[0].y + lm[1].y + lm[2].y + lm[3].y) / 4f
        if (lWrist.y < lShoulder.y && abs(lWrist.x - lm[0].x) < 0.1f) return BodyAction.TOUCH_FACE
        if (rWrist.y < rShoulder.y && abs(rWrist.x - lm[0].x) < 0.1f) return BodyAction.TOUCH_FACE

        return BodyAction.NONE
    }

    // ---------- 手势 ----------
    fun analyzeGesture(landmarks: List<Point3D>): HandGesture {
        if (landmarks.size < 21) return HandGesture.NONE
        // 指尖: 8(index),12(middle),16(ring),20(pinky)  拇指:4
        // MCP: 5,9,13,17
        val extended = booleanArrayOf(
            landmarks[8].y < landmarks[6].y,   // index
            landmarks[12].y < landmarks[10].y,  // middle
            landmarks[16].y < landmarks[14].y,  // ring
            landmarks[20].y < landmarks[18].y   // pinky
        )
        val count = extended.count { it }
        val thumbExtended = abs(landmarks[4].x - landmarks[2].x) > 0.05f

        return when {
            count == 0 -> HandGesture.FIST
            count == 5 || (count == 4 && thumbExtended) -> HandGesture.OPEN_PALM
            count == 1 && extended[0] -> HandGesture.POINT_INDEX
            count == 2 && extended[0] && extended[1] -> HandGesture.VICTORY
            thumbExtended && count == 0 -> HandGesture.THUMB_UP
            else -> HandGesture.NONE
        }
    }

    // ---------- 手物交互（启发式） ----------
    fun analyzeObjectInteraction(frame: PerceptionFrame): ObjectInteraction {
        if (!frame.leftHandDetected && !frame.rightHandDetected) return ObjectInteraction.NONE
        if (!frame.poseDetected) return ObjectInteraction.OPERATING
        // 双手在身前中下部区域 → 操作物体
        val midHip = frame.poseLandmarks.getOrNull(23)?.y ?: 0.5f
        val hands = listOfNotNull(
            frame.leftHandLandmarks.firstOrNull(),
            frame.rightHandLandmarks.firstOrNull()
        )
        val avgHandY = hands.map { it.y }.average().toFloat()
        if (hands.size >= 2) return ObjectInteraction.TWO_HAND
        if (avgHandY > midHip - 0.1f) return ObjectInteraction.OPERATING
        return ObjectInteraction.APPROACHING
    }

    // ---------- 综合 ----------
    fun analyze(frame: PerceptionFrame, prevMeltdown: Boolean): PerceptionState {
        val (emotion, conf) = analyzeEmotion(frame.faceBlendshapes)
        val isMeltdown = prevMeltdown || (emotion == Emotion.CRYING && conf > 1.0f)
        return PerceptionState(
            timestampMs = frame.timestampMs,
            emotion = emotion,
            emotionConfidence = conf,
            focusLevel = analyzeFocus(frame),
            gazeDirection = analyzeGaze(frame),
            bodyAction = analyzeBodyAction(frame),
            leftGesture = analyzeGesture(frame.leftHandLandmarks),
            rightGesture = analyzeGesture(frame.rightHandLandmarks),
            objectInteraction = analyzeObjectInteraction(frame),
            isMeltdown = isMeltdown
        )
    }
}
