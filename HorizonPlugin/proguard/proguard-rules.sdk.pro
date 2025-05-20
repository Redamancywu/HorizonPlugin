# 作者：Redamancy  时间：2025-05-19
# SDK基础混淆规则，防止关键类被混淆

# 保留所有被自动注册的模块类
-keep class * { @com.neil.plugin.autoregister.AutoRegisterModule *; }

# 保留常见SDK入口类
-keep class com.neil.** { *; }

# 你可以在DSL中追加更多规则 