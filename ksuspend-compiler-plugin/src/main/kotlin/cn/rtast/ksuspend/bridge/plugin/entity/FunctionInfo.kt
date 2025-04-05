/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/5
 */

package cn.rtast.ksuspend.bridge.plugin.entity

import cn.rtast.ksuspend.bridge.plugin.util.fQName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

data class FunctionInfo(
    val packageName: String,
    val className: String? = null,
    val functionName: String
)

val FunctionInfo.callableId
    get() = CallableId(packageName.fQName, className?.fQName, Name.identifier(functionName))