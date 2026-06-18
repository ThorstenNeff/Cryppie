package com.tneff.cyppie.wallet

/**
 * Sealed failure types for the L1 key/account layer (KAN-56).
 *
 * Sealed so callers and tests can assert deterministically on negative cases without
 * matching on free-text messages. Messages never contain key material or seed bytes.
 */
sealed class WalletKeyException(message: String) : Exception(message) {

    /** The mnemonic is not a valid BIP-39 phrase (bad word, length, or checksum). */
    class InvalidMnemonic(message: String) : WalletKeyException(message)

    /** An account index is out of the supported BIP-44 range (non-negative, hardened-safe). */
    class InvalidDerivationIndex(message: String) : WalletKeyException(message)

    /** A derivation path is malformed (reserved for when paths become configurable post-MVP). */
    class InvalidPath(message: String) : WalletKeyException(message)

    /** The seed is not available — the wallet is locked (unlock→sign gate, ADR-0009). */
    class WalletLocked(message: String = "Seed is locked") : WalletKeyException(message)

    /** A 32-byte hash to sign / signing input had the wrong shape. */
    class InvalidSigningInput(message: String) : WalletKeyException(message)
}
