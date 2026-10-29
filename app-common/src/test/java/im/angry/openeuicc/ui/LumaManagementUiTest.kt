package im.angry.openeuicc.ui

import android.app.Dialog
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import im.angry.openeuicc.common.R
import im.angry.openeuicc.service.EuiccChannelManagerService
import im.angry.openeuicc.testutil.*
import im.angry.openeuicc.util.isEnabled
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import net.typeblog.lpac_jni.LocalProfileAssistant
import net.typeblog.lpac_jni.LocalProfileInfo
import net.typeblog.lpac_jni.ProfileClass
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestOpenEuiccApplication::class, sdk = [35],
    qualifiers = "zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumaManagementUiTest {
    private fun preview(): org.robolectric.android.controller.ActivityController<LumaPreviewActivity> {
        val controller = Robolectric.buildActivity(LumaPreviewActivity::class.java)
        controller.get().setTheme(R.style.Theme_OpenEUICC)
        controller.setup().visible()
        shadowOf(Looper.getMainLooper()).idle()
        layout(controller.get().window.decorView)
        return controller
    }

    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1830, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 824, 1830)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun previewLoadsWithoutOpeningAnyEuiccChannel() {
        // TestOpenEuiccApplication has no manager installed: accessing a channel would fail.
        val controller = preview()
        val activity = controller.get()
        assertEquals(4, activity.requireViewById<RecyclerView>(R.id.profile_list).adapter!!.itemCount)
        assertTrue(activity.supportActionBar!!.subtitle.toString().contains("示例数据"))
        controller.pause().stop().destroy()
    }

    @Test fun profileFlagsFollowOwnerMetadataAndResetWhenHolderIsReused() {
        val controller = preview()
        val activity = controller.get()
        val recycler = activity.requireViewById<RecyclerView>(R.id.profile_list)
        val adapter = recycler.adapter as EuiccManagementFragment.EuiccProfileAdapter
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(1)) as
            EuiccManagementFragment.ProfileViewHolder
        val flag = holder.itemView.requireViewById<ImageView>(R.id.luma_profile_flag)
        adapter.bindViewHolder(holder, 1)
        assertEquals(R.drawable.flag_cn, shadowOf(flag.drawable).createdFromResId)
        assertTrue(flag.contentDescription.toString().contains("中国"))
        adapter.bindViewHolder(holder, 2)
        assertEquals(R.drawable.flag_jp, shadowOf(flag.drawable).createdFromResId)
        adapter.bindViewHolder(holder, 3)
        assertEquals(R.drawable.flag_gb, shadowOf(flag.drawable).createdFromResId)
        for (operatorId in listOf("", "09F110", "64F00Z")) {
            // A recycled row must not retain the previous profile's country.
            holder.setProfile(LocalProfileInfo("8900000000000000004",
                LocalProfileInfo.State.Disabled, "Unknown", "", "Example", "",
                ProfileClass.Operational, operatorId))
            assertEquals(R.drawable.ic_luma_globe, shadowOf(flag.drawable).createdFromResId)
            assertEquals(activity.getString(R.string.luma_profile_region_unknown), flag.contentDescription)
        }
        controller.pause().stop().destroy()
    }

    @Test fun enabledFilterCanBeToggledAndSurvivesRecreation() {
        val controller = preview()
        controller.get().requireViewById<Chip>(R.id.luma_filter_active).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, controller.get().requireViewById<RecyclerView>(R.id.profile_list).adapter!!.itemCount)
        controller.recreate().visible()
        layout(controller.get().window.decorView)
        assertEquals(2, controller.get().requireViewById<RecyclerView>(R.id.profile_list).adapter!!.itemCount)
        controller.get().requireViewById<Chip>(R.id.luma_filter_all).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(4, controller.get().requireViewById<RecyclerView>(R.id.profile_list).adapter!!.itemCount)
        controller.pause().stop().destroy()
    }

    @Test fun eidIsHiddenButRevealAndCopyUseTheRealIdentifier() {
        val controller = preview()
        val activity = controller.get()
        val eid = activity.requireViewById<TextView>(R.id.luma_eid)
        assertFalse(eid.text.contains("89000000000000000000000000000000"))
        eid.performClick()
        assertTrue(eid.text.contains("89000000000000000000000000000000"))
        eid.performLongClick()
        assertEquals("89000000000000000000000000000000", activity.getSystemService(ClipboardManager::class.java)
            .primaryClip!!.getItemAt(0).text.toString())
        eid.performClick()
        assertFalse(eid.text.contains("89000000000000000000000000000000"))
        controller.pause().stop().destroy()
    }

    @Test fun previewDownloadCannotStartTheRealWizard() {
        val controller = preview()
        val activity = controller.get()
        activity.requireViewById<View>(R.id.luma_download).performClick()
        assertEquals(activity.getString(R.string.luma_preview_only), ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test fun realProfileSwitchRequiresConfirmationAndCancelDoesNotCallLpa(): Unit = runBlocking {
        val switches = java.util.concurrent.atomic.AtomicInteger(0)
        val profiles = listOf(
            LocalProfileInfo("8900000000000000001", LocalProfileInfo.State.Enabled, "Active", "", "Example", "", ProfileClass.Operational),
            LocalProfileInfo("8900000000000000002", LocalProfileInfo.State.Disabled, "Travel", "", "Example", "", ProfileClass.Operational)
        )
        val lpa = object : LocalProfileAssistant by MockLpa() {
            override val profiles = profiles
            override fun enableProfile(iccid: String, refresh: Boolean): Boolean { switches.incrementAndGet(); return true }
        }
        val manager = MockEuiccChannelManager(MockEuiccChannel(0, 0, lpa))
        TestOpenEuiccApplication.mockEuiccChannelManager = manager
        val serviceController = Robolectric.buildService(EuiccChannelManagerService::class.java).create()
        val service = serviceController.get()
        val context = (RuntimeEnvironment.getApplication() as TestOpenEuiccApplication)
        val bindIntent = Intent(context, EuiccChannelManagerService::class.java)
        shadowOf(context).setComponentNameAndServiceForBindService(
            ComponentName(context, EuiccChannelManagerService::class.java), service.onBind(bindIntent))
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.get().setTheme(R.style.Theme_OpenEUICC)
        controller.setup().visible()
        awaitMainLooper {
            controller.get().findViewById<RecyclerView>(R.id.profile_list)?.adapter?.itemCount == 3
        }
        val recycler = controller.get().requireViewById<RecyclerView>(R.id.profile_list)
        layout(controller.get().window.decorView)
        // Create and bind the exact production holder for the inactive row.
        @Suppress("UNCHECKED_CAST")
        val adapter = recycler.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(2))
        adapter.bindViewHolder(holder, 2)
        holder.itemView.requireViewById<View>(R.id.luma_profile_action).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(0, switches.get())
        dialog.getButton(Dialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, switches.get())
        holder.itemView.requireViewById<View>(R.id.luma_profile_action).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(Dialog.BUTTON_POSITIVE).performClick()
        // Robolectric records startForegroundService but does not deliver the OS callback.
        shadowOf(Looper.getMainLooper()).idle()
        service.onStartCommand(Intent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        awaitMainLooper { switches.get() == 1 }
        // Finish the task before tearing down the service.
        val idle = async { service.waitForForegroundTask() }
        awaitMainLooper { idle.isCompleted }
        controller.pause().stop().destroy()
        serviceController.destroy()
    }

    private fun capture(name: String) {
        val controller = preview()
        val root = controller.get().window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val target = File("build/reports/luma/$name.png")
        requireNotNull(target.parentFile).mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(target.length() > 1000)
        controller.pause().stop().destroy()
    }

    @Test fun captureLightPreview() = capture("preview-light")

    @Test
    @Config(qualifiers = "zh-rCN-w412dp-h915dp-night-xhdpi")
    fun captureDarkPreview() = capture("preview-dark")
}
