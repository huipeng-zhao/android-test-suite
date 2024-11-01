package com.calibur.nfchcetest

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.ProgressDialog
import android.os.Bundle

class MyDialogFragment : DialogFragment() {
    fun interface DialogCallback {
        fun onConfirmed()
    }

    interface DialogCallback2 {
        fun onClick(which: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        when (arguments.getInt(KEY_DLG_ID)) {
            DIALOG_ID_PROGRESS -> {
                val dialog = ProgressDialog(activity)
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
                dialog.setMessage(arguments.getString(KEY_DLG_MSG))
                dialog.setCancelable(false)
                return dialog
            }

            DIALOG_ID_INFO -> {
                return AlertDialog.Builder(activity)
                    .setTitle(arguments.getString(KEY_DLG_TITLE))
                    .setMessage(arguments.getString(KEY_DLG_MSG))
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setCancelable(false)
                    .setPositiveButton("OK", null).create()
            }

            DIALOG_ID_ERROR -> {
                return AlertDialog.Builder(activity)
                    .setTitle(arguments.getString(KEY_DLG_TITLE))
                    .setMessage(arguments.getString(KEY_DLG_MSG))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .setPositiveButton("OK", null).create()
            }

            DIALOG_ID_CONF -> {
                return AlertDialog.Builder(activity)
                    .setTitle(arguments.getString(KEY_DLG_TITLE))
                    .setMessage(arguments.getString(KEY_DLG_MSG))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.no, null)
                    .setNegativeButton(android.R.string.yes) { dialog, whichButton -> mDialogCallback!!.onConfirmed() }
                    .create()
            }

            DIALOG_ID_CHOISE -> {
                return AlertDialog.Builder(activity)
                    .setTitle(arguments.getString(KEY_DLG_TITLE))
                    .setMessage(arguments.getString(KEY_DLG_MSG))
                    .setCancelable(false)
                    .setItems(arguments.getInt(KEY_DLG_ARRAY)) { dialog, which ->
                        mDialogCallbackForList!!.onClick(
                            which
                        )
                    }
                    .create()
            }
        }
        return null
    }

    companion object {
        const val DIALOG_ID_PROGRESS: Int = 1
        const val DIALOG_ID_INFO: Int = 2
        const val DIALOG_ID_ERROR: Int = 3
        const val DIALOG_ID_CONF: Int = 4
        const val DIALOG_ID_CHOISE: Int = 5

        const val DIALOG_TAG: String = "dialog"

        private const val KEY_DLG_ID = "id"
        private const val KEY_DLG_TITLE = "title"
        private const val KEY_DLG_MSG = "msg"
        private const val KEY_DLG_ARRAY = "array"

        private var mDialogCallback: DialogCallback? = null
        private var mDialogCallbackForList: DialogCallback2? = null

        fun newInstance(
            id: Int,
            title: String?,
            message: String?,
            callback: DialogCallback?
        ): MyDialogFragment {
            mDialogCallback = callback
            val flagment = MyDialogFragment()
            val args = Bundle()
            args.putInt(KEY_DLG_ID, id)
            args.putString(KEY_DLG_TITLE, title)
            args.putString(KEY_DLG_MSG, message)
            flagment.arguments = args
            return flagment
        }

        fun newInstance(
            id: Int,
            title: String?,
            message: String?,
            arrayRes: Int,
            callback: DialogCallback2?
        ): MyDialogFragment {
            mDialogCallbackForList = callback
            val flagment = MyDialogFragment()
            val args = Bundle()
            args.putInt(KEY_DLG_ID, id)
            args.putString(KEY_DLG_TITLE, title)
            args.putString(KEY_DLG_MSG, message)
            args.putInt(KEY_DLG_ARRAY, arrayRes)
            flagment.arguments = args
            return flagment
        }
    }
}
