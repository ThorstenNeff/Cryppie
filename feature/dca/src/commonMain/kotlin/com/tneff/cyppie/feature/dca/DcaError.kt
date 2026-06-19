package com.tneff.cyppie.feature.dca

import androidx.compose.runtime.Composable
import com.tneff.cyppie.feature.dca.generated.resources.Res
import com.tneff.cyppie.feature.dca.generated.resources.dca_err_amount
import com.tneff.cyppie.feature.dca.generated.resources.dca_err_authorize
import com.tneff.cyppie.feature.dca.generated.resources.dca_err_password
import com.tneff.cyppie.feature.dca.generated.resources.dca_err_submit
import org.jetbrains.compose.resources.stringResource

/** A DCA error code (VMs are non-composable → they expose the code; the screen resolves the dca_err_* string). */
enum class DcaError { WRONG_PASSWORD, SUBMIT_FAILED, AUTHORIZE_FAILED, ENTER_AMOUNT }

@Composable
fun DcaError.text(): String = when (this) {
    DcaError.WRONG_PASSWORD -> stringResource(Res.string.dca_err_password)
    DcaError.SUBMIT_FAILED -> stringResource(Res.string.dca_err_submit)
    DcaError.AUTHORIZE_FAILED -> stringResource(Res.string.dca_err_authorize)
    DcaError.ENTER_AMOUNT -> stringResource(Res.string.dca_err_amount)
}
