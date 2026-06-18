package com.tneff.cyppie.wallet

import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.secp256k1.Secp256k1

/**
 * JVM + Android actual of [EvmCrypto]. Backed by ACINQ bitcoin-kmp / secp256k1-kmp (consumed as
 * the plain `-jvm` jar; native secp256k1 supplied per target by `secp256k1-kmp-jni-jvm` /
 * `secp256k1-kmp-jni-android`). Identical to the iOS actual by design.
 */
internal actual object EvmCrypto {

    actual fun isValidMnemonic(words: List<String>): Boolean =
        runCatching { MnemonicCode.validate(words) }.isSuccess

    actual fun mnemonicToSeed(words: List<String>, passphrase: String): ByteArray =
        MnemonicCode.toSeed(words, passphrase)

    actual fun entropyToMnemonic(entropy: ByteArray): List<String> =
        MnemonicCode.toMnemonics(entropy)

    actual fun derivePrivateKey(seed: ByteArray, path: String): ByteArray =
        DeterministicWallet.generate(seed).derivePrivateKey(path).privateKey.value.toByteArray()

    actual fun publicKey(privateKey: ByteArray): ByteArray =
        Secp256k1.pubkeyCreate(privateKey) // 65-byte uncompressed (0x04 || X || Y)

    actual fun sign(privateKey: ByteArray, hash: ByteArray): RecoverableSignature {
        val sig = Secp256k1.sign(hash, privateKey)               // 64-byte compact r||s
        val (normalized, _) = Secp256k1.signatureNormalize(sig)  // enforce low-S (EIP-2)
        val target = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privateKey))
        var recId = -1
        for (id in 0..3) {
            val recovered = runCatching { Secp256k1.ecdsaRecover(normalized, hash, id) }.getOrNull() ?: continue
            if (Secp256k1.pubKeyCompress(recovered).contentEquals(target)) {
                recId = id
                break
            }
        }
        if (recId < 0) throw WalletKeyException.InvalidSigningInput("Could not determine recovery id")
        return RecoverableSignature(
            r = normalized.copyOfRange(0, 32),
            s = normalized.copyOfRange(32, 64),
            recId = recId,
        )
    }

    actual fun recoverPublicKey(hash: ByteArray, signature: RecoverableSignature): ByteArray =
        Secp256k1.ecdsaRecover(signature.r + signature.s, hash, signature.recId)
}
