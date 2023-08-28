package com.github.foxray.ui.profileoverride

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.github.foxray.R
import com.github.foxray.constant.PerAppProxyUpdateType
import com.github.foxray.database.Settings
import com.github.foxray.databinding.ActivityConfigOverrideBinding
import com.github.foxray.ktx.addTextChangedListener
import com.github.foxray.ktx.setSimpleItems
import com.github.foxray.ktx.text
import com.github.foxray.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileOverrideActivity : AbstractActivity() {

    private lateinit var binding: ActivityConfigOverrideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.title_profile_override)
        binding = ActivityConfigOverrideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.switchPerAppProxy.isChecked = Settings.perAppProxyEnabled
        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            Settings.perAppProxyEnabled = isChecked
            binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
            binding.configureAppListButton.isEnabled = isChecked
        }
        binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
        binding.configureAppListButton.isEnabled = binding.switchPerAppProxy.isChecked

        binding.perAppProxyUpdateOnChange.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                Settings.perAppProxyUpdateOnChange = PerAppProxyUpdateType.valueOf(it).value()
            }
        }

        binding.configureAppListButton.setOnClickListener {
            startActivity(Intent(this, PerAppProxyActivity::class.java))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val perAppUpdateOnChange = Settings.perAppProxyUpdateOnChange
        withContext(Dispatchers.Main) {
            binding.perAppProxyUpdateOnChange.text =
                PerAppProxyUpdateType.valueOf(perAppUpdateOnChange).name
            binding.perAppProxyUpdateOnChange.setSimpleItems(R.array.per_app_proxy_update_on_change_value)
        }
    }
}