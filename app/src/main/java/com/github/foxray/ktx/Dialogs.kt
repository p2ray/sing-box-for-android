package com.github.foxray.ktx

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.foxray.R

fun Context.errorDialogBuilder(@StringRes messageId: Int): MaterialAlertDialogBuilder {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, null)
}

fun Context.errorDialogBuilder(message: String): MaterialAlertDialogBuilder {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
}

fun Context.errorDialogBuilder(exception: Throwable): MaterialAlertDialogBuilder {
    return errorDialogBuilder(exception.localizedMessage ?: exception.toString())
}