package com.gnoemes.shikimori.presentation.view.common.fragment

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.presentation.view.base.fragment.MvpDialogFragment

class AuthDialog : MvpDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(context!!).apply {
            setMessage(R.string.dialog_auth_message)
            setPositiveButton(R.string.common_sign_in) { _, _ -> target?.onSignIn() }
            setNegativeButton(R.string.common_sign_up) { _, _ -> target?.onSignUp() }
        }.create()
    }

    private val target: AuthCallback?
        get() = (targetFragment as? AuthCallback)

    interface AuthCallback {
        fun onSignIn()
        fun onSignUp()
    }
}