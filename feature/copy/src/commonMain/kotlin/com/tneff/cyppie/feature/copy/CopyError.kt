package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.Composable
import com.tneff.cyppie.feature.copy.generated.resources.Res
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_address
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_budget
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_password
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_self
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_submit
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_verify
import org.jetbrains.compose.resources.stringResource

/** A Copy / Follow-Trader error code (KAN-155). VMs expose the code; the screen resolves the copy_err_* string. */
enum class CopyError { INVALID_ADDRESS, SELF_COPY, ENTER_BUDGET, WRONG_PASSWORD, VERIFY_FAILED, SUBMIT_FAILED }

@Composable
fun CopyError.text(): String = when (this) {
    CopyError.INVALID_ADDRESS -> stringResource(Res.string.copy_err_address)
    CopyError.SELF_COPY -> stringResource(Res.string.copy_err_self)
    CopyError.ENTER_BUDGET -> stringResource(Res.string.copy_err_budget)
    CopyError.WRONG_PASSWORD -> stringResource(Res.string.copy_err_password)
    // Fail-closed: on-device grant verification failed → we refuse to sign.
    CopyError.VERIFY_FAILED -> stringResource(Res.string.copy_err_verify)
    CopyError.SUBMIT_FAILED -> stringResource(Res.string.copy_err_submit)
}
