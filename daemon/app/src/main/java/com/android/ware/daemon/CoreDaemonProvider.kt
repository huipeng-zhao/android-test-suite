package com.android.ware.daemon

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

import com.android.ware.daemon.lifekeeper.LifeKeeper

class CoreDaemonProvider : ContentProvider() {
    private lateinit var mLifeKeeper: LifeKeeper
    companion object {
        private const val TAG = "CoreDaemon"
    }

    override fun onCreate(): Boolean {
        mLifeKeeper = LifeKeeper(context)
        mLifeKeeper.trigger()
        Log.i(TAG, "Daemon bring-up！！")
        return true
    }

    override fun query(uri: Uri?, projection: Array<String?>?,
        selection: String?, selectionArgs: Array<String?>?,
        sortOrder: String?): Cursor {
        throw UnsupportedOperationException("Invalid operation query().")
    }

    override fun getType(uri: Uri?): String {
        throw UnsupportedOperationException("Invalid operation getType().")
    }

    override fun insert(uri: Uri?, values: ContentValues?): Uri {
        throw UnsupportedOperationException("Invalid operation insert().")
    }

    override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<String?>?): Int {
        throw UnsupportedOperationException("Invalid operation delete().")
    }

    override fun update(uri: Uri?, values: ContentValues?,
        selection: String?, selectionArgs: Array<String?>?): Int {
        throw UnsupportedOperationException("Invalid operation update().")
    }
}