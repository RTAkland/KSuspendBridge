/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/5
 */

package cn.rtast.ksuspend.bridge

import kotlinx.coroutines.runBlocking


public fun <T> runBlockingWithResult(block: suspend () -> T): T {
    return runBlocking { block() }
}