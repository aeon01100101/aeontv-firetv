package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.jellyfin.androidtv.aeon.AeonUi.accentButton
import org.jellyfin.androidtv.aeon.AeonUi.avatarView
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.profileColor
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * Profile management, reached only with the account unlocked: reset any
 * profile's PIN (set a new one or remove it), delete profiles, add a
 * profile, and change the account PIN. Guarded here as well as at the gate,
 * so a cold start of this screen without unlocking bounces straight out.
 */
class ManageProfilesActivity : Activity() {

    private var editingPinFor: String? = null
    private var confirmingDeleteFor: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionState.accountUnlocked) {
            finish()
            return
        }
        showList()
    }

    private fun showList() {
        editingPinFor = null
        confirmingDeleteFor = null
        val profiles = Profiles.load(this)
        val root = screenRoot()
        root.addView(titleView("Manage profiles", 48))
        root.addView(
            subtitleView(
                "Reset a profile's PIN, delete a profile, or change the account PIN.",
                24,
            ),
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        for (profile in profiles) {
            list.addView(profileRow(profile))
        }
        root.addView(list)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }
        buttons.addView(accentButton("＋ Add profile") {
            ProfileSetupActivity.startAdd(this)
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(12) }
        })
        buttons.addView(accentButton("Change account PIN") { showChangeAccountPin() }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(12) }
        })
        buttons.addView(accentButton("Done") { finish() })
        root.addView(buttons)
        setContentView(root)
    }

    private fun showChangeAccountPin() {
        val root = screenRoot()
        root.addView(titleView("Change account PIN", 48))
        root.addView(
            subtitleView(
                "The account PIN unlocks this TV and protects Manage profiles.",
                24,
            ),
        )

        val pinField = pinField("New account PIN (4–6 digits)")
        val pinConfirm = pinField("Repeat new account PIN")
        val error = errorText()
        root.addView(pinField)
        root.addView(pinConfirm)
        root.addView(error)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
        }
        buttons.addView(accentButton("Save") {
            val pin = pinField.text.toString().trim()
            val confirm = pinConfirm.text.toString().trim()
            when {
                !Profiles.isValidPin(pin) -> error.text = "PINs are 4–6 digits."
                pin != confirm -> error.text = "The two PINs don't match."
                else -> {
                    Profiles.setAccountPin(this, pin)
                    showList()
                }
            }
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        buttons.addView(accentButton("Cancel") { showList() })
        root.addView(buttons)
        setContentView(root)
        pinField.requestFocus()
    }

    private fun profileRow(profile: Profile): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) }
        }
        row.addView(avatarView(profileColor(profile.colorIndex), profile.name, 88))
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply {
                    leftMargin = dp(20)
                    rightMargin = dp(20)
                }
        }
        textCol.addView(TextView(this).apply {
            text = profile.name
            setTextColor(AeonUi.TEXT)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        textCol.addView(TextView(this).apply {
            text = if (profile.pinHash != null) "PIN set" else "No PIN"
            setTextColor(AeonUi.SUB)
            textSize = 22f
        })
        row.addView(textCol)
        row.addView(accentButton("Reset PIN") { showResetPin(profile) }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(12) }
        })
        row.addView(accentButton("Delete") { showConfirmDelete(profile) })
        return row
    }

    private fun showResetPin(profile: Profile) {
        editingPinFor = profile.id
        val root = screenRoot()
        root.addView(titleView("Reset PIN", 48))
        root.addView(subtitleView("${profile.name} — leave blank to remove the PIN.", 24))

        val pinField = pinField("New PIN (4–6 digits, or blank)")
        val pinConfirm = pinField("Repeat new PIN")
        val error = errorText()
        root.addView(pinField)
        root.addView(pinConfirm)
        root.addView(error)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
        }
        buttons.addView(accentButton("Save") {
            val pin = pinField.text.toString().trim()
            val confirm = pinConfirm.text.toString().trim()
            when {
                pin.isEmpty() && confirm.isEmpty() -> {
                    Profiles.setPin(this, profile.id, null)
                    showList()
                }
                !Profiles.isValidPin(pin) -> error.text = "PINs are 4–6 digits."
                pin != confirm -> error.text = "The two PINs don't match."
                else -> {
                    Profiles.setPin(this, profile.id, pin)
                    showList()
                }
            }
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        buttons.addView(accentButton("Cancel") { showList() })
        root.addView(buttons)
        setContentView(root)
        pinField.requestFocus()
    }

    private fun showConfirmDelete(profile: Profile) {
        confirmingDeleteFor = profile
        val root = screenRoot()
        root.addView(titleView("Delete profile?", 48))
        root.addView(
            subtitleView(
                "Delete ${profile.name} and all of its saved progress and favorites? " +
                    "This can't be undone.",
                24,
            ),
        )
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(24) }
        }
        buttons.addView(accentButton("Delete") {
            Profiles.deleteProfile(this, profile.id)
            if (Profiles.load(this).isEmpty()) {
                // No profiles left: the gate will route to creating one.
                finish()
            } else {
                showList()
            }
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        buttons.addView(accentButton("Cancel") { showList() })
        root.addView(buttons)
        setContentView(root)
    }

    private fun pinField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(AeonUi.SUB)
        setTextColor(AeonUi.TEXT)
        textSize = 28f
        isFocusable = true
        isFocusableInTouchMode = true
        setSingleLine()
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
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

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ManageProfilesActivity::class.java))
        }
    }
}
