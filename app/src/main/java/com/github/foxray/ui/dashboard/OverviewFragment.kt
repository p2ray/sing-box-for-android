package com.github.foxray.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.StatusMessage
import com.github.foxray.R
import com.github.foxray.bg.BoxService
import com.github.foxray.constant.Status
import com.github.foxray.database.Profile
import com.github.foxray.database.ProfileManager
import com.github.foxray.database.Settings
import com.github.foxray.databinding.FragmentDashboardOverviewBinding
import com.github.foxray.databinding.ViewProfileItemBinding
import com.github.foxray.ktx.errorDialogBuilder
import com.github.foxray.ui.MainActivity
import com.github.foxray.utils.CommandClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewFragment : Fragment(), CommandClient.Handler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardOverviewBinding? = null
    private val binding get() = _binding!!
    private val commandClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Status, this)

    private var _adapter: Adapter? = null
    private val adapter get() = _adapter!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardOverviewBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        binding.profileList.adapter = Adapter(lifecycleScope, binding).apply {
            _adapter = this
            reload()
        }
        binding.profileList.layoutManager = LinearLayoutManager(requireContext())
        val divider = MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        divider.isLastItemDecorated = false
        binding.profileList.addItemDecoration(divider)
        activity.serviceStatus.observe(viewLifecycleOwner) {
            binding.statusContainer.isVisible = it == Status.Starting || it == Status.Started
            if (it == Status.Started) {
                commandClient.connect()
            }
        }
        ProfileManager.registerCallback(this::updateProfiles)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _adapter = null
        _binding = null
        commandClient.disconnect()
        ProfileManager.unregisterCallback(this::updateProfiles)
    }

    private fun updateProfiles() {
        _adapter?.reload()
    }

    override fun onConnected() {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = getString(R.string.loading)
            binding.goroutinesText.text = getString(R.string.loading)
        }
    }

    override fun onDisconnected() {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = getString(R.string.loading)
            binding.goroutinesText.text = getString(R.string.loading)
        }
    }

    override fun updateStatus(status: StatusMessage) {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = Libbox.formatBytes(status.memory)
            binding.goroutinesText.text = status.goroutines.toString()
            val trafficAvailable = status.trafficAvailable
            binding.trafficContainer.isVisible = trafficAvailable
            if (trafficAvailable) {
                binding.inboundConnectionsText.text = status.connectionsIn.toString()
                binding.outboundConnectionsText.text = status.connectionsOut.toString()
                binding.uplinkText.text = Libbox.formatBytes(status.uplink) + "/s"
                binding.downlinkText.text = Libbox.formatBytes(status.downlink) + "/s"
                binding.uplinkTotalText.text = Libbox.formatBytes(status.uplinkTotal)
                binding.downlinkTotalText.text = Libbox.formatBytes(status.downlinkTotal)
            }
        }
    }

    class Adapter(
        internal val scope: CoroutineScope,
        private val parent: FragmentDashboardOverviewBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null
        internal fun reload() {
            scope.launch(Dispatchers.IO) {
                items = ProfileManager.list().toMutableList()
                if (items.isNotEmpty()) {
                    selectedProfileID = Settings.selectedProfile
                    for ((index, profile) in items.withIndex()) {
                        if (profile.id == selectedProfileID) {
                            lastSelectedIndex = index
                            break
                        }
                    }
                    if (lastSelectedIndex == null) {
                        lastSelectedIndex = 0
                        selectedProfileID = items[0].id
                        Settings.selectedProfile = selectedProfileID
                    }
                }
                withContext(Dispatchers.Main) {

                    parent.container.isVisible = items.isNotEmpty()
                    parent.profileCard.isVisible = false

                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewProfileItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

    }

    class Holder(
        private val adapter: Adapter,
        private val binding: ViewProfileItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            binding.profileSelected.setOnCheckedChangeListener(null)
            binding.profileSelected.isChecked = profile.id == adapter.selectedProfileID
            binding.profileSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.selectedProfileID = profile.id
                    adapter.lastSelectedIndex?.let { index ->
                        adapter.notifyItemChanged(index)
                    }
                    adapter.lastSelectedIndex = adapterPosition
                    adapter.scope.launch(Dispatchers.IO) {
                        switchProfile(profile)
                    }
                }
            }
            binding.root.setOnClickListener {
                binding.profileSelected.toggle()
            }
        }

        private suspend fun switchProfile(profile: Profile) {
            Settings.selectedProfile = profile.id
            val mainActivity = (binding.root.context as? MainActivity) ?: return
            val started = mainActivity.serviceStatus.value == Status.Started
            if (!started) {
                return
            }
            val restart = Settings.rebuildServiceMode()
            if (restart) {
                mainActivity.reconnect()
                BoxService.stop()
                delay(200)
                mainActivity.startService()
                return
            }
            runCatching {
                Libbox.newStandaloneCommandClient().serviceReload()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    mainActivity.errorDialogBuilder(it).show()
                }
            }
        }
    }

}