package im.angry.openeuicc.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.service.EuiccChannelManagerService
import im.angry.openeuicc.service.EuiccChannelManagerService.Companion.waitDone
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.typeblog.lpac_jni.LocalProfileInfo
import net.typeblog.lpac_jni.ProfileClass
import java.util.Locale

open class EuiccManagementFragment : Fragment(), EuiccProfilesChangedListener,
    EuiccChannelFragmentMarker {
    companion object {
        const val TAG = "EuiccManagementFragment"

        fun newInstance(
            slotId: Int,
            portId: Int,
            seId: EuiccChannel.SecureElementId
        ): EuiccManagementFragment =
            newInstanceEuicc(EuiccManagementFragment::class.java, slotId, portId, seId)
    }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var profileList: RecyclerView
    private var logicalSlotId: Int = -1
    private lateinit var eid: String
    private var enabledProfile: LocalProfileInfo? = null

    private val adapter = EuiccProfileAdapter()
    private var allProfiles: List<LocalProfileInfo> = emptyList()
    private var enabledOnly = false
    private var refreshJob: Job? = null
    private var eidRevealed = false
    private lateinit var filteredEmptyView: View
    private val previewMode: Boolean get() = arguments?.getBoolean("lumaPreview") == true

    private fun previewNotice() {
        Toast.makeText(requireContext(), R.string.luma_preview_only, Toast.LENGTH_SHORT).show()
    }

    private fun operationAllowed(): Boolean {
        if (previewMode) { previewNotice(); return false }
        return !invalid && !swipeRefresh.isRefreshing && logicalSlotId != -1
    }

    private fun launchDownload() {
        if (!operationAllowed()) return
        startActivity(DownloadWizardActivity.newIntent(requireContext(), logicalSlotId, seId))
    }

    private fun openCardActivity(clazz: Class<*>) {
        if (!operationAllowed()) return
        startActivity(Intent(requireContext(), clazz).apply {
            putExtra("logicalSlotId", logicalSlotId)
            putExtra("seId", seId)
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilter() {
        adapter.profiles = if (enabledOnly) allProfiles.filter { it.isEnabled } else allProfiles
        adapter.notifyDataSetChanged()
    }

    private fun requestEnable(profile: LocalProfileInfo, canEnable: Boolean) {
        if (!operationAllowed()) return
        if (!canEnable) {
            Toast.makeText(requireContext(), R.string.toast_profile_enable_cross_class, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.luma_switch_title, profile.displayName))
            .setMessage(R.string.luma_switch_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.luma_enable) { _, _ ->
                // Another task may have started while the confirmation was visible.
                if (operationAllowed()) enableOrDisableProfile(profile.iccid, true)
            }.show()
    }

    // Marker for when this fragment might enter an invalid state
    // e.g. after a failed enable / disable operation
    private var invalid = false

    // Subscribe to settings we care about outside of coroutine contexts while initializing
    // This gives us access to the "latest" state without having to launch coroutines
    private lateinit var disableSafeguardFlow: StateFlow<Boolean>

    private lateinit var unfilteredProfileListFlow: StateFlow<Boolean>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabledOnly = savedInstanceState?.getBoolean("lumaEnabledOnly") ?: false
        setHasOptionsMenu(!previewMode)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_euicc, container, false)

        swipeRefresh = view.requireViewById(R.id.swipe_refresh)
        fab = view.requireViewById(R.id.fab)
        profileList = view.requireViewById(R.id.profile_list)
        profileList.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        filteredEmptyView = inflater.inflate(R.layout.footer_no_profile, profileList, false).apply {
            requireViewById<TextView>(R.id.luma_empty_heading).setText(R.string.luma_no_active)
            requireViewById<TextView>(R.id.luma_empty_description).setText(R.string.luma_no_active_hint)
        }
        // The full-width Add eSIM button is part of the scrolling dashboard.
        fab.hide()

        val origFabMarginRight = (fab.layoutParams as ViewGroup.MarginLayoutParams).rightMargin
        val origFabMarginBottom = (fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        setupRootViewSystemBarInsets(
            view, arrayOf(
                mainViewPaddingInsetHandler(profileList),
                { insets ->
                    fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        rightMargin = origFabMarginRight + insets.right
                        bottomMargin = origFabMarginBottom + insets.bottom
                    }
                }
            ))


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh.setOnRefreshListener { refresh() }
        profileList.adapter = adapter

        fab.setOnClickListener { launchDownload() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("lumaEnabledOnly", enabledOnly)
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        profileList.adapter = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onEuiccProfilesChanged() {
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_euicc, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.show_notifications).isVisible =
            logicalSlotId != -1
        menu.findItem(R.id.euicc_info).isVisible =
            logicalSlotId != -1
        menu.findItem(R.id.euicc_memory_reset).isVisible =
            enabledProfile == null
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.show_notifications -> {
            Intent(requireContext(), NotificationsActivity::class.java).apply {
                putExtra("logicalSlotId", logicalSlotId)
                putExtra("seId", seId)
                startActivity(this)
            }
            true
        }

        R.id.euicc_info -> {
            Intent(requireContext(), EuiccInfoActivity::class.java).apply {
                putExtra("logicalSlotId", logicalSlotId)
                putExtra("seId", seId)
                startActivity(this)
            }
            true
        }

        R.id.euicc_memory_reset -> {
            EuiccMemoryResetFragment.newInstance(slotId, portId, seId, eid)
                .show(childFragmentManager, EuiccMemoryResetFragment.TAG)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    protected open suspend fun onCreateFooterViews(
        parent: ViewGroup,
        profiles: List<LocalProfileInfo>
    ): List<View> =
        if (profiles.isEmpty()) {
            val view = layoutInflater.inflate(R.layout.footer_no_profile, parent, false)
            listOf(view)
        } else {
            listOf()
        }

    private fun refresh() {
        if (invalid || refreshJob?.isActive == true || view == null) return
        swipeRefresh.isRefreshing = true
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                doRefresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, R.string.luma_read_failed, Toast.LENGTH_LONG).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    protected open suspend fun doRefresh() {
        if (previewMode) {
            disableSafeguardFlow = MutableStateFlow(false)
            unfilteredProfileListFlow = MutableStateFlow(false)
            logicalSlotId = 0
            eid = "89000000000000000000000000000000"
            allProfiles = listOf(
                previewProfile("8900000000000000001", R.string.luma_preview_daily, "Example Mobile", true, "64F000"),
                previewProfile("8900000000000000002", R.string.luma_preview_travel, "Example Travel", false, "44F001"),
                previewProfile("8900000000000000003", R.string.luma_preview_work, "Example Connect", false, "32F451")
            )
            enabledProfile = allProfiles.enabled
            adapter.footerViews = emptyList()
            applyFilter()
            return
        }
        ensureEuiccChannelManager()
        euiccChannelManagerService.waitForForegroundTask()

        if (!::disableSafeguardFlow.isInitialized) {
            disableSafeguardFlow =
                preferenceRepository.disableSafeguardFlow.stateIn(lifecycleScope)
        }
        if (!::unfilteredProfileListFlow.isInitialized) {
            unfilteredProfileListFlow =
                preferenceRepository.unfilteredProfileListFlow.stateIn(lifecycleScope)
        }

        val profiles = withEuiccChannel { channel ->
            logicalSlotId = channel.logicalSlotId
            eid = channel.lpa.eID
            enabledProfile = channel.lpa.profiles.enabled
            euiccChannelManager.notifyEuiccProfilesChanged(channel.logicalSlotId)
            if (unfilteredProfileListFlow.value)
                channel.lpa.profiles
            else
                channel.lpa.profiles.operational
        }

        withContext(Dispatchers.Main) {
            allProfiles = profiles
            adapter.footerViews = onCreateFooterViews(profileList, profiles)
            applyFilter()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun previewProfile(iccid: String, name: Int, provider: String, enabled: Boolean, operatorId: String) =
        LocalProfileInfo(iccid,
            if (enabled) LocalProfileInfo.State.Enabled else LocalProfileInfo.State.Disabled,
            getString(name), "", provider, "", ProfileClass.Operational, operatorId)

    private fun profileRegionDescription(countryCode: String?): String =
        if (countryCode == null) getString(R.string.luma_profile_region_unknown) else
            getString(R.string.luma_profile_region,
                Locale("", countryCode).getDisplayCountry(resources.configuration.locales[0]))

    private suspend fun showSwitchFailureText() = withContext(Dispatchers.Main) {
        val resId = R.string.toast_profile_enable_failed
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
    }

    /** Called by privileged builds after a profile has been enabled and the modem reconnects. */
    protected open suspend fun onProfileSwitchSucceeded(logicalSlotId: Int) = Unit

    private fun enableOrDisableProfile(iccid: String, enable: Boolean) {
        swipeRefresh.isRefreshing = true
        fab.isEnabled = false

        lifecycleScope.launch {
            ensureEuiccChannelManager()
            euiccChannelManagerService.waitForForegroundTask()

            val err = euiccChannelManagerService
                .launchProfileSwitchTask(
                    slotId, portId, seId, iccid, enable,
                    reconnectTimeoutMillis = 30 * 1000
                )
                .stateFlow.waitDone()

            when (err) {
                null -> if (enable) onProfileSwitchSucceeded(logicalSlotId)
                is EuiccChannelManagerService.SwitchingProfilesRefreshException -> {
                    // This is only really fatal for internal eSIMs
                    if (!isUsb) {
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                                .setMessage(R.string.profile_switch_did_not_refresh)
                                .setPositiveButton(android.R.string.ok) { _, _ -> requireActivity().finish() }
                                .setOnDismissListener { _ -> requireActivity().finish() }
                                .show()
                        }
                    }
                }

                is TimeoutCancellationException -> {
                    withContext(Dispatchers.Main) {
                        // Prevent this Fragment from being used again
                        invalid = true
                        // Timed out waiting for SIM to come back online, we can no longer assume that the LPA is still valid
                        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                            .setMessage(appContainer.customizableTextProvider.profileSwitchingTimeoutMessage)
                            .setPositiveButton(android.R.string.ok) { _, _ -> requireActivity().finish() }
                            .setOnDismissListener { _ -> requireActivity().finish() }
                            .show()
                    }
                }

                else -> showSwitchFailureText()
            }

            refresh()
            fab.isEnabled = true
        }
    }

    protected open fun populatePopupWithProfileActions(
        popup: PopupMenu,
        profile: LocalProfileInfo
    ) {
        popup.inflate(R.menu.profile_options)
        if (!profile.isEnabled) return
        popup.menu.findItem(R.id.enable).isVisible = false
        popup.menu.findItem(R.id.delete).isVisible = false

        // We hide the disable option by default to avoid "bricking" some cards that won't get
        // recognized again by the phone's modem. However, we don't have that worry if we are
        // accessing it through a USB card reader, or when the user explicitly opted in
        if (!isUsb && !disableSafeguardFlow.value) return
        popup.menu.findItem(R.id.disable).isVisible = true
    }

    sealed class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        enum class Type(val value: Int) {
            PROFILE(0),
            FOOTER(1),
            HEADER(2);

            companion object {
                fun fromInt(value: Int) =
                    entries.first { it.value == value }
            }
        }
    }

    inner class HeaderViewHolder(private val root: View) : ViewHolder(root) {
        private val eidView = root.requireViewById<TextView>(R.id.luma_eid)
        private val filters = root.requireViewById<ChipGroup>(R.id.luma_filters)

        init {
            root.requireViewById<View>(R.id.luma_download).setOnClickListener { launchDownload() }
            root.requireViewById<View>(R.id.luma_card_info).setOnClickListener { openCardActivity(EuiccInfoActivity::class.java) }
            root.requireViewById<View>(R.id.luma_notifications).setOnClickListener { openCardActivity(NotificationsActivity::class.java) }
            eidView.setOnClickListener { eidRevealed = !eidRevealed; updateEid() }
            eidView.setOnLongClickListener {
                requireContext().getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("EID", eid))
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                    Toast.makeText(context, R.string.toast_eid_copied, Toast.LENGTH_SHORT).show()
                true
            }
        }

        private fun updateEid() {
            if (!this@EuiccManagementFragment::eid.isInitialized) return
            eidView.text = "EID  " + if (eidRevealed) eid else "•••• •••• •••• " + eid.takeLast(4)
        }

        fun bind() {
            root.requireViewById<TextView>(R.id.luma_device_name).text = if (isUsb)
                getString(R.string.luma_device_usb) else getString(R.string.luma_device, logicalSlotId + 1)
            root.requireViewById<TextView>(R.id.luma_total_count).text = getString(R.string.luma_profile_count, allProfiles.size)
            root.requireViewById<TextView>(R.id.luma_enabled_count).text = getString(R.string.luma_active_count, allProfiles.count { it.isEnabled })
            updateEid()
            filters.setOnCheckedStateChangeListener(null)
            filters.check(if (enabledOnly) R.id.luma_filter_active else R.id.luma_filter_all)
            filters.setOnCheckedStateChangeListener { _, ids ->
                val next = R.id.luma_filter_active in ids
                if (enabledOnly != next) {
                    enabledOnly = next
                    // RecyclerView may be binding this holder when restoring state.
                    profileList.post { if (view != null) applyFilter() }
                }
            }
        }
    }

    inner class FooterViewHolder : ViewHolder(FrameLayout(requireContext())) {
        init {
            itemView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun attach(view: View) {
            view.parent?.let { (it as ViewGroup).removeView(view) }
            (itemView as FrameLayout).addView(view)
        }

        fun detach() {
            (itemView as FrameLayout).removeAllViews()
        }
    }

    inner class ProfileViewHolder(private val root: View) : ViewHolder(root) {
        private val iccid: TextView = root.requireViewById(R.id.iccid)
        private val name: TextView = root.requireViewById(R.id.name)
        private val state: TextView = root.requireViewById(R.id.state)
        private val provider: TextView = root.requireViewById(R.id.provider)
        private val profileClassLabel: TextView = root.requireViewById(R.id.profile_class_label)
        private val profileClass: TextView = root.requireViewById(R.id.profile_class)
        private val profileMenu: ImageButton = root.requireViewById(R.id.profile_menu)
        private val profileSeqNumber: TextView = root.requireViewById(R.id.profile_sequence_number)
        private val card = root.requireViewById<MaterialCardView>(R.id.luma_profile_card)
        private val action = root.requireViewById<MaterialSwitch>(R.id.luma_profile_action)
        private val flag = root.requireViewById<ImageView>(R.id.luma_profile_flag)

        init {
            iccid.setOnClickListener {
                if (iccid.transformationMethod == null) {
                    iccid.transformationMethod = PasswordTransformationMethod.getInstance()
                } else {
                    iccid.transformationMethod = null
                }
            }

            iccid.setOnLongClickListener {
                requireContext().getSystemService(ClipboardManager::class.java)!!
                    .setPrimaryClip(ClipData.newPlainText("iccid", iccid.text))
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast
                    .makeText(requireContext(), R.string.toast_iccid_copied, Toast.LENGTH_SHORT)
                    .show()
                true
            }

            profileMenu.setOnClickListener { showOptionsMenu() }
            card.setOnClickListener {
                if (!swipeRefresh.isRefreshing && !invalid) showDetails()
            }
            action.setOnClickListener {
                // Only commit the visual state after the card reports success.
                action.isChecked = profile.isEnabled
                if (profile.isEnabled) showOptionsMenu() else requestEnable(profile, canEnable)
            }
        }

        private lateinit var profile: LocalProfileInfo
        private var canEnable: Boolean = false

        fun setProfile(profile: LocalProfileInfo) {
            this.profile = profile
            name.text = profile.displayName
            val countryCode = ProfileCountry.fromOperatorId(profile.profileOwnerMccMnc)
            flag.setImageResource(ProfileCountry.drawable(countryCode))
            flag.contentDescription = profileRegionDescription(countryCode)
            val color = ContextCompat.getColor(requireContext(), if (profile.isEnabled) R.color.luma_primary else R.color.luma_muted)
            state.setTextColor(color)
            state.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                if (profile.isEnabled) R.color.luma_badge else R.color.luma_disabled))
            card.strokeColor = ContextCompat.getColor(requireContext(),
                if (profile.isEnabled) R.color.luma_primary else R.color.luma_outline)
            action.isChecked = profile.isEnabled
            action.contentDescription = getString(if (profile.isEnabled) R.string.luma_using else R.string.luma_use)
            action.isEnabled = true

            state.setText(
                if (profile.isEnabled) {
                    R.string.profile_state_enabled
                } else {
                    R.string.luma_inactive
                }
            )
            provider.text = profile.providerName
            profileClassLabel.isVisible = unfilteredProfileListFlow.value
            profileClass.isVisible = unfilteredProfileListFlow.value
            profileClass.setText(
                when (profile.profileClass) {
                    ProfileClass.Testing -> R.string.profile_class_testing
                    ProfileClass.Provisioning -> R.string.profile_class_provisioning
                    ProfileClass.Operational -> R.string.profile_class_operational
                }
            )
            iccid.text = profile.iccid
            iccid.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        fun setProfileSequenceNumber(index: Int) {
            profileSeqNumber.text = root.context.getString(
                R.string.profile_sequence_number_format,
                index,
            )
        }

        fun setEnabledProfile(enabledProfile: LocalProfileInfo?) {
            // cannot cross profile class enable profile
            // e.g: testing -> operational or operational -> testing
            canEnable = enabledProfile == null ||
                enabledProfile.profileClass == profile.profileClass
        }

        private fun showOptionsMenu() {
            // Prevent users from doing multiple things at once
            if (!operationAllowed()) return

            PopupMenu(root.context, profileMenu).apply {
                setOnMenuItemClickListener(::onMenuItemClicked)
                populatePopupWithProfileActions(this, profile)
                show()
            }
        }

        private fun showDetails() {
            val sheet = BottomSheetDialog(requireContext())
            val content = layoutInflater.inflate(R.layout.luma_profile_detail, null)
            content.requireViewById<TextView>(R.id.luma_detail_name).text = profile.displayName
            content.requireViewById<TextView>(R.id.luma_detail_provider).text = profile.providerName
            content.requireViewById<TextView>(R.id.luma_detail_country).text =
                profileRegionDescription(ProfileCountry.fromOperatorId(profile.profileOwnerMccMnc))
            content.requireViewById<TextView>(R.id.luma_detail_state).setText(
                if (profile.isEnabled) R.string.profile_state_enabled else R.string.luma_inactive)
            content.requireViewById<TextView>(R.id.luma_detail_iccid).apply {
                text = profile.iccid
                transformationMethod = PasswordTransformationMethod.getInstance()
                setOnClickListener {
                    transformationMethod = if (transformationMethod == null)
                        PasswordTransformationMethod.getInstance() else null
                }
                setOnLongClickListener {
                    requireContext().getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("ICCID", profile.iccid))
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                        Toast.makeText(context, R.string.toast_iccid_copied, Toast.LENGTH_SHORT).show()
                    true
                }
            }
            content.requireViewById<MaterialButton>(R.id.luma_detail_enable).apply {
                setText(if (profile.isEnabled) R.string.luma_using else R.string.luma_use)
                isEnabled = !profile.isEnabled
                setOnClickListener { sheet.dismiss(); requestEnable(profile, canEnable) }
            }
            fun bindAction(viewId: Int, menuId: Int) {
                content.requireViewById<View>(viewId).apply {
                    val menu = PopupMenu(requireContext(), this).apply {
                        if (!previewMode) populatePopupWithProfileActions(this, profile)
                    }.menu
                    isVisible = !previewMode && menu.findItem(menuId)?.isVisible == true
                    setOnClickListener {
                        sheet.dismiss()
                        if (operationAllowed()) menu.findItem(menuId)?.let { onMenuItemClicked(it) }
                    }
                }
            }
            bindAction(R.id.luma_detail_rename, R.id.rename)
            bindAction(R.id.luma_detail_delete, R.id.delete)
            sheet.setContentView(content)
            sheet.show()
        }

        private fun onMenuItemClicked(item: MenuItem): Boolean =
            when (item.itemId) {
                R.id.enable -> {
                    requestEnable(profile, canEnable)
                    true
                }

                R.id.disable -> {
                    enableOrDisableProfile(profile.iccid, false)
                    true
                }

                R.id.rename -> {
                    ProfileRenameFragment.newInstance(
                        slotId,
                        portId,
                        seId,
                        profile.iccid,
                        profile.displayName
                    )
                        .show(childFragmentManager, ProfileRenameFragment.TAG)
                    true
                }

                R.id.delete -> {
                    ProfileDeleteFragment.newInstance(
                        slotId,
                        portId,
                        seId,
                        profile.iccid,
                        profile.displayName
                    )
                        .show(childFragmentManager, ProfileDeleteFragment.TAG)
                    true
                }

                else -> false
            }
    }

    inner class EuiccProfileAdapter : RecyclerView.Adapter<ViewHolder>() {
        var profiles: List<LocalProfileInfo> = emptyList()
        var footerViews: List<View> = emptyList()
        private val visibleFooters: List<View>
            get() = if (enabledOnly && profiles.isEmpty() && allProfiles.isNotEmpty())
                listOf(filteredEmptyView) else footerViews

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            when (ViewHolder.Type.fromInt(viewType)) {
                ViewHolder.Type.PROFILE -> ProfileViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.euicc_profile, parent, false))
                ViewHolder.Type.HEADER -> HeaderViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.luma_dashboard_header, parent, false))
                ViewHolder.Type.FOOTER -> FooterViewHolder()
            }

        override fun getItemViewType(position: Int): Int = when {
            position == 0 -> ViewHolder.Type.HEADER.value
            position <= profiles.size -> ViewHolder.Type.PROFILE.value
            else -> ViewHolder.Type.FOOTER.value
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> holder.bind()
                is ProfileViewHolder -> {
                    holder.setProfile(profiles[position - 1])
                    holder.setEnabledProfile(enabledProfile)
                    holder.setProfileSequenceNumber(position)
                }
                is FooterViewHolder -> holder.attach(visibleFooters[position - profiles.size - 1])
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            if (holder is FooterViewHolder) holder.detach()
        }

        override fun getItemCount(): Int = if (logicalSlotId == -1) 0 else 1 + profiles.size + visibleFooters.size
    }
}
