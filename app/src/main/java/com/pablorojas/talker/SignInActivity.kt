package com.pablorojas.talker

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.firebase.ui.auth.AuthUI
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import com.firebase.ui.auth.IdpResponse
import android.content.Intent
import android.support.design.widget.Snackbar
import com.firebase.ui.auth.ErrorCodes
import org.jetbrains.anko.startActivity


class SignInActivity : AppCompatActivity() {

    private val RC_SIGN_IN = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)
    }

    override fun onResume() {
        super.onResume()
        val isUserSignedIn = FirebaseAuth.getInstance().currentUser != null
        if (!isUserSignedIn) signIn() else navToMain()
    }


    fun signIn() {
        // Choose authentication providers
        val providers = Arrays.asList(
                AuthUI.IdpConfig.PhoneBuilder().build())

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN)
    }

    fun navToMain() {
        startActivity<MainActivity>()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)
            when {
                resultCode == Activity.RESULT_OK -> {
                    // Successfully signed in
                    showSnackbar("SignIn successful")
                    navToMain()
                }

                response == null -> {
                    // Sign in failed
                    // User pressed back button
                    showSnackbar("Sign in cancelled")
                    return
                }

                response.error!!.errorCode == ErrorCodes.NO_NETWORK -> {
                    // Sign in failed
                    //No Internet Connection
                    showSnackbar("No Internet connection")
                    return
                }

                response.error!!.errorCode == ErrorCodes.UNKNOWN_ERROR -> {
                    // Sign in failed
                    //Unknown Error
                    showSnackbar("Unknown error")
                    return
                }

                else -> {
                    showSnackbar("Unknown Response")
                }

            }
        }
    }

    fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content),
                message, Snackbar.LENGTH_SHORT).show()
    }
}