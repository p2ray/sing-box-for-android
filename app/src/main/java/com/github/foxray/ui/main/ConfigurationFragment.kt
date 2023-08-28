package com.github.foxray.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.foxray.R
import com.github.foxray.database.Profile
import com.github.foxray.database.ProfileManager
import com.github.foxray.databinding.FragmentConfigurationBinding
import com.github.foxray.databinding.ViewConfigutationItemBinding
import com.github.foxray.ktx.errorDialogBuilder
import com.github.foxray.ktx.shareProfile
import com.github.foxray.ui.profile.EditProfileActivity
import com.github.foxray.ui.profile.NewProfileActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigurationFragment : Fragment() {

    private var _adapter: Adapter? = null
    private var adapter: Adapter
        get() = _adapter as Adapter
        set(value) {
            _adapter = value
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        adapter = Adapter(lifecycleScope, binding)
        binding.profileList.also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return adapter.move(viewHolder.adapterPosition, target.adapterPosition)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                }
            }).attachToRecyclerView(it)
        }
        adapter.reload()
        binding.fab.setOnClickListener {
            startActivity(Intent(requireContext(), NewProfileActivity::class.java))
        }
        ProfileManager.registerCallback(this::updateProfiles)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        _adapter?.reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ProfileManager.unregisterCallback(this::updateProfiles)
        _adapter = null
    }

    private fun updateProfiles() {
        _adapter?.reload()
    }

    class Adapter(
        internal val scope: CoroutineScope,
        internal val parent: FragmentConfigurationBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        private var isMoving = false

        internal fun reload() {
            if (isMoving) return
            scope.launch(Dispatchers.IO) {
                items = ProfileManager.list().toMutableList()
                withContext(Dispatchers.Main) {
                    if (items.isEmpty()) {
                        parent.statusText.isVisible = true
                        parent.profileList.isVisible = false
                    } else if (parent.statusText.isVisible) {
                        parent.statusText.isVisible = false
                        parent.profileList.isVisible = true
                    }
                    notifyDataSetChanged()
                }
            }
        }

        internal fun move(from: Int, to: Int): Boolean {
            val first = items.getOrNull(from) ?: return false
            var previousOrder = first.userOrder
            val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                -1, to + 1 downTo from
            )
            val updated = mutableListOf<Profile>()
            for (i in range) {
                val next = items.getOrNull(i + step) ?: return false
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                updated.add(next)
            }
            first.userOrder = previousOrder
            updated.add(first)
            notifyItemMoved(from, to)
            isMoving = true
            GlobalScope.launch(Dispatchers.IO) {
                ProfileManager.update(updated)
                isMoving = false
            }
            return true
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewConfigutationItemBinding.inflate(
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

    class Holder(private val adapter: Adapter, private val binding: ViewConfigutationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            binding.root.setOnClickListener {
                val intent = Intent(binding.root.context, EditProfileActivity::class.java)
                intent.putExtra("profile_id", profile.id)
                it.context.startActivity(intent)
            }
            binding.moreButton.setOnClickListener { button ->
                val popup = PopupMenu(button.context, button)
                popup.setForceShowIcon(true)
                popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_share -> {
                            adapter.scope.launch(Dispatchers.IO) {
                                try {
                                    button.context.shareProfile(profile)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        button.context.errorDialogBuilder(e).show()
                                    }
                                }
                            }
                            true
                        }

                        R.id.action_delete -> {
                            adapter.items.remove(profile)
                            adapter.notifyItemRemoved(adapterPosition)
                            adapter.scope.launch(Dispatchers.IO) {
                                runCatching {
                                    ProfileManager.delete(profile)
                                }
                            }
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

}