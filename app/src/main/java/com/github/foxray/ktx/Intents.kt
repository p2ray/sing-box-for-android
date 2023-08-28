package com.github.foxray.ktx

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.foxray.R
import com.github.foxray.ui.shared.AbstractActivity

fun AbstractActivity.startFilesForResult(
    launcher: ActivityResultLauncher<String>, input: String
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    val builder = MaterialAlertDialogBuilder(this)
    builder.setPositiveButton(resources.getString(android.R.string.ok), null)
    builder.setMessage(R.string.file_manager_missing)
    builder.show()
}