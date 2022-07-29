package com.example.videoeditor

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.videoeditor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pickVideoButton.setOnClickListener(pickVideo())
        binding.clearCacheButton.setOnClickListener(clearAllCache())
    }

    private fun pickVideo() = View.OnClickListener {
        storagePermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            when {
                granted -> {
                    chooseVideoLauncher.launch("video/*")
                }
                !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> showPermissionDeniedDialog()
                else -> showRationaleDialog()
            }
        }

    private val chooseVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val intent = PlayerActivity.newPlayerIntent(this@MainActivity, it)
                startActivity(intent)
            }
        }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.permission_denied_dialog_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                val uri = Uri.fromParts("package", packageName, null)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                ContextCompat.startActivity(this, intent, null)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.storage_permission_rationale)
            .setPositiveButton(R.string.allow) { _, _ ->
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun clearAllCache() = View.OnClickListener {
        if (cacheDir.deleteRecursively())
            Toast.makeText(this, "Кэш успешно очищен!", Toast.LENGTH_SHORT).show()
        else
            Toast.makeText(this, "Не удалось очистить кэш", Toast.LENGTH_SHORT).show()
    }
}