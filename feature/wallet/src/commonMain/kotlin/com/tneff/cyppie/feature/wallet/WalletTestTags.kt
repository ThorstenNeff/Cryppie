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
}
