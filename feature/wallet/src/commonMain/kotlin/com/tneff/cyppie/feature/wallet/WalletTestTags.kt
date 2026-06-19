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

    const val TOKEN_CONTRACT_FIELD = "token_contract_field"
    const val TOKEN_ERROR = "token_error"
    const val TOKEN_RESOLVED = "token_resolved"
    const val TOKEN_NETWORK = "token_network"
    const val TOKEN_ADD = "token_add"
    // Wallet-Home (KAN-81 / D1)
    const val HOME_ACCOUNT_LIST = "home_account_list"
    const val HOME_DEGRADED_BANNER = "home_degraded_banner"
    const val HOME_REFRESH = "home_refresh"
    const val HOME_EMPTY = "home_empty"
    const val HOME_ERROR = "home_error"
    const val HOME_RETRY = "home_retry"
    const val HOME_RECEIVE = "home_receive"
    const val HOME_ADD_TOKEN = "home_add_token"
    const val HOME_NFTS = "home_nfts"
    const val HOME_SEND = "home_send"
    const val HOME_PORTFOLIO = "home_portfolio"
    const val HOME_CONNECT = "home_connect"

    /** Account row `i` in the switcher. */
    fun homeAccountItem(i: Int): String = "home_account_item_$i"

    /** Per-chain section, `home_chain_section_<chain>` (chain display name, lowercased). */
    fun homeChainSection(chain: String): String = "home_chain_section_${chain.lowercase()}"

    /** Native balance row for a chain. */
    fun homeBalanceNative(chain: String): String = "home_balance_native_${chain.lowercase()}"

    /** Token balance row, `home_balance_token_<chain>_<addr>`. */
    fun homeBalanceToken(chain: String, addr: String): String =
        "home_balance_token_${chain.lowercase()}_${addr.lowercase()}"

    // NFT grid (KAN-105)
    const val NFT_GRID = "nft_grid"
    const val NFT_EMPTY = "nft_empty"
    const val NFT_ERROR = "nft_error"
    const val NFT_RETRY = "nft_retry"
    const val NFT_DEGRADED_BANNER = "nft_degraded_banner"

    /** NFT tile, `nft_item_<contract>_<tokenId>`. */
    fun nftItem(contract: String, tokenId: String): String =
        "nft_item_${contract.lowercase()}_$tokenId"

    // Send flow (KAN-110) — testTags mirror the i18n keys (one vocabulary for design/copy/tests).
    const val SEND_ASSET_SELECT = "send_asset_select"
    const val SEND_AMOUNT_FIELD = "send_amount_field"
    const val SEND_MAX = "send_max"
    const val SEND_RECIPIENT_FIELD = "send_recipient_field"
    const val SEND_RECIPIENT_ERROR = "send_recipient_error"
    const val SEND_FEE = "send_fee"
    const val SEND_CONTINUE = "send_continue"
    const val SEND_INSUFFICIENT = "send_err_insufficient"
    const val SEND_NETWORK_BANNER = "send_err_network"
    const val SEND_FEESPIKE_BANNER = "send_err_feespike"
    const val SEND_DISCLOSURE = "send_disclosure"
    const val SEND_DISCLOSURE_ASSET = "send_disclosure_asset"
    const val SEND_DISCLOSURE_NETWORK = "send_disclosure_network"
    const val SEND_DISCLOSURE_MAXFEE = "send_disclosure_maxfee"
    const val SEND_DISCLOSURE_FEE = "send_disclosure_fee"
    const val SEND_DISCLOSURE_TO = "send_disclosure_to"
    const val SEND_DISCLOSURE_NONCE = "send_disclosure_nonce"
    const val SEND_DISCLOSURE_GAS = "send_disclosure_gas"
    const val SEND_DISCLOSURE_TOTAL = "send_disclosure_total"
    const val SEND_SIGN = "send_sign"
    const val SEND_AUTH_PASSWORD = "send_auth_password"
    const val SEND_AUTH_SUBMIT = "send_auth_submit"
    const val SEND_AUTH_ERROR = "send_auth_error"
    const val SEND_STATUS_PENDING = "send_status_pending"
    const val SEND_STATUS_CONFIRMED = "send_status_confirmed"
    const val SEND_STATUS_FAILED = "send_status_failed"
    const val SEND_REJECTED_DIALOG = "send_err_rejected"
    const val SEND_EXPLORER = "send_explorer"
    const val SEND_DONE = "send_done"

    /** Asset row in the picker, `send_asset_<chain>_<symbol>`. */
    fun sendAsset(chain: String, symbol: String): String =
        "send_asset_${chain.lowercase()}_${symbol.lowercase()}"

    /** Fee tier option, `send_fee_<tier>` (slow/normal/fast). */
    fun sendFeeTier(tier: String): String = "send_fee_${tier.lowercase()}"

    // WalletConnect (KAN-126) — testTags mirror the wc_ i18n keys (SPEC_WC §5).
    const val WC_PAIRING = "wc_pairing"
    const val WC_PAIRING_SCAN = "wc_pairing_scan"
    const val WC_PAIRING_PASTE = "wc_pairing_paste"
    const val WC_PAIRING_INPUT = "wc_pairing_input"
    const val WC_PAIRING_CONNECT = "wc_pairing_connect"
    const val WC_ERROR = "wc_error"
    const val WC_PROPOSAL = "wc_proposal"
    const val WC_VERIFY = "wc_verify"
    const val WC_APPROVE = "wc_approve"
    const val WC_REJECT = "wc_reject"
}
