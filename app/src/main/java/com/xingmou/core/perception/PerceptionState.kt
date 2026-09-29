package com.xingmou.core.perception

/** 表情状态。 */
enum class Emotion { HAPPY, SAD, ANGRY, SURPRISED, FEARFUL, DISGUSTED, NEUTRAL, CRYING, LAUGHING }

/** 专注度级别。 */
enum class FocusLevel { FOCUSED, DISTRACTED, SLEEPING }

/** 视线方向。 */
enum class GazeDirection { CENTER, LEFT, RIGHT, UP, DOWN }

/** 人体动作。 */
enum class BodyAction {
    NONE, HAND_RAISE_LEFT, HAND_RAISE_RIGHT, BOTH_HANDS_RAISE,
    POINT_LEFT, POINT_RIGHT, CLAP, NOD, SHAKE_HEAD,
    HANDS_ON_HIPS, HANDS_ON_HEAD, TOUCH_FACE, TOUCH_HEAD,
    STANDING, SITTING, BENDING, TURNING
}

/** 手势。 */
enum class HandGesture {
    NONE, FIST, OPEN_PALM, POINT_INDEX, THUMB_UP, THUMB_DOWN,
    OK, VICTORY, ROCK, SCISSORS, PAPER, HEART, HOOK,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5
}

/** 手物交互状态。 */
enum class ObjectInteraction { NONE, APPROACHING, OPERATING, PICKING, PLACING, MOVING, TWO_HAND, LEAVING }

/** 判断后的语义状态，由 StateAnalyzer 输出。 */
data class PerceptionState(
    val timestampMs: Long,
    val emotion: Emotion = Emotion.NEUTRAL,
    val emotionConfidence: Float = 0f,
    val focusLevel: FocusLevel = FocusLevel.FOCUSED,
    val gazeDirection: GazeDirection = GazeDirection.CENTER,
    val bodyAction: BodyAction = BodyAction.NONE,
    val leftGesture: HandGesture = HandGesture.NONE,
    val rightGesture: HandGesture = HandGesture.NONE,
    val objectInteraction: ObjectInteraction = ObjectInteraction.NONE,
    val isMeltdown: Boolean = false
)
