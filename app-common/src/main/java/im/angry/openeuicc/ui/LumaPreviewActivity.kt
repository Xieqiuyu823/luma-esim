package im.angry.openeuicc.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.util.*

class LumaPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_luma_preview)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Luma eSIM"
            subtitle = getString(R.string.luma_preview_label)
            setDisplayHomeAsUpEnabled(true)
        }
        setupRootViewSystemBarInsets(window.decorView.rootView,
            arrayOf(this::activityToolbarInsetHandler), consume = false)
        if (savedInstanceState == null) {
            val fragment = newInstanceEuicc(EuiccManagementFragment::class.java,
                0, 0, EuiccChannel.SecureElementId.DEFAULT) { putBoolean("lumaPreview", true) }
            supportFragmentManager.beginTransaction()
                .replace(R.id.luma_preview_container, fragment).commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)
}
