package im.angry.openeuicc.ui

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.OpenEuiccContextMarker

class NoEuiccPlaceholderFragment : Fragment(), OpenEuiccContextMarker {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_no_euicc_placeholder, container, false)
        val textView = view.requireViewById<TextView>(R.id.no_euicc_placeholder)
        textView.setText(R.string.luma_not_found_message)
        view.requireViewById<View>(R.id.luma_retry).setOnClickListener { (activity as? MainActivity)?.refresh() }
        view.requireViewById<View>(R.id.luma_preview_button).setOnClickListener {
            startActivity(Intent(requireContext(), LumaPreviewActivity::class.java))
        }
        return view
    }
}
