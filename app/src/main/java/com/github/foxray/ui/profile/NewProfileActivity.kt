package com.github.foxray.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import com.github.foxray.R
import com.github.foxray.database.Profile
import com.github.foxray.database.ProfileManager
import com.github.foxray.database.TypedProfile
import com.github.foxray.databinding.ActivityAddProfileBinding
import com.github.foxray.ktx.addTextChangedListener
import com.github.foxray.ktx.errorDialogBuilder
import com.github.foxray.ktx.removeErrorIfNotEmpty
import com.github.foxray.ktx.showErrorIfEmpty
import com.github.foxray.ktx.startFilesForResult
import com.github.foxray.ktx.text
import com.github.foxray.ui.shared.AbstractActivity
import com.github.foxray.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Date

class NewProfileActivity : AbstractActivity() {
    enum class FileSource(val formatted: String) {
        CreateNew("Create New"),
        Import("Import");
    }

    private var _binding: ActivityAddProfileBinding? = null
    private val binding get() = _binding!!

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { fileURI ->
            if (fileURI != null) {
                binding.sourceURL.editText?.setText(fileURI.toString())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_new_profile)
        _binding = ActivityAddProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.name.removeErrorIfNotEmpty()
        binding.type.addTextChangedListener {
            when (it) {
                TypedProfile.Type.Local.name -> {
                    binding.localFields.isVisible = true
                    binding.remoteFields.isVisible = false
                }

                TypedProfile.Type.Remote.name -> {
                    binding.localFields.isVisible = false
                    binding.remoteFields.isVisible = true
                }
            }
        }
        binding.fileSourceMenu.addTextChangedListener {
            when (it) {
                FileSource.CreateNew.formatted -> {
                    binding.importFileButton.isVisible = false
                    binding.sourceURL.isVisible = false
                }

                FileSource.Import.formatted -> {
                    binding.importFileButton.isVisible = true
                    binding.sourceURL.isVisible = true
                }
            }
        }
        binding.importFileButton.setOnClickListener {
            startFilesForResult(importFile, "application/json")
        }
        binding.createProfile.setOnClickListener(this::createProfile)
        intent.getStringExtra("importName")?.also { importName ->
            intent.getStringExtra("importURL")?.also { importURL ->
                binding.name.editText?.setText(importName)
                binding.type.text = TypedProfile.Type.Remote.name
                binding.remoteURL.editText?.setText(importURL)
            }
        }
    }

    private fun createProfile(view: View) {
        if (binding.name.showErrorIfEmpty()) {
            return
        }
        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                when (binding.fileSourceMenu.text) {
                    FileSource.Import.formatted -> {
                        if (binding.sourceURL.showErrorIfEmpty()) {
                            return
                        }
                    }
                }
            }

            TypedProfile.Type.Remote.name -> {
                if (binding.remoteURL.showErrorIfEmpty()) {
                    return
                }
            }
        }
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                createProfile0()
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    binding.progressView.isVisible = false
                    errorDialogBuilder(e).show()
                }
            }
        }
    }

    private suspend fun createProfile0() {
        val typedProfile = TypedProfile()
        val profile = Profile(name = binding.name.text, typed = typedProfile)
        profile.userOrder = ProfileManager.nextOrder()

        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                typedProfile.type = TypedProfile.Type.Local
                val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
                val configFile = File(configDirectory, "${profile.userOrder}.json")
                when (binding.fileSourceMenu.text) {
                    FileSource.CreateNew.formatted -> {
                        configFile.writeText("{}")
                    }

                    FileSource.Import.formatted -> {
                        val sourceURL = binding.sourceURL.text
                        val content = if (sourceURL.startsWith("content://")) {
                            val inputStream =
                                contentResolver.openInputStream(Uri.parse(sourceURL)) as InputStream
                            inputStream.use { it.bufferedReader().readText() }
                        } else if (sourceURL.startsWith("file://")) {
                            File(sourceURL).readText()
                        } else if (sourceURL.startsWith("http://") || sourceURL.startsWith("https://")) {
                            HTTPClient().use { it.getString(sourceURL) }
                        } else {
                            error("unsupported source: $sourceURL")
                        }

                        Libbox.checkConfig(content)
                        configFile.writeText(content)
                    }
                }
                typedProfile.path = configFile.path
            }

            TypedProfile.Type.Remote.name -> {
                typedProfile.type = TypedProfile.Type.Remote
                val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
                val configFile = File(configDirectory, "${profile.userOrder}.json")
                val remoteURL = binding.remoteURL.text
                val content = HTTPClient().use { it.getString(remoteURL) }
                Libbox.checkConfig(content)
                configFile.writeText(content)
                typedProfile.path = configFile.path
                typedProfile.remoteURL = remoteURL
                typedProfile.lastUpdated = Date()
            }
        }
        ProfileManager.create(profile)
        withContext(Dispatchers.Main) {
            binding.progressView.isVisible = false
            finish()
        }
    }

}