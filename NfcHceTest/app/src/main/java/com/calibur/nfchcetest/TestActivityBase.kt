package com.calibur.nfchcetest

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.calibur.nfchcetest.MyDialogFragment.DialogCallback
import com.calibur.nfchcetest.Util.SoundPlayer

open class TestActivityBase : AppCompatActivity() {
    protected var mSoundPlayer: SoundPlayer? = null
    private var mToast: Toast? = null
    private var mDialogFragment: MyDialogFragment? = null
    private var mLogTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mSoundPlayer = SoundPlayer(this)
        mLogTag = javaClass.simpleName
    }

    public override fun onDestroy() {
        super.onDestroy()
        dismissDialog()
        mSoundPlayer!!.release()
    }

    protected fun showProgressDialog(title: String, message: String) {
        showDialog(MyDialogFragment.Companion.DIALOG_ID_PROGRESS, title, message)
    }

    protected fun showAlertDialog(title: String, message: String) {
        showDialog(MyDialogFragment.Companion.DIALOG_ID_ERROR, title, message)
    }

    protected fun showInfoDialog(title: String, message: String) {
        showDialog(MyDialogFragment.Companion.DIALOG_ID_INFO, title, message)
    }

    private fun showDialog(id: Int, title: String, message: String) {
        runOnUiThread {
            try {
                if (mDialogFragment != null) {
                    mDialogFragment!!.dismiss()
                }
                mDialogFragment = MyDialogFragment.Companion.newInstance(
                    id, title, message, mDialogCallback
                )
                mDialogFragment!!.isCancelable = true
                mDialogFragment!!.show(fragmentManager, MyDialogFragment.Companion.DIALOG_TAG)
            } catch (e: RuntimeException) {
            } catch (e: Exception) {
            }
        }
    }

    protected open fun dismissDialog() {
        runOnUiThread {
            try {
                if (mDialogFragment != null) {
                    mDialogFragment!!.dismiss()
                    mDialogFragment = null
                }
            } catch (e: RuntimeException) {
            } catch (e: Exception) {
            }
        }
    }

    private val mDialogCallback = DialogCallback { }

    // if msg is null, it is just canceled.
    protected fun showToast(msg: String?) {
        runOnUiThread {
            try {
                if (mToast != null) {
                    mToast!!.cancel()
                    mToast = null
                }
                if (msg != null) {
                    mToast = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT)
                    mToast?.show()
                }
            } catch (e: Exception) {
                Log.d(mLogTag, e.toString())
            }
        }
    }

    protected fun showSuccessDialog(htmlText: HtmlText?) {
        mSoundPlayer!!.play(SoundPlayer.Companion.SOUND_SUCCESS)
        showInfoDialog("Test", "Passed")
        if (htmlText != null) {
            htmlText.addSubject2("                   ", HtmlText.Companion.TRANS_BLUE2)
            htmlText.addSubject2("    Test passed    ", HtmlText.Companion.TRANS_BLUE2)
            htmlText.addSubject2("                   ", HtmlText.Companion.TRANS_BLUE2)
        }
        Log.d(mLogTag, "Test passed")
    }

    protected fun showFailureDialog(msg: String, htmlText: HtmlText?) {
        mSoundPlayer!!.play(SoundPlayer.Companion.SOUND_FAILURE)
        showAlertDialog("Test failed", msg)
        if (htmlText != null) {
            htmlText.addSubject2("                   ", HtmlText.Companion.TRANS_RED1)
            htmlText.addSubject2("    Test failed    ", HtmlText.Companion.TRANS_RED1)
            htmlText.addSubject2("                   ", HtmlText.Companion.TRANS_RED1)
        }
        Log.d(mLogTag, "Test failed")
    }
}