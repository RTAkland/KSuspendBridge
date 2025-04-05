/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

@file:OptIn(UnsafeDuringIrConstructionAPI::class, ObsoleteDescriptorBasedAPI::class, FirIncompatiblePluginAPI::class)

package cn.rtast.ksuspend.bridge.plugin

import cn.rtast.ksuspend.bridge.plugin.entity.FunctionInfo
import cn.rtast.ksuspend.bridge.plugin.entity.callableId
import cn.rtast.ksuspend.bridge.plugin.exceptions.GeneratedFunctionDuplicatedException
import cn.rtast.ksuspend.bridge.plugin.exceptions.NonSuspendFunctionException
import cn.rtast.ksuspend.bridge.plugin.util.irBuilder
import cn.rtast.ksuspend.bridge.plugin.util.irGet
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotationValueOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KSuspendBridgeTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoidWithContext() {

    override fun visitClassNew(declaration: IrClass): IrStatement {
        declaration.functions.filter {
            it.descriptor.annotations.any { it.fqName.toString().split(".").last() == "KSuspendBlocking" }
        }.toList().forEach { func ->
            if (!func.isSuspend) throw NonSuspendFunctionException("该函数不是一个挂起函数 ${func.name.asString()}")
            val suffix = func.getAnnotation(FqName("cn.rtast.ksuspend.bridge.annotations.KSuspendBlocking"))
                .getAnnotationValueOrNull<String>("suffix") ?: "JvmBlocking"
            val newFunctionName = "${func.name.asString()}$suffix"
            if (declaration.functions.any { it.name.asString() == newFunctionName }) {
                throw GeneratedFunctionDuplicatedException("生成的方法名重复 ${func.name.asString()} -> $newFunctionName")
            }
            val runBlockingFqName = FunctionInfo("kotlinx.coroutines", null, "runBlocking").callableId
            val runBlockingFun = pluginContext.referenceFunctions(runBlockingFqName).firstOrNull()
            if (runBlockingFun == null) error("找不到函数")
            val generatedFunction = pluginContext.irFactory.buildFun {
                name = Name.identifier(newFunctionName)
                isSuspend = false
                visibility = func.visibility
                returnType = func.returnType
                modality = func.modality
                startOffset = func.startOffset
                endOffset = func.endOffset
            }.apply {
                parent = declaration
                func.valueParameters.forEach { param -> valueParameters += param.copyTo(this) }
                body = pluginContext.irBuilder(symbol).irBlockBody {
                    val suspendCall = pluginContext.irBuilder(func.symbol).run {
                        irCall(func).apply {
                            if (func.valueParameters.isNotEmpty()) {
                                func.valueParameters.forEachIndexed { index, param ->
                                    putValueArgument(index, irGet(valueParameters[index]))
                                }
                            }
                        }
                    }
                    val runBlockingCall = pluginContext.irBuilder(func.symbol).run {
                        irCall(runBlockingFun).apply {
                            putTypeArgument(0, func.returnType)
                            putValueArgument(0, suspendCall)
                        }
                    }
                    +irReturn(runBlockingCall)
                }
            }
            declaration.declarations += generatedFunction
        }
        return super.visitClassNew(declaration)
    }
}