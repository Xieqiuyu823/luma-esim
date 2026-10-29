package im.angry.openeuicc.ui

import android.os.Build
import android.telephony.SubscriptionManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import im.angry.openeuicc.R
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.typeblog.lpac_jni.LocalProfileInfo

internal data class ActiveSubscriptionSlot(
    val subscriptionId: Int,
    val logicalSlotIndex: Int,
    val portIndex: Int? = null
)

internal fun activeSubscriptionIdForLogicalSlot(
    subscriptions: Collection<ActiveSubscriptionSlot>,
    logicalSlotIndex: Int,
    portIndex: Int? = null
): Int? {
    val matches = subscriptions.filter { it.logicalSlotIndex == logicalSlotIndex }
    return if (portIndex != null) {
        matches.singleOrNull { it.portIndex == portIndex }?.subscriptionId
    } else {
        matches.singleOrNull()?.subscriptionId
    }
}

class PrivilegedEuiccManagementFragment : EuiccManagementFragment(), PrivilegedEuiccContextMarker {
    companion object {
        fun newInstance(
            slotId: Int,
            portId: Int,
            seId: EuiccChannel.SecureElementId
        ): EuiccManagementFragment =
            newInstanceEuicc(PrivilegedEuiccManagementFragment::class.java, slotId, portId, seId)
    }

    private var isMEP = false
    private var isRemovable = false

    override suspend fun onProfileSwitchSucceeded(logicalSlotId: Int) {
        if (logicalSlotId < 0 || !preferenceRepository.autoEnableDataRoamingFlow.first()) return

        val result = try {
            withContext(Dispatchers.IO) {
                val subscriptions = appContainer.subscriptionManager.activeSubscriptionInfoList.orEmpty()
                val supportsPortIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                val subscriptionId = activeSubscriptionIdForLogicalSlot(
                    subscriptions.map {
                        ActiveSubscriptionSlot(
                            it.subscriptionId,
                            it.simSlotIndex,
                            if (supportsPortIndex) it.portIndex else null
                        )
                    },
                    logicalSlotId,
                    if (supportsPortIndex) portId else null
                ) ?: return@withContext AutoRoamingResult.NO_ACTIVE_SUBSCRIPTION

                val info = subscriptions.single { it.subscriptionId == subscriptionId }
                if (info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE) {
                    AutoRoamingResult.ALREADY_ENABLED
                } else if (appContainer.telephonyManager.enableDataRoaming(
                        appContainer.subscriptionManager,
                        subscriptionId
                    )
                ) {
                    AutoRoamingResult.ENABLED
                } else {
                    AutoRoamingResult.FAILED
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AutoRoamingResult.FAILED
        }

        val message = when (result) {
            AutoRoamingResult.ENABLED -> R.string.toast_auto_data_roaming_enabled
            AutoRoamingResult.NO_ACTIVE_SUBSCRIPTION -> R.string.toast_auto_data_roaming_no_sim
            AutoRoamingResult.FAILED -> R.string.toast_auto_data_roaming_failed
            AutoRoamingResult.ALREADY_ENABLED -> return
        }
        if (isAdded) {
            val text = if (result == AutoRoamingResult.ENABLED) {
                getString(message, logicalSlotId + 1)
            } else {
                getString(message)
            }
            Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
        }
    }

    private enum class AutoRoamingResult {
        ENABLED,
        ALREADY_ENABLED,
        NO_ACTIVE_SUBSCRIPTION,
        FAILED
    }

    override suspend fun onCreateFooterViews(
        parent: ViewGroup,
        profiles: List<LocalProfileInfo>
    ): List<View> =
        super.onCreateFooterViews(parent, profiles).let { footers ->
            withEuiccChannel { channel ->
                isMEP = channel.isMEP
                isRemovable = channel.port.card.isRemovable
            }

            if (isMEP) {
                val view = layoutInflater.inflate(R.layout.footer_mep, parent, false)
                view.requireViewById<Button>(R.id.footer_mep_slot_mapping).setOnClickListener {
                    (requireActivity() as PrivilegedMainActivity).showSlotMappingFragment()
                }
                footers + view
            } else {
                footers
            }
        }

    override fun populatePopupWithProfileActions(popup: PopupMenu, profile: LocalProfileInfo) {
        super.populatePopupWithProfileActions(popup, profile)
        if (!profile.isEnabled || isRemovable) return
        // Only show the disable option for non-removable eUICCs
        // Some devices without internal eUICCs have the "optimization" of ignoring SIM
        // slots without a valid profile. This can lead to "bricking" of external eUICCs
        // at least for that specific device.
        // TODO: Maybe we can still make this option available in some sort of "advanced" mode.
        popup.menu.findItem(im.angry.openeuicc.common.R.id.disable).isVisible = true
    }
}
