package com.example.protradingterminal

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- PRINT SHA-1 FOR FIREBASE SETUP (Fixed) ---
        printHashKey()

        // Find the NavHostFragment using supportFragmentManager
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        
        // Get the NavController from the NavHostFragment
        val navController = navHostFragment.navController
        
        // Setup BottomNavigationView with NavController
        val navView: BottomNavigationView = findViewById(R.id.bottom_nav)
        navView.setupWithNavController(navController)
    }

    private fun printHashKey() {
        try {
            val packageName = packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val signatures = signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners

                signatures?.let {
                    for (signature in it) {
                        val sha1 = getSHA1(signature.toByteArray())
                        Log.i("FIREBASE_SHA1", "Copy this SHA-1 to Firebase: $sha1")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                val signatures = info.signatures
                signatures?.let {
                    for (signature in it) {
                        val sha1 = getSHA1(signature.toByteArray())
                        Log.i("FIREBASE_SHA1", "Copy this SHA-1 to Firebase: $sha1")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FIREBASE_SHA1", "Error getting SHA-1", e)
        }
    }

    private fun getSHA1(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(bytes)
        val digest = md.digest()
        val hexString = StringBuilder()
        for (i in digest.indices) {
            val appendString = Integer.toHexString(0xFF and digest[i].toInt())
            if (appendString.length == 1) hexString.append("0")
            hexString.append(appendString.uppercase())
            if (i < digest.size - 1) hexString.append(":")
        }
        return hexString.toString()
    }
}