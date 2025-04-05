/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/4
 */

package cn.rtast.ksuspend.bridge.plugin.util

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

val FqName.callableId get() =
    CallableId(this.parent(), shortName())

val FqName.classId get() =
    ClassId.topLevel(this)

val String.fQName get() = FqName(this)