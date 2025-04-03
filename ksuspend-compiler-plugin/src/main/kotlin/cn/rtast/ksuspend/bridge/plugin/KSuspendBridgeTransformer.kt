/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

@file:OptIn(UnsafeDuringIrConstructionAPI::class, ObsoleteDescriptorBasedAPI::class, FirIncompatiblePluginAPI::class)

package cn.rtast.ksuspend.bridge.plugin

import cn.rtast.ksuspend.bridge.plugin.exceptions.GeneratedFunctionDuplicatedException
import cn.rtast.ksuspend.bridge.plugin.exceptions.NonSuspendFunctionException
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotationValueOrNull
import org.jetbrains.kotlin.name.CallableId
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
            if (declaration.functions.any { it.name.asString() == newFunctionName }) throw GeneratedFunctionDuplicatedException(
                "生成的方法名重复 ${func.name.asString()} -> $newFunctionName"
            )
            generateBlockingFunction(declaration, func)
        }
        return declaration
    }

    fun generateBlockingFunction(irClass: IrClass, irFunction: IrSimpleFunction) {
        val generatedFunction = irClass.addFunction {
            startOffset = irFunction.startOffset
            endOffset = irFunction.endOffset
            name = Name.identifier(irFunction.name.asString() + "JvmBlocking")
            returnType = irFunction.returnType
            visibility = irFunction.visibility
        }.apply {
            valueParameters = irFunction.valueParameters.dropLast(1).map { it.copyTo(this) }
            val runBlockingFqName = FqName("kotlinx.coroutines.runBlocking")
            val runBlockingSymbol =
                pluginContext.referenceFunctions(CallableId(runBlockingFqName.parent(), runBlockingFqName.shortName()))
                    .firstOrNull() ?: return
//            pluginContext.irFactory.createBlockBody(startOffset, endOffset, listOf(
//
//            ))
//            val irCall = IrCallImpl(
//                startOffset = irFunction.startOffset,
//                endOffset = irFunction.endOffset,
//                type = returnType,
//                symbol = targetFunction.symbol,
//                typeArgumentsCount = 0,
//                valueArgumentsCount = targetFunction.valueParameters.size
//            )
//            body = pluginContext.irBuilder(this.symbol).irBlockBody {
//                +irReturn(
//                    irCall(runBlockingSymbol).apply {
//                        putTypeArgument(0, irFunction.returnType)
//                        pluginContext.irFactory.buildFun {
//                            name = Name.special("<anonymous>")
//                            returnType = irFunction.returnType
//                            visibility = DescriptorVisibilities.PUBLIC
//                            origin = IrDeclarationOrigin.LOCAL_FUNCTION
//                        }.apply func@{
//                            addDispatchReceiver { pluginContext.irBuiltIns.anyType } // 添加 CoroutineScope 接收者
//                            irFunction.valueParameters.dropLast(1).forEachIndexed { index, param ->
//                                addValueParameter(param.name.asString(), param.type)
//                            }
//                            body = pluginContext.irBuilder(symbol).irBlockBody {
//                                +irReturn(irCall(irFunction.symbol).apply {
//                                    irFunction.valueParameters.dropLast(1).forEachIndexed { index, param ->
//                                        putValueArgument(index, irGet(valueParameters[index]))
//                                    }
//                                })
//                            }
//                        }
//                    }
//                )
//            }
        }

        irClass.declarations.add(generatedFunction)
    }
}
