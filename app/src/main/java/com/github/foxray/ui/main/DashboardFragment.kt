package com.github.foxray.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.github.foxray.R
import com.github.foxray.bg.BoxService
import com.github.foxray.constant.Status
import com.github.foxray.database.Profile
import com.github.foxray.database.ProfileManager
import com.github.foxray.database.TypedProfile
import com.github.foxray.databinding.FragmentDashboardBinding
import com.github.foxray.ktx.errorDialogBuilder
import com.github.foxray.ui.MainActivity
import com.github.foxray.ui.dashboard.GroupsFragment
import com.github.foxray.ui.dashboard.OverviewFragment
import com.github.foxray.utils.HTTPClient
import io.nekohasekai.libbox.Libbox
import ir.heydarii.androidloadingfragment.LoadingFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        onCreate()

        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        binding.dashboardPager.adapter = Adapter(this)
        binding.dashboardPager.offscreenPageLimit = Page.values().size
        TabLayoutMediator(binding.dashboardTabLayout, binding.dashboardPager) { tab, position ->
            tab.setText(Page.values()[position].titleRes)
        }.attach()
        activity.serviceStatus.observe(viewLifecycleOwner) {
            when (it) {
                Status.Stopped -> {
                    //binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    //binding.fab.show()
                    binding.fab.reset()
                    binding.notConnect.text = "Not Connected"
                    binding.buttonName.text = "CONNECT"
                    binding.buttonName.isVisible =true
                    disablePager()
                }

                Status.Starting -> {
                    //binding.fab.hide()
                    binding.notConnect.text = "Connecting..."
                    binding.buttonName.isVisible = false
                }

                Status.Started -> {
                    //binding.fab.setImageResource(R.drawable.ic_stop_24)
                    //binding.fab.show()
                    binding.fab.doResult(true)
                    binding.notConnect.text = "Connected"
                    binding.buttonName.text = "DISCONNECT"
                    binding.buttonName.isVisible = true
                    enablePager()
                }

                Status.Stopping -> {
                    //binding.fab.hide()
                    binding.fab.startLoading()
                    binding.notConnect.text = "Disconnecting..."
                    binding.buttonName.isVisible = false
                    disablePager()
                }

                else -> {}
            }
        }
        binding.fab.setOnClickListener {
            when (activity.serviceStatus.value) {
                Status.Stopped -> {
                    activity.startService()
                }

                Status.Started -> {
                    BoxService.stop()
                }

                else -> {}
            }
        }
        binding.settings.setOnClickListener {
            activity.findNavController(R.id.nav_host_fragment_activity_my).navigate(R.id.navigation_settings)
        }
    }

    private fun enablePager() {
        binding.dashboardTabLayout.isVisible = true
        binding.dashboardPager.isUserInputEnabled = true
    }

    private fun disablePager() {
        binding.dashboardTabLayout.isVisible = false
        binding.dashboardPager.isUserInputEnabled = false
        binding.dashboardPager.setCurrentItem(0, false)
    }

    enum class Page(@StringRes val titleRes: Int, val fragmentClass: Class<out Fragment>) {
        Overview(R.string.title_overview, OverviewFragment::class.java),
        Groups(R.string.title_groups, GroupsFragment::class.java);
    }

    class Adapter(parent: Fragment) : FragmentStateAdapter(parent) {
        override fun getItemCount(): Int {
            return Page.values().size
        }

        override fun createFragment(position: Int): Fragment {
            return Page.values()[position].fragmentClass.newInstance()
        }
    }

    private suspend fun addConfig(){
        showLoading()
        ProfileManager.delete(ProfileManager.list())
        val typedProfile = TypedProfile()
        val profile = Profile(name = "test", typed = typedProfile)
        profile.userOrder = 0L
        typedProfile.type = TypedProfile.Type.Remote
        val configDirectory = File(requireActivity().filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "${profile.userOrder}.json")
        val remoteURL = "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/singbox/sfasfi/mix.json"
        val content = HTTPClient().use { it.getString(remoteURL) }
        Libbox.checkConfig(content)
        configFile.writeText(content)
        typedProfile.path = configFile.path
        typedProfile.remoteURL = remoteURL
        typedProfile.lastUpdated = Date()
        typedProfile.autoUpdate = true
        typedProfile.autoUpdateInterval = 1
        ProfileManager.create(profile)
        val txt = binding.notConnect
        Thread.sleep(1_000)
        txt.text = "Synchronizing Servers..."
        Thread.sleep(1_000)
        txt.text = "Finding The Best Locations For You..."
        Thread.sleep(1_000)
        txt.text = "Found The Fastest Routes To Connect To"
        Thread.sleep(1_000)
        txt.text = "Not Connected - Press Connect Button"
        hideLoading()
    }
    private fun showLoading() {
        LoadingFragment.getInstance().show(requireActivity().supportFragmentManager, "TAG")
    }
    private fun hideLoading() {
        LoadingFragment.getInstance().dismissDialog()
    }
}