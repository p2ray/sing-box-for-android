package com.github.foxray.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import com.github.foxray.R
import com.github.foxray.bg.UpdateProfileWork
import com.github.foxray.constant.EnabledType
import com.github.foxray.database.Profile
import com.github.foxray.database.ProfileManager
import com.github.foxray.database.TypedProfile
import com.github.foxray.databinding.ActivityEditProfileBinding
import com.github.foxray.ktx.addTextChangedListener
import com.github.foxray.ktx.errorDialogBuilder
import com.github.foxray.ktx.setSimpleItems
import com.github.foxray.ktx.shareProfile
import com.github.foxray.ktx.text
import com.github.foxray.ui.shared.AbstractActivity
import com.github.foxray.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class EditProfileActivity : AbstractActivity() {

    private var _binding: ActivityEditProfileBinding? = null
    private val binding get() = _binding!!
    private var _profile: Profile? = null
    private val profile get() = _profile!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_edit_profile)
        _binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                loadProfile()
            }.onFailure {
                errorDialogBuilder(it)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .show()
            }
        }
    }

    private suspend fun loadProfile() {
        delay(200L)

        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) error("invalid arguments")
        _profile = ProfileManager.get(profileId) ?: error("invalid arguments")
        withContext(Dispatchers.Main) {
            binding.name.text = profile.name
            binding.name.addTextChangedListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        profile.name = it
                        ProfileManager.update(profile)
                    } catch (e: Exception) {
                        errorDialogBuilder(e).show()
                    }
                }
            }
            binding.type.text = profile.typed.type.name
            binding.editButton.setOnClickListener {
                startActivity(
                    Intent(
                        this@EditProfileActivity,
                        EditProfileContentActivity::class.java
                    ).apply {
                        putExtra("profile_id", profile.id)
                    })
            }
            when (profile.typed.type) {
                TypedProfile.Type.Local -> {
                    binding.editButton.isVisible = true
                    binding.remoteFields.isVisible = false
                }

                TypedProfile.Type.Remote -> {
                    binding.editButton.isVisible = false
                    binding.remoteFields.isVisible = true
                    binding.remoteURL.text = profile.typed.remoteURL
                    binding.lastUpdated.text =
                        DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                    binding.autoUpdate.text = EnabledType.from(profile.typed.autoUpdate).name
                    binding.autoUpdate.setSimpleItems(R.array.enabled)
                    binding.autoUpdateInterval.isVisible = profile.typed.autoUpdate
                    binding.autoUpdateInterval.text = profile.typed.autoUpdateInterval.toString()
                }
            }
            binding.remoteURL.addTextChangedListener(this@EditProfileActivity::updateRemoteURL)
            binding.autoUpdate.addTextChangedListener(this@EditProfileActivity::updateAutoUpdate)
            binding.autoUpdateInterval.addTextChangedListener(this@EditProfileActivity::updateAutoUpdateInterval)
            binding.updateButton.setOnClickListener(this@EditProfileActivity::updateProfile)
            binding.checkButton.setOnClickListener(this@EditProfileActivity::checkProfile)
            binding.shareButton.setOnClickListener(this@EditProfileActivity::shareProfile)
            binding.profileLayout.isVisible = true
            binding.progressView.isVisible = false
        }
    }


    private fun updateRemoteURL(newValue: String) {
        profile.typed.remoteURL = newValue
        updateProfile()
    }

    private fun updateAutoUpdate(newValue: String) {
        val boolValue = EnabledType.valueOf(newValue).boolValue
        if (profile.typed.autoUpdate == boolValue) {
            return
        }
        binding.autoUpdateInterval.isVisible = boolValue
        profile.typed.autoUpdate = boolValue
        if (boolValue) {
            lifecycleScope.launch(Dispatchers.IO) {
                UpdateProfileWork.reconfigureUpdater()
            }
        }
        updateProfile()
    }

    private fun updateAutoUpdateInterval(newValue: String) {
        if (newValue.isBlank()) {
            binding.autoUpdateInterval.error = getString(R.string.profile_input_required)
            return
        }
        val intValue = try {
            newValue.toInt()
        } catch (e: Exception) {
            binding.autoUpdateInterval.error = e.localizedMessage
            return
        }
        if (intValue < 15) {
            binding.autoUpdateInterval.error =
                getString(R.string.profile_auto_update_interval_minimum_hint)
            return
        }
        binding.autoUpdateInterval.error = null
        profile.typed.autoUpdateInterval = intValue
        updateProfile()
    }

    private fun updateProfile() {
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(200)
            try {
                ProfileManager.update(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
            withContext(Dispatchers.Main) {
                binding.progressView.isVisible = false
            }
        }
    }

    private fun updateProfile(view: View) {
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)
                File(profile.typed.path).writeText(content)
                profile.typed.lastUpdated = Date()
                ProfileManager.update(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
            withContext(Dispatchers.Main) {
                binding.lastUpdated.text =
                    DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                binding.progressView.isVisible = false
            }
        }
    }

    private fun checkProfile(button: View) {
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(200)
            try {
                Libbox.checkConfig(File(profile.typed.path).readText())
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
            withContext(Dispatchers.Main) {
                binding.progressView.isVisible = false
            }
        }
    }

    private fun shareProfile(button: View) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                shareProfile(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
        }
    }

}