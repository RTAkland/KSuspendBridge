/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

package test

import cn.rtast.ksuspend.bridge.annotations.KSuspendBlocking

class Main {

    @KSuspendBlocking
    suspend fun test() {
        println("aaa")
    }
}

fun main() {
    Main()
}