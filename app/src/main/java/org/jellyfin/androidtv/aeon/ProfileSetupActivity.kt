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
import org.jellyfin.androidtv.aeon.AeonUi.applyAeonFocus
import org.jellyfin.androidtv.aeon.AeonUi.avatarView
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.profileColor
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.R

/**
 * Profile creation: the first-run owner setup and "Add profile" share this
 * screen. Name, avatar color, and an optional 4–6 digit PIN (typed twice so
 * a mistype can't lock anyone out).
 */
class ProfileSetupActivity : Activity() {

    private var isOwnerMode = false
    private var selectedColor = 0
    private lateinit var nameField: EditText
    private lateinit var pinField: EditText
    private lateinit var pinConfirmField: EditText
    private lateinit var errorView: TextView
    private lateinit var swatchRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOwnerMode = intent.getBooleanExtra(EXTRA_OWNER_MODE, false)

        val root = screenRoot()
        root.addView(
            titleView(
                if (isOwnerMode) "Welcome to AeonTV" else "Add profile",
                48,
            ),
        )
        root.addView(
            subtitleView(
                if (isOwnerMode) {
                    "Create your profile — you'll be the owner of this TV."
                } else {
                    "A new profile for this TV."
                },
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

        root.addView(label("PIN (optional)"))
        root.addView(subtitleView("4–6 digits. Leave blank for no PIN.", 20))
        pinField = pinField("PIN")
        root.addView(pinField)
        pinConfirmField = pinField("Repeat PIN")
        root.addView(pinConfirmField)

        errorView = TextView(this).apply {
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
        root.addView(errorView)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        buttons.addView(accentButton(if (isOwnerMode) "Create profile" else "Add profile") {
            submit()
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.apply { rightMargin = dp(16) }
        })
        if (!isOwnerMode) {
            buttons.addView(accentButton("Cancel") { finish() })
        }
        root.addView(buttons)

        setContentView(root)
        nameField.requestFocus()
    }

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
        val profile = Profiles.addProfile(
            this,
            name = name,
            colorIndex = selectedColor,
            pin = finalPin,
            isOwner = isOwnerMode,
            ultraLow110p = if (isOwnerMode) StreamPrefs.globalIsUltraLow(this) else false,
        )
        if (isOwnerMode) {
            Profiles.setActiveId(this, profile.id)
            SessionState.ownerAuthed = true
            AeonHomeActivity.start(this)
        }
        finish()
    }

    companion object {
        private const val EXTRA_OWNER_MODE = "owner_mode"

        fun startOwner(context: Context) {
            context.startActivity(
                Intent(context, ProfileSetupActivity::class.java)
                    .putExtra(EXTRA_OWNER_MODE, true),
            )
        }

        fun startAdd(context: Context) {
            context.startActivity(
                Intent(context, ProfileSetupActivity::class.java)
                    .putExtra(EXTRA_OWNER_MODE, false),
            )
        }
    }
}
