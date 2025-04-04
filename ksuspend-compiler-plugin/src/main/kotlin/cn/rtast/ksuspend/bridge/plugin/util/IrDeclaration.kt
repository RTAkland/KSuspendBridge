/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

package cn.rtast.ksuspend.bridge.plugin.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol

fun IrPluginContext.irBuilder(
    symbol: IrSymbol,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET,
) = DeclarationIrBuilder(this, symbol, startOffset, endOffset)

fun irGet(parameter: IrValueParameter): IrGetValueImpl {
    return IrGetValueImpl(parameter.startOffset, parameter.endOffset, parameter.type, parameter.symbol)
}