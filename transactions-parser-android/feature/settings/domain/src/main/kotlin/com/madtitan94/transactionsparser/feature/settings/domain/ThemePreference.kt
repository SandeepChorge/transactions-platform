package com.madtitan94.transactionsparser.feature.settings.domain

/**
 * Which of the two palettes the app should wear.
 *
 * [SYSTEM] is the default rather than [DARK]: the design ships both themes precisely so that a
 * device following its own light/dark setting gets the half it asked for, and a user who has
 * never opened Settings should still see the app agree with the rest of their phone.
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}
