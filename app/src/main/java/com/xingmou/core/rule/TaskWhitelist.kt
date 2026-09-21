package com.xingmou.core.rule

/**
 * 白名单任务（对应提示词第八节硬规则第 3 条）。
 *
 * 模型只能选择经专业审核的训练模块、素材类型和难度范围，不能自由编排训练。
 * 该白名单应在程序层维护，模型输出不在白名单内的任务一律拒绝。
 */
object TaskWhitelist {

    /** 经审核的训练任务清单（示例，需按实际课程体系填充） */
    val allowedTasks: Set<String> = setOf(
        "按顺序放图片",
        "图片配对",
        "颜色分类",
        "形状匹配",
        "大小排序",
        "指认物品",
        "模仿动作",
        "跟读词语"
    )

    /** 训练领域 */
    val allowedDomains: Set<String> = setOf(
        "认知", "语言", "精细动作", "社会适应", "注意与执行"
    )

    /** 素材类型 */
    val allowedMaterialTypes: Set<String> = setOf(
        "图片", "卡片", "实物", "音频", "视频"
    )

    /** 是否在白名单内（领域 + 任务 + 素材三层校验） */
    fun isAllowed(domain: String?, task: String?, materialType: String?): Boolean {
        if (task != null && task !in allowedTasks) return false
        if (domain != null && domain !in allowedDomains) return false
        if (materialType != null && materialType !in allowedMaterialTypes) return false
        return true
    }
}
