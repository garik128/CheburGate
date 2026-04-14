package com.android.cheburgate.ui.servers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.cheburgate.R
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.Server
import com.android.cheburgate.databinding.ActivityAddServerBinding
import com.android.cheburgate.databinding.FragmentAddManualBinding
import com.android.cheburgate.databinding.FragmentAddViaLinkBinding
import com.android.cheburgate.util.LinkParser
import com.android.cheburgate.util.pasteFromClipboard
import com.android.cheburgate.util.showToast
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class AddServerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER_ID = "serverId"
    }

    private lateinit var binding: ActivityAddServerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
        val isEditMode = serverId > 0L

        if (isEditMode) {
            supportActionBar?.title = getString(R.string.edit_server)
            binding.tabLayout.visibility = android.view.View.GONE
        }

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> AddViaLinkFragment()
                else -> if (isEditMode) AddManualFragment.newInstance(serverId) else AddManualFragment()
            }
        }

        if (isEditMode) {
            binding.viewPager.setCurrentItem(1, false)
            binding.viewPager.isUserInputEnabled = false
        } else {
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.via_link)
                    else -> getString(R.string.manually)
                }
            }.attach()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class AddViaLinkFragment : Fragment() {

    private var _binding: FragmentAddViaLinkBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentAddViaLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnPasteFromClipboard.setOnClickListener {
            val text = requireContext().pasteFromClipboard() ?: ""
            binding.etLinks.setText(text)
        }

        binding.btnAdd.setOnClickListener {
            val text = binding.etLinks.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val servers = LinkParser.parseMultiple(text)
            if (servers.isEmpty()) {
                requireContext().showToast(getString(R.string.wrong_link_format))
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = AppDatabase.getInstance(requireContext())
                servers.forEach { server -> db.serverDao().insert(server) }
                requireActivity().finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AddManualFragment : Fragment() {

    companion object {
        private const val ARG_SERVER_ID = "serverId"

        fun newInstance(serverId: Long) = AddManualFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SERVER_ID, serverId) }
        }
    }

    private var _binding: FragmentAddManualBinding? = null
    private val binding get() = _binding!!
    private var existingServer: Server? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentAddManualBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val serverId = arguments?.getLong(ARG_SERVER_ID, -1L) ?: -1L
        val isEditMode = serverId > 0L

        val protocols = listOf("VLESS", "Hysteria2")
        val protocolAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, protocols)
        (binding.spinnerProtocol as? AutoCompleteTextView)?.apply {
            setAdapter(protocolAdapter)
            setText("VLESS", false)
            setOnItemClickListener { _, _, position, _ ->
                binding.layoutVless.visibility = if (position == 0) View.VISIBLE else View.GONE
                binding.layoutHy2.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
        }

        if (isEditMode) {
            binding.btnAddManual.setText(R.string.save)
            lifecycleScope.launch {
                val server = AppDatabase.getInstance(requireContext()).serverDao().getById(serverId)
                if (server != null) {
                    existingServer = server
                    fillForm(server)
                }
            }
        }

        binding.btnAddManual.setOnClickListener {
            val protocol = binding.spinnerProtocol.text.toString().lowercase()
            val name = binding.etName.text.toString().trim()
            val address = binding.etAddress.text.toString().trim()
            val portStr = binding.etPort.text.toString().trim()

            if (name.isEmpty() || address.isEmpty() || portStr.isEmpty()) {
                requireContext().showToast("Заполните все обязательные поля")
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull() ?: run {
                requireContext().showToast("Неверный порт")
                return@setOnClickListener
            }

            // В режиме редактирования копируем существующий сервер, чтобы не потерять
            // поля, которых нет в форме (transport, flow, alpn, xhttpHost и др.)
            val server = if (isEditMode && existingServer != null) {
                val base = existingServer!!
                when {
                    protocol.contains("vless") -> base.copy(
                        name = name,
                        address = address,
                        port = port,
                        uuid = binding.etUuid.text.toString().trim(),
                        security = binding.etSecurity.text.toString().trim().ifEmpty { "none" },
                        publicKey = binding.etPublicKey.text.toString().trim().ifEmpty { null },
                        shortId = binding.etShortId.text.toString().trim().ifEmpty { null },
                        serverName = binding.etSniVless.text.toString().trim().ifEmpty { null }
                    )
                    else -> base.copy(
                        name = name,
                        address = address,
                        port = port,
                        password = binding.etPassword.text.toString().trim(),
                        serverName = binding.etSniHy2.text.toString().trim().ifEmpty { null },
                        insecure = binding.switchInsecure.isChecked
                    )
                }
            } else {
                when {
                    protocol.contains("vless") -> Server(
                        name = name,
                        protocol = "vless",
                        address = address,
                        port = port,
                        uuid = binding.etUuid.text.toString().trim(),
                        password = null,
                        flow = null,
                        transport = "tcp",
                        security = binding.etSecurity.text.toString().trim().ifEmpty { "none" },
                        publicKey = binding.etPublicKey.text.toString().trim().ifEmpty { null },
                        shortId = binding.etShortId.text.toString().trim().ifEmpty { null },
                        serverName = binding.etSniVless.text.toString().trim().ifEmpty { null },
                        fingerprint = "chrome",
                        path = null
                    )
                    else -> Server(
                        name = name,
                        protocol = "hysteria2",
                        address = address,
                        port = port,
                        uuid = null,
                        password = binding.etPassword.text.toString().trim(),
                        flow = null,
                        transport = null,
                        security = "tls",
                        publicKey = null,
                        shortId = null,
                        serverName = binding.etSniHy2.text.toString().trim().ifEmpty { null },
                        fingerprint = null,
                        path = null,
                        insecure = binding.switchInsecure.isChecked
                    )
                }
            }

            lifecycleScope.launch {
                val db = AppDatabase.getInstance(requireContext())
                if (isEditMode && existingServer != null) {
                    db.serverDao().update(server)
                } else {
                    db.serverDao().insert(server)
                }
                requireActivity().finish()
            }
        }
    }

    private fun fillForm(server: Server) {
        binding.etName.setText(server.name)
        binding.etAddress.setText(server.address)
        binding.etPort.setText(server.port.toString())

        val isVless = server.protocol.lowercase() == "vless"
        (binding.spinnerProtocol as? AutoCompleteTextView)?.setText(
            if (isVless) "VLESS" else "Hysteria2", false
        )
        binding.layoutVless.visibility = if (isVless) View.VISIBLE else View.GONE
        binding.layoutHy2.visibility = if (!isVless) View.VISIBLE else View.GONE

        if (isVless) {
            binding.etUuid.setText(server.uuid ?: "")
            binding.etSecurity.setText(server.security ?: "")
            binding.etPublicKey.setText(server.publicKey ?: "")
            binding.etShortId.setText(server.shortId ?: "")
            binding.etSniVless.setText(server.serverName ?: "")
        } else {
            binding.etPassword.setText(server.password ?: "")
            binding.etSniHy2.setText(server.serverName ?: "")
            binding.switchInsecure.isChecked = server.insecure
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
