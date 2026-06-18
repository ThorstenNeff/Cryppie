package com.tneff.cyppie.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseOperationPrompt
import platform.Security.kSecValueData
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.darwin.OSStatus

/**
 * iOS Keychain-backed [SecureKeyStore]. Self-contained: the secret is stored as a generic-password
 * item guarded by `SecAccessControl(.biometryCurrentSet)` with
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, so the **system** presents Face ID / Touch ID on
 * [retrieve] (`SecItemCopyMatching`) — no app UI is needed (unlike Android, where ONB-9 drives the
 * `BiometricPrompt`). Behaviour is device-verified by the Test agent's audit gate.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureKeyStore(
    private val service: String = "com.tneff.cyppie.wallet",
    private val prompt: String = "Unlock your wallet",
) : SecureKeyStore {

    override val isHardwareBacked: Boolean = true

    override suspend fun protect(alias: String, secret: ByteArray) {
        clear(alias) // replace any existing item
        val access = SecAccessControlCreateWithFlags(
            allocator = null,
            protection = kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            flags = kSecAccessControlBiometryCurrentSet,
            error = null,
        ) ?: throw StorageException.KeyStoreUnavailable("Could not create access control")

        val data = secret.toNSData()
        val valueRef = CFBridgingRetain(data)
        try {
            val query = newQuery(alias) {
                CFDictionaryAddValue(it, kSecValueData, valueRef)
                CFDictionaryAddValue(it, kSecAttrAccessControl, access)
            }
            try {
                val status = SecItemAdd(query, null)
                if (status != errSecSuccess) {
                    throw StorageException.KeyStoreUnavailable("Keychain add failed (OSStatus $status)")
                }
            } finally {
                CFRelease(query)
            }
        } finally {
            CFBridgingRelease(valueRef)
            CFRelease(access)
        }
    }

    override suspend fun retrieve(alias: String): ByteArray? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = newQuery(alias) {
            CFDictionaryAddValue(it, kSecReturnData, kCFBooleanTrueRef())
            CFDictionaryAddValue(it, kSecMatchLimit, kSecMatchLimitOne)
            val promptRef = CFBridgingRetain(prompt)
            CFDictionaryAddValue(it, kSecUseOperationPrompt, promptRef)
            CFRelease(promptRef) // dict retains; drop our +1 (M4)
        }
        try {
            val status: OSStatus = SecItemCopyMatching(query, result.ptr)
            when (status) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
                errSecItemNotFound -> null
                else -> throw StorageException.KeyStoreUnavailable("Keychain read failed (OSStatus $status)")
            }
        } finally {
            CFRelease(query)
        }
    }

    override suspend fun clear(alias: String) {
        val query = newQuery(alias) {}
        try {
            SecItemDelete(query) // ignore errSecItemNotFound
        } finally {
            CFRelease(query)
        }
    }

    /**
     * Builds a base generic-password query for [alias], then applies [extra]. Each `CFBridgingRetain`
     * temporary is released after `CFDictionaryAddValue` — the dict keeps its own retain via the
     * type value-callbacks, so dropping our +1 avoids a per-call leak (M4).
     */
    private inline fun newQuery(
        alias: String,
        extra: (platform.CoreFoundation.CFMutableDictionaryRef?) -> Unit,
    ): platform.CoreFoundation.CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(alias)
        CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        extra(dict)
        return dict
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun kCFBooleanTrueRef() = platform.CoreFoundation.kCFBooleanTrue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
