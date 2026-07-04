package com.velikececi.udfdonusturucu.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose `LocalContext` çoğunlukla bir Activity'dir, ama ContextWrapper zincirine sarılmış olabilir. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
