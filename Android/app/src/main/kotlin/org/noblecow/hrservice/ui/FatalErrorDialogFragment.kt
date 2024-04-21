package org.noblecow.hrservice.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.noblecow.hrservice.R

private const val ARG_ERROR_MESSAGE = "errorMessage"

class FatalErrorDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val builder = AlertDialog.Builder(activity).apply {
                setMessage(
                    arguments?.getString(ARG_ERROR_MESSAGE) ?: getString(R.string.error_unknown)
                )
                setPositiveButton(R.string.exit) { _, _ ->
                    activity.finishAndRemoveTask()
                }
            }
            builder.create().apply {
                setCanceledOnTouchOutside(false)
            }
        } ?: error("Activity cannot be null")
    }

    companion object {
        fun newInstance(errorMessage: String): FatalErrorDialogFragment {
            return FatalErrorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ERROR_MESSAGE, errorMessage)
                }
            }
        }
    }
}
