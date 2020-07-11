/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Shopify Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.shopify.testify

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UElement

val AnActionEvent.moduleName: String
    get() {
        val psiFile = this.getData(PlatformDataKeys.PSI_FILE)
        return (psiFile as? KtFile)?.module?.name ?: ""
    }

val UElement.baselineImageName: String
    get() {
        var imageName = "unknown"
        (this.sourcePsi as? KtNamedFunction)?.let {
            val c = it.fqName?.parent()?.shortName()
            val m = it.fqName?.shortName()
            imageName = "${c}_$m.png"
        }
        return imageName
    }

val UElement.methodName: String
    get() {
        val methodName = (this.sourcePsi as? KtNamedFunction)?.name
        return methodName ?: "unknown"
    }

val KtNamedFunction.testifyMethodInvocationPath: String
    get() {
        val className = this.fqName?.parent().toString()
        val methodName = this.fqName?.shortName().toString()
        return "$className#$methodName"
    }
