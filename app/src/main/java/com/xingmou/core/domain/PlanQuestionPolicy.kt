package com.xingmou.core.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xingmou.core.model.SupportLevel
import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.data.catalog.QuestionType
import com.xingmou.data.catalog.TaskCatalog
import com.xingmou.data.db.PlanVersionEntity
import kotlin.math.abs

/** 当前生效专业方案对儿童端出题的最小、可审计快照。 */
data class ActivePlanQuestionPolicy(
    val planId: String,
    val planVersion: Int,
    val moduleId: String?,
    val difficulty: Int,
    val supportLevel: SupportLevel,
    val observableGoal: String = ""
)

/**
 * 将专业方案 payload 解析为题目策略，并负责将策略应用到静态题库。
 * 解析失败时返回 null，儿童端继续使用本地默认课程，不阻断训练。
 */
object PlanQuestionPolicy {
    private val taskAliases = mapOf(
        "图片配对" to "M01",
        "配对" to "M01",
        "即时记忆" to "M01",
        "翻牌记忆" to "M02",
        "顺序记忆" to "M03",
        "工作记忆" to "M04",
        "图片命名" to "L01",
        "句子表达" to "L02",
        "理解指令" to "L03",
        "替代沟通" to "L04"
    )

    fun from(plan: PlanVersionEntity?): ActivePlanQuestionPolicy? {
        if (plan == null || plan.status.lowercase() != "active") return null
        val root = runCatching { JsonParser.parseString(plan.payloadJson).asJsonObject }.getOrNull() ?: return null
        val module = root.string("module_id")?.let(::resolveModuleId)
            ?: root.string("task")?.let(::resolveModuleId)
        val moduleConfig = root.array("modules")?.firstOrNull { item ->
            val candidate = item.asJsonObject.string("module_id")?.let(::resolveModuleId)
            module != null && candidate == module
        }?.asJsonObject
        val difficulty = (moduleConfig?.int("difficulty") ?: root.int("difficulty") ?: 1).coerceIn(1, 5)
        val support = parseSupport(moduleConfig?.string("support_level") ?: root.string("support_level"))
        return ActivePlanQuestionPolicy(
            planId = plan.planId,
            planVersion = plan.version,
            moduleId = moduleConfig?.string("module_id")?.let(::resolveModuleId) ?: module,
            difficulty = difficulty,
            supportLevel = support,
            observableGoal = moduleConfig?.string("goal") ?: root.string("observable_goal").orEmpty()
        )
    }

    /** 方案优先；同一方案继续训练时保留 Agent 上一次自适应难度。 */
    fun effectiveDifficulty(
        policy: ActivePlanQuestionPolicy?,
        currentPlanId: String?,
        currentPlanVersion: Int?,
        currentDifficulty: Int
    ): Int = if (policy != null && policy.planId == currentPlanId && policy.planVersion == currentPlanVersion) {
        currentDifficulty.coerceIn(1, 5)
    } else {
        policy?.difficulty ?: currentDifficulty.coerceIn(1, 5)
    }

    /** 先按方案模块筛选，再按目标难度排序；同模块的所有变式仍保留在进度统计中。 */
    fun selectQuestions(
        bank: List<QuestionDefinition>,
        policy: ActivePlanQuestionPolicy?,
        difficulty: Int
    ): List<QuestionDefinition> {
        if (policy == null) return bank
        val scoped = policy.moduleId?.let { id -> bank.filter { it.moduleId == id } }.orEmpty()
        val source = if (scoped.isNotEmpty()) scoped else bank
        return source.sortedWith(compareBy<QuestionDefinition> { abs(it.difficulty - difficulty) }.thenBy { it.id })
            .map { it.forDifficulty(difficulty) }
    }

    /** 方案难度 1/2 分别裁剪为 2/3 个选项，同时始终保留正确选项。 */
    fun QuestionDefinition.forDifficulty(level: Int): QuestionDefinition {
        if (type == QuestionType.OBSERVED || correctOption == null) return copy(difficulty = level.coerceIn(1, 5))
        val count = when (level.coerceIn(1, 5)) {
            1 -> 2
            2 -> 3
            else -> options.size
        }.coerceAtMost(options.size)
        if (count >= options.size) return copy(difficulty = level.coerceIn(1, 5))
        val target = correctOption.coerceIn(options.indices)
        val indexes = buildList {
            add(target)
            options.indices.filter { it != target }.take(count - 1).forEach(::add)
        }
        return copy(
            options = indexes.map(options::get),
            correctOption = indexes.indexOf(target),
            difficulty = level.coerceIn(1, 5)
        )
    }

    fun parseSupport(value: String?): SupportLevel = runCatching {
        SupportLevel.valueOf(value.orEmpty().trim().uppercase())
    }.getOrDefault(SupportLevel.L1)

    private fun resolveModuleId(value: String): String? {
        val normalized = value.trim()
        if (TaskCatalog.find(normalized) != null) return normalized
        TaskCatalog.all.firstOrNull { it.name == normalized }?.let { return it.id }
        return taskAliases.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.int(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive }?.asInt
    private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
}

