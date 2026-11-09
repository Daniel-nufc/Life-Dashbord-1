package app.lifedashboard.testing

import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Nur für Tests: leere Activity, in der Screenshot-Tests die echten Bildschirme anzeigen. */
@AndroidEntryPoint
class HiltTestActivity : FragmentActivity()
