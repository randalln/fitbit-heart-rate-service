package org.noblecow.hrservice

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.noblecow.hrservice.R

class FatalErrorDialogFragment(
    private val errorMessage: String
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val builder = AlertDialog.Builder(activity).apply {
                setMessage(errorMessage)
                setPositiveButton(R.string.exit) { _, _ ->
                    activity.finish()
                }
            }
            builder.create()
        } ?: error("Activity cannot be null")
    }
}