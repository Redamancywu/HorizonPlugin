// 作者：Redamancy  时间：2025-07-06
package com.neil.plugin.resource

/**
 * 资源命名策略枚举
 * 定义不同的资源命名策略
 * 
 * 作者：Redamancy  时间：2025-07-06
 */
enum class ResourceNamingStrategy {
    /**
     * 前缀策略：在资源名前添加前缀
     * 示例：prefix_resourceName
     */
    PREFIX,
    
    /**
     * 后缀策略：在资源名后添加后缀
     * 示例：resourceName_suffix
     */
    SUFFIX,
    
    /**
     * 混合策略：同时添加前缀和后缀
     * 示例：prefix_resourceName_suffix
     */
    MIXED,
    
    /**
     * 哈希策略：使用资源名称的哈希值作为名称
     * 示例：h123abc45
     */
    HASH,
    
    /**
     * 模块映射策略：根据模块名称生成特定前缀
     * 示例：m_login_button
     */
    MODULE_MAPPING,
    
    /**
     * 目录隔离策略：将资源按模块放入不同目录
     * 示例：module/resourceName
     */
    DIRECTORY_ISOLATION,
    
    /**
     * 语义命名策略：根据资源用途进行命名
     * 示例：btn_login_normal
     */
    SEMANTIC,
    
    /**
     * 版本化策略：在资源名中包含版本号
     * 示例：resourceName_v1
     */
    VERSIONED;
    
    companion object {
        /**
         * 根据策略名称获取枚举值
         * 
         * @param strategyName 策略名称
         * @return 命名策略枚举值，默认为PREFIX
         */
        fun fromString(strategyName: String?): ResourceNamingStrategy {
            return values().find { 
                it.name.equals(strategyName, ignoreCase = true)
            } ?: PREFIX
        }
        
        /**
         * 获取所有可用的命名策略名称
         * 
         * @return 策略名称列表
         */
        fun getAllStrategyNames(): List<String> {
            return values().map { it.name }
        }
    }
} 