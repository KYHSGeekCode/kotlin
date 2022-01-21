/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

import kotlin.wasm.internal.ExternalInterfaceType
import kotlin.wasm.internal.jsToKotlinStringAdapter

/**
 * The base class for all errors and exceptions. Only instances of this class can be thrown or caught.
 *
 * @param message the detail message string.
 * @param cause the cause of this throwable.
 */
public open class Throwable(open val message: String?, open val cause: kotlin.Throwable?) {
    constructor(message: String?) : this(message, null)

    constructor(cause: Throwable?) : this(cause?.toString(), cause)

    constructor() : this(null, null)

    internal val jsStack: ExternalInterfaceType = captureStackTrace()

    internal val stack: String by lazy {
        jsToKotlinStringAdapter(jsStack)
    }

    internal var suppressedExceptionsList: MutableList<Throwable>? = null
}

@JsFun("() => new Error().stack")
private external fun captureStackTrace(): ExternalInterfaceType