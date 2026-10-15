package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.androidtv.aeon.AeonUi.accentButton
import org.jellyfin.androidtv.aeon.AeonUi.applyAeonFocus
import org.jellyfin.androidtv.aeon.AeonUi.avatarView
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.profileColor
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.R

/**
 * The launcher entry.
 *
 * - No account yet → first-run setup (account PIN + first profile).
 * - Account locked → the account PIN unlocks this TV (once per session).
 * - Then: exactly one profile with no PIN → straight in; otherwise the
 *   "Who's watching?" picker; profiles with their own PIN ask for it.
 *
 * Model (Isaiah's): ONE account with a PIN; multiple profiles under it;
 * profile PINs are optional per profile. A forgotten profile PIN can be
 * reset in Manage profiles (behind the account PIN). The account PIN has
 * no reset and no backdoor — only clearing the app's data recovers it.
 */
class ProfileGateActivity : Activity() {

    private enum class PinPurpose { ACCOUNT, PROFILE }

    private var profiles: List<Profile> = emptyList()
    private var pinMode = false
    private val pinDigits = StringBuilder()
    private var pinTarget: Profile? = null
    private var pinPurpose = PinPurpose.PROFILE
    private var pinAfterAuth: (() -> Unit)? = null
    private var dotsRow: LinearLayout? = null
    private var pinError: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        route()
    }

    private fun route() {
        profiles = Profiles.load(this)
        pinMode = false
        when {
            !Profiles.hasAccount(this) -> {
                ProfileSetupActivity.startFirstRun(this)
                showBlank()
            }
            !SessionState.accountUnlocked -> showAccountPin()
            else -> routeAfterUnlock()
        }
    }

    private fun routeAfterUnlock() {
        profiles = Profiles.load(this)
        when {
            profiles.isEmpty() -> {
                ProfileSetupActivity.startAdd(this)
                showBlank()
            }
            profiles.size == 1 && profiles[0].pinHash == null -> {
                enterProfile(profiles[0])
            }
            else -> showPicker()
        }
    }

    private fun showAccountPin() {
        showPinEntry(
            profile = null,
            purpose = PinPurpose.ACCOUNT,
            title = "Enter account PIN",
            subtitle = "This TV is protected by an account PIN.",
            footer = "Forgot the account PIN? It can't be reset — only clearing " +
                "AeonTV's app data (which removes all profiles) restores access.",
        ) {
            SessionState.accountUnlocked = true
            routeAfterUnlock()
        }
    }

    private fun enterProfile(profile: Profile) {
        Profiles.setActiveId(this, profile.id)
        AeonHomeActivity.start(this)
        finish()
    }

    private fun showBlank() {
        setContentView(screenRoot())
    }

    // region picker

    private fun showPicker() {
        pinMode = false
        val root = screenRoot()
        root.addView(titleView("Who's watching?", 52))
        root.addView(subtitleView("Pick your profile to start watching.", 24))

        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@ProfileGateActivity, 3)
            setBackgroundColor(AeonUi.BG)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        grid.adapter = PickerAdapter(profiles, ::onProfileClicked)
        root.addView(grid)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(20) }
        }
        buttons.addView(accentButton("＋ Add profile") {
            ProfileSetupActivity.startAdd(this)
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        buttons.addView(accentButton("⚙ Manage profiles") {
            ManageProfilesActivity.start(this)
        })
        root.addView(buttons)

        setContentView(root)
        (grid.getChildAt(0) ?: grid).let {
            grid.post { grid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
    }

    private inner class PickerAdapter(
        private val items: List<Profile>,
        private val onClick: (Profile) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = LinearLayout(this@ProfileGateActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.aeon_card)
                setPadding(dp(24), dp(28), dp(24), dp(28))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            return object : RecyclerView.ViewHolder(card) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val profile = items[position]
            val card = holder.itemView as LinearLayout
            card.removeAllViews()
            card.tag = profile.id
            card.addView(
                avatarView(
                    profileColor(profile.colorIndex),
                    profile.name,
                    120,
                ),
            )
            card.addView(TextView(this@ProfileGateActivity).apply {
                text = profile.name
                setTextColor(AeonUi.TEXT)
                textSize = 30f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
                maxLines = 1
            })
            card.addView(TextView(this@ProfileGateActivity).apply {
                val locked = profile.pinHash != null
                text = if (locked) "🔒 PIN" else "No PIN"
                setTextColor(AeonUi.SUB)
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            card.setOnClickListener { onClick(profile) }
            card.applyAeonFocus(dimIdle = true)
        }
    }

    private fun onProfileClicked(profile: Profile) {
        if (profile.pinHash == null) {
            enterProfile(profile)
        } else {
            showPinEntry(
                profile = profile,
                purpose = PinPurpose.PROFILE,
                title = "Enter your PIN",
                subtitle = profile.name,
                footer = "Forgot your PIN? It can be reset in Manage profiles.",
            ) { enterProfile(profile) }
        }
    }

    // endregion

    // region PIN entry

    private fun showPinEntry(
        profile: Profile?,
        purpose: PinPurpose,
        title: String,
        subtitle: String,
        footer: String,
        onSuccess: () -> Unit,
    ) {
        pinMode = true
        pinDigits.clear()
        pinTarget = profile
        pinPurpose = purpose
        pinAfterAuth = onSuccess

        val root = screenRoot()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(titleView(title, 48))
        root.addView(subtitleView(subtitle, 28))

        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(24) }
        }
        root.addView(dotsRow)
        renderDots()

        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫"),
        )
        var firstKey: TextView? = null
        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(10) }
            }
            for (label in row) {
                val key = TextView(this).apply {
                    text = label
                    setTextColor(AeonUi.TEXT)
                    textSize = 32f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    setBackgroundResource(R.drawable.aeon_card)
                    layoutParams = LinearLayout.LayoutParams(dp(104), dp(80)).apply {
                        leftMargin = dp(8)
                        rightMargin = dp(8)
                    }
                    setOnClickListener { onPadKey(label) }
                    applyAeonFocus(dimIdle = false)
                }
                if (firstKey == null) firstKey = key
                rowLayout.addView(key)
            }
            pad.addView(rowLayout)
        }
        root.addView(pad)

        val okRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        okRow.addView(accentButton("OK") { submitPin() }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        okRow.addView(accentButton("Clear") { clearPin() })
        root.addView(okRow)

        pinError = TextView(this).apply {
            setTextColor(0xFFFF6B6B.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
        }
        root.addView(pinError)

        root.addView(subtitleView(footer, 20).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(24) }
        })

        setContentView(root)
        firstKey?.requestFocus()
    }

    private fun renderDots() {
        val row = dotsRow ?: return
        row.removeAllViews()
        for (i in 0 until 6) {
            row.addView(TextView(this).apply {
                text = if (i < pinDigits.length) "●" else "○"
                setTextColor(if (i < pinDigits.length) AeonUi.ACCENT else AeonUi.SUB)
                textSize = 40f
                setPadding(dp(10), 0, dp(10), 0)
            })
        }
    }

    private fun onPadKey(label: String) {
        when (label) {
            "C" -> clearPin()
            "⌫" -> {
                if (pinDigits.isNotEmpty()) {
                    pinDigits.deleteCharAt(pinDigits.length - 1)
                    renderDots()
                    pinError?.text = ""
                }
            }
            else -> {
                if (pinDigits.length < 6 && label[0].isDigit()) {
                    pinDigits.append(label)
                    renderDots()
                    pinError?.text = ""
                    if (pinDigits.length == 6) submitPin()
                }
            }
        }
    }

    private fun clearPin() {
        pinDigits.clear()
        renderDots()
        pinError?.text = ""
    }

    private fun submitPin() {
        val pin = pinDigits.toString()
        if (!Profiles.isValidPin(pin)) {
            pinError?.text = "PINs are 4–6 digits."
            clearPin()
            return
        }
        val ok = when (pinPurpose) {
            PinPurpose.ACCOUNT -> Profiles.verifyAccountPin(this, pin)
            PinPurpose.PROFILE -> {
                val target = pinTarget
                target != null && Profiles.verifyPin(target, pin)
            }
        }
        if (ok) {
            val next = pinAfterAuth
            pinAfterAuth = null
            pinMode = false
            next?.invoke()
        } else {
            pinError?.text = "Wrong PIN. Try again."
            clearPin()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (pinMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_0 -> { onPadKey("0"); return true }
                KeyEvent.KEYCODE_1 -> { onPadKey("1"); return true }
                KeyEvent.KEYCODE_2 -> { onPadKey("2"); return true }
                KeyEvent.KEYCODE_3 -> { onPadKey("3"); return true }
                KeyEvent.KEYCODE_4 -> { onPadKey("4"); return true }
                KeyEvent.KEYCODE_5 -> { onPadKey("5"); return true }
                KeyEvent.KEYCODE_6 -> { onPadKey("6"); return true }
                KeyEvent.KEYCODE_7 -> { onPadKey("7"); return true }
                KeyEvent.KEYCODE_8 -> { onPadKey("8"); return true }
                KeyEvent.KEYCODE_9 -> { onPadKey("9"); return true }
                KeyEvent.KEYCODE_DEL -> { onPadKey("⌫"); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Use for the simple gate back behavior")
    override fun onBackPressed() {
        if (pinMode && pinPurpose == PinPurpose.PROFILE) {
            showPicker()
        } else {
            // The account PIN screen is the lock itself: Back just exits.
            super.onBackPressed()
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ProfileGateActivity::class.java))
        }
    }
}
