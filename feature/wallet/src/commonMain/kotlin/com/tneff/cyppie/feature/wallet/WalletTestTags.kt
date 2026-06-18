package com.tneff.cyppie.feature.wallet

/**
 * Stable testTags for the wallet feature screens (mirrors `OnboardingTestTags`). The Receive tags
 * intentionally match the i18n keys (KAN-78 / KAN-47 spec) so design, copy and tests share one
 * vocabulary.
 */
object WalletTestTags {
    const val RECEIVE_CHAIN = "receive_chain"
    const val RECEIVE_QR = "receive_qr"
    const val RECEIVE_ADDRESS = "receive_address"
    const val RECEIVE_COPY = "receive_copy"
    const val RECEIVE_WARNING = "receive_warning"
}
