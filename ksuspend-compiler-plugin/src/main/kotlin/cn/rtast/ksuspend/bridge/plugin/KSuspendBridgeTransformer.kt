/*
 * Copyright © 2025 RTAkland
 * Author: RTAkland
 * Date: 2025/4/3
 */

@file:OptIn(UnsafeDuringIrConstructionAPI::class, ObsoleteDescriptorBasedAPI::class, FirIncompatiblePluginAPI::class)

package cn.rtast.ksuspend.bridge.plugin

import cn.rtast.ksuspend.bridge.plugin.exceptions.GeneratedFunctionDuplicatedException
import cn.rtast.ksuspend.bridge.plugin.exceptions.NonSuspendFunctionException
import cn.rtast.ksuspend.bridge.plugin.util.callableId
import cn.rtast.ksuspend.bridge.plugin.util.classId
import cn.rtast.ksuspend.bridge.plugin.util.irGet
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.defaultType
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
            if (declaration.functions.any { it.name.asString() == newFunctionName }) throw GeneratedFunctionDuplicatedException(
                "生成的方法名重复 ${func.name.asString()} -> $newFunctionName"
            )
            generate(declaration, func)
        }
        return declaration
    }

    fun generate(irClass: IrClass, originalFunction: IrSimpleFunction) {
        val irFactory = pluginContext.irFactory
        if (!originalFunction.isSuspend) return
        val newFunction = irFactory.createSimpleFunction(
            startOffset = originalFunction.startOffset,
            endOffset = originalFunction.endOffset,
            origin = IrDeclarationOrigin.DEFINED,
            name = Name.identifier(originalFunction.name.asString() + "JvmBlocking"),
            visibility = originalFunction.visibility,
            modality = originalFunction.modality,
            returnType = originalFunction.returnType,
            isInline = originalFunction.isInline,
            isExternal = originalFunction.isExternal,
            isTailrec = originalFunction.isTailrec,
            isSuspend = false,
            symbol = IrSimpleFunctionSymbolImpl(),
            isInfix = originalFunction.isInfix,
            isExpect = originalFunction.isExpect,
            isOperator = originalFunction.isOperator
        ).apply {
            parent = irClass
            dispatchReceiverParameter = originalFunction.dispatchReceiverParameter
            extensionReceiverParameter = originalFunction.extensionReceiverParameter
            valueParameters = originalFunction.valueParameters.dropLast(1).map { it.copyTo(this) }
            body = pluginContext.createRunBlockingBody(this, originalFunction)
        }
        irClass.declarations.add(newFunction)
    }

    private fun IrPluginContext.createRunBlockingBody(
        newFunction: IrSimpleFunction,
        originalFunction: IrSimpleFunction
    ): IrBlockBody {
        val irFactory = this.irFactory
        val runBlockingFqName = FqName("kotlinx.coroutines.runBlocking")
        val runBlockingSymbol =
            pluginContext.referenceFunctions(runBlockingFqName.callableId)
                .first()
        val emptyCoroutineContextFqName = FqName("kotlin.coroutines.EmptyCoroutineContext")
        val emptyCoroutineContext = referenceClass(emptyCoroutineContextFqName.classId)
            ?.owner ?: error("Cannot find EmptyCoroutineContext")
        return irFactory.createBlockBody(
            startOffset = newFunction.startOffset,
            endOffset = newFunction.endOffset,
            statements = listOf(
                IrReturnImpl(
                    startOffset = newFunction.startOffset,
                    endOffset = newFunction.endOffset,
                    type = newFunction.returnType,
                    returnTargetSymbol = newFunction.symbol,
                    value = IrCallImpl.fromSymbolOwner(
                        startOffset = newFunction.startOffset,
                        endOffset = newFunction.endOffset,
                        symbol = runBlockingSymbol
                    ).apply {
                        putValueArgument(
                            0, IrGetObjectValueImpl(
                                startOffset, endOffset, emptyCoroutineContext.defaultType, emptyCoroutineContext.symbol
                            )
                        )
                        putValueArgument(1, createSuspendLambda(newFunction, originalFunction))
                    }
                )
            )
        )
    }

    private fun IrPluginContext.createSuspendLambda(
        newFunction: IrSimpleFunction,
        originalFunction: IrSimpleFunction
    ): IrExpression {
        val irFactory = this.irFactory
        val functionType = originalFunction.symbol.owner.returnType
        val lambdaFunction = irFactory.createSimpleFunction(
            startOffset = newFunction.startOffset,
            endOffset = newFunction.endOffset,
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
            name = Name.special("<anonymous>"),
            visibility = DescriptorVisibilities.LOCAL,
            returnType = originalFunction.returnType,
            isSuspend = true, // Lambda 必须是 suspend
            symbol = IrSimpleFunctionSymbolImpl(),
            isInfix = originalFunction.isInfix,
            isInline = originalFunction.isInline,
            isExternal = originalFunction.isExternal,
            isTailrec = originalFunction.isTailrec,
            isExpect = originalFunction.isExpect,
            isOperator = originalFunction.isOperator,
            modality = originalFunction.modality
        ).apply {
            parent = newFunction
            valueParameters += newFunction.valueParameters.map { it.copyTo(this) }
            body = irFactory.createBlockBody(
                startOffset = startOffset,
                endOffset = endOffset,
                statements = listOf(
                    IrReturnImpl(
                        startOffset, endOffset, returnType, symbol,
                        IrCallImpl.fromSymbolOwner(
                            startOffset, endOffset, originalFunction.symbol
                        ).apply {
                            newFunction.dispatchReceiverParameter?.let { dispatchReceiver = irGet(it) }
                            newFunction.extensionReceiverParameter?.let { extensionReceiver = irGet(it) }
                            newFunction.valueParameters.forEachIndexed { index, param ->
                                putValueArgument(index, irGet(param))
                            }
                        }
                    )
                )
            )
        }

        return IrFunctionExpressionImpl(
            startOffset = newFunction.startOffset,
            endOffset = newFunction.endOffset,
            type = functionType,
            function = lambdaFunction,
            origin = IrStatementOrigin.LAMBDA
        )
    }
}