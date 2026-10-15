package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
 * First-run setup and "Add profile".
 *
 * First run is two steps: (1) set the ACCOUNT PIN — the one PIN that locks
 * this TV and guards profile management; (2) create the first profile.
 * Profiles after that are created from step 2 alone (from the picker).
 * A profile PIN is optional; when set it is typed twice so a mistype can't
 * lock anyone out.
 */
class ProfileSetupActivity : Activity() {

    private var firstRun = false
    private var pendingAccountPin: String? = null
    private var selectedColor = 0
    private lateinit var nameField: EditText
    private lateinit var pinField: EditText
    private lateinit var pinConfirmField: EditText
    private lateinit var errorView: TextView
    private lateinit var swatchRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        if (firstRun) {
            showAccountStep()
        } else {
            showProfileStep()
        }
    }

    // region step 1: account PIN (first run only)

    private fun showAccountStep() {
        val root = screenRoot()
        root.addView(titleView("Welcome to AeonTV", 48))
        root.addView(
            subtitleView(
                "Set the account PIN for this TV. It unlocks AeonTV and " +
                    "protects your profiles and settings.",
                24,
            ),
        )

        root.addView(label("Account PIN"))
        root.addView(subtitleView("4–6 digits. You'll enter it when AeonTV starts.", 20))
        val accountPinField = pinField("Account PIN")
        root.addView(accountPinField)
        val accountPinConfirm = pinField("Repeat account PIN")
        root.addView(accountPinConfirm)

        val error = errorText()
        root.addView(error)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        buttons.addView(accentButton("Continue") {
            val pin = accountPinField.text.toString().trim()
            val confirm = accountPinConfirm.text.toString().trim()
            when {
                !Profiles.isValidPin(pin) -> error.text = "PINs are 4–6 digits."
                pin != confirm -> error.text = "The two PINs don't match."
                else -> {
                    pendingAccountPin = pin
                    showProfileStep()
                }
            }
        })
        root.addView(buttons)

        root.addView(
            subtitleView(
                "There's no way to reset a forgotten account PIN — write it down somewhere safe.",
                20,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(24) }
            },
        )

        setContentView(root)
        accountPinField.requestFocus()
    }

    // endregion

    // region step 2: the profile itself

    private fun showProfileStep() {
        selectedColor = 0
        val root = screenRoot()
        root.addView(
            titleView(
                if (firstRun) "Create your first profile" else "Add profile",
                48,
            ),
        )
        root.addView(
            subtitleView(
                "Profiles are who's watching. Each one keeps its own favorites, " +
                    "Continue Watching and quality setting — and can have its own PIN if you want one.",
                24,
            ),
        )

        root.addView(label("Name"))
        nameField = editField("e.g. Isaiah")
        root.addView(nameField)

        root.addView(label("Color"))
        swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(20) }
        }
        root.addView(swatchRow)
        renderSwatches()

        root.addView(label("Profile PIN (optional)"))
        root.addView(subtitleView("4–6 digits. Leave blank for no PIN.", 20))
        pinField = pinField("PIN")
        root.addView(pinField)
        pinConfirmField = pinField("Repeat PIN")
        root.addView(pinConfirmField)

        errorView = errorText()
        root.addView(errorView)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        buttons.addView(accentButton(if (firstRun) "Create profile" else "Add profile") {
            submit()
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        if (!firstRun) {
            buttons.addView(accentButton("Cancel") { finish() })
        }
        root.addView(buttons)

        setContentView(root)
        nameField.requestFocus()
    }

    private fun renderSwatches() {
        swatchRow.removeAllViews()
        for (i in AeonUi.PROFILE_COLORS.indices) {
            val chosen = i == selectedColor
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            cell.addView(avatarView(profileColor(i), if (chosen) "✓" else "", 72))
            cell.setOnClickListener {
                selectedColor = i
                renderSwatches()
            }
            cell.applyAeonFocus(dimIdle = false)
            swatchRow.addView(cell)
        }
    }

    private fun submit() {
        val name = nameField.text.toString().trim()
        if (name.isEmpty()) {
            errorView.text = "Give the profile a name."
            return
        }
        val pin = pinField.text.toString().trim()
        val confirm = pinConfirmField.text.toString().trim()
        val finalPin: String? = when {
            pin.isEmpty() && confirm.isEmpty() -> null
            !Profiles.isValidPin(pin) -> {
                errorView.text = "PINs are 4–6 digits."
                return
            }
            pin != confirm -> {
                errorView.text = "The two PINs don't match."
                return
            }
            else -> pin
        }
        if (firstRun) {
            val accountPin = pendingAccountPin
            if (accountPin == null) {
                // Shouldn't happen (step 1 gates this), but never strand setup.
                showAccountStep()
                return
            }
            Profiles.setAccountPin(this, accountPin)
        }
        val profile = Profiles.addProfile(
            this,
            name = name,
            colorIndex = selectedColor,
            pin = finalPin,
            ultraLow110p = if (firstRun) StreamPrefs.globalIsUltraLow(this) else false,
        )
        if (firstRun) {
            Profiles.setActiveId(this, profile.id)
            SessionState.accountUnlocked = true
            AeonHomeActivity.start(this)
        }
        finish()
    }

    // endregion

    // region shared field builders

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(AeonUi.TEXT)
        textSize = 26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(8)
        }
    }

    private fun editField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(AeonUi.SUB)
        setTextColor(AeonUi.TEXT)
        textSize = 28f
        isFocusable = true
        isFocusableInTouchMode = true
        setSingleLine()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
    }

    private fun pinField(hint: String): EditText = editField(hint).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun errorText(): TextView = TextView(this).apply {
        setTextColor(0xFFFF6B6B.toInt())
        textSize = 22f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    // endregion

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"

        fun startFirstRun(context: Context) {
            context.startActivity(
                Intent(context, ProfileSetupActivity::class.java)
                    .putExtra(EXTRA_FIRST_RUN, true),
            )
        }

        fun startAdd(context: Context) {
            context.startActivity(
                Intent(context, ProfileSetupActivity::class.java)
                    .putExtra(EXTRA_FIRST_RUN, false),
            )
        }
    }
}
