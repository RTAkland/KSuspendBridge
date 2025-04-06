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
import cn.rtast.ksuspend.bridge.plugin.util.irGet
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
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
            it.descriptor.annotations.any { annotation ->
                annotation.fqName.toString().split(".").last() == "KSuspendBlocking"
            }
        }.toList().forEach { func ->
            if (!func.isSuspend) throw NonSuspendFunctionException("该函数不是一个挂起函数 ${func.name.asString()}")
            val suffix = func.getAnnotation(FqName("cn.rtast.ksuspend.bridge.annotations.KSuspendBlocking"))
                .getAnnotationValueOrNull<String>("suffix") ?: "JvmBlocking"
            val newFunctionName = "${func.name.asString()}$suffix"
            if (declaration.functions.any { it.name.asString() == newFunctionName }) {
                throw GeneratedFunctionDuplicatedException("生成的方法名重复 ${func.name.asString()} -> $newFunctionName")
            }
            val runBlockingFqName = FunctionInfo("kotlinx.coroutines", null, "runBlocking").callableId
            val runBlockingFunSymbol = pluginContext.referenceFunctions(runBlockingFqName).firstOrNull()
                ?: error("找不到函数 runBlockingWithResult，请确保该函数已正确定义。")
            val newFunction = pluginContext.irFactory.buildFun {
                name = Name.identifier(func.name.identifier + "JvmBlocking")
                returnType = func.returnType
                visibility = func.visibility
                modality = Modality.FINAL
                origin = IrDeclarationOrigin.DEFINED
                isSuspend = false
            }.apply {
                parent = func.parent
                copyTypeParametersFrom(func).forEach { oldParam ->
                    val valueBuilder = IrValueParameterBuilder().apply {
                        name = oldParam.name
                        type = oldParam.defaultType
                    }
                    val newParam = pluginContext.irFactory.buildValueParameter(valueBuilder, func.parent)
                    valueParameters += newParam
                }
            }
            val irBuilder = DeclarationIrBuilder(pluginContext, newFunction.symbol)
            val callOriginal = irBuilder.irCall(func.symbol).apply {
                func.valueParameters.forEachIndexed { index, param ->
                    putValueArgument(index, irGet(newFunction.valueParameters[index]))
                }
            }
            val lambda = pluginContext.irFactory.buildFun {
                name = Name.identifier("lambdaForBlocking")
                returnType = func.returnType
                isSuspend = true
                origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            }.apply {
                parent = newFunction
                body = irBuilder.irBlockBody {
                    +irReturn(callOriginal)
                }
            }
            val lambdaExpr = IrFunctionExpressionImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = pluginContext.irBuiltIns.suspendFunctionN(0).typeWith(func.returnType),
                function = lambda,
                origin = IrStatementOrigin.LAMBDA
            )
            val callRunBlocking = irBuilder.irCall(runBlockingFunSymbol).apply {
                putValueArgument(0, lambdaExpr)
            }
            newFunction.body = irBuilder.irBlockBody {
                +irReturn(callRunBlocking)
            }
            addFunctionToParent(newFunction, func)
        }
        return super.visitClassNew(declaration)
    }

    fun addFunctionToParent(newFunction: IrSimpleFunction, originalFunction: IrSimpleFunction) {
        when (val p = originalFunction.parent) {
            is IrClass -> p.declarations += newFunction
            is IrPackageFragment -> p.declarations += newFunction
            else -> error("Unsupported parent: $p")
        }
    }
}
