package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.rpc.NftItem
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.nft_back
import com.tneff.cyppie.feature.wallet.generated.resources.nft_collection_unknown
import com.tneff.cyppie.feature.wallet.generated.resources.nft_degraded
import com.tneff.cyppie.feature.wallet.generated.resources.nft_empty
import com.tneff.cyppie.feature.wallet.generated.resources.nft_error
import com.tneff.cyppie.feature.wallet.generated.resources.nft_retry
import com.tneff.cyppie.feature.wallet.generated.resources.nft_title
import org.jetbrains.compose.resources.stringResource

/** Threshold (items from the end) at which the next page is requested. */
private const val NFT_PREFETCH_DISTANCE = 6

/**
 * KAN-105 — read-only NFT grid (PRD-03) over [NftViewModel]. Adaptive lazy grid with `nextPageKey`
 * paging; spam excluded; only Alchemy-cached media is shown (privacy). The media tile is a placeholder
 * until the image-loading library is wired (pending PO decision; the cached `imageUrl` is ready).
 */
@Composable
fun NftGridScreen(
    onBack: () -> Unit,
    viewModel: NftViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()

    // Lazy paging: request the next page as the tail of the grid scrolls into view.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { lastVisible ->
            val current = viewModel.uiState
            if (current is NftUiState.Content && current.canLoadMore &&
                lastVisible >= current.items.size - NFT_PREFETCH_DISTANCE
            ) {
                viewModel.loadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            CryptasaTopAppBar(
                title = stringResource(Res.string.nft_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.nft_back),
            )

            if (state is NftUiState.Content && state.degraded) {
                CryptasaBanner(
                    title = stringResource(Res.string.nft_degraded),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.padding(horizontal = spacing.xl).testTag(WalletTestTags.NFT_DEGRADED_BANNER),
                )
            }

            when (state) {
                NftUiState.Loading -> Centered { ProgressRing(diameter = 48.dp) }
                NftUiState.Empty -> Centered {
                    Text(
                        text = stringResource(Res.string.nft_empty),
                        style = CryptasaTheme.typography.body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag(WalletTestTags.NFT_EMPTY),
                    )
                }
                NftUiState.Error -> Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(
                            text = stringResource(Res.string.nft_error),
                            style = CryptasaTheme.typography.body,
                            color = colors.danger,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag(WalletTestTags.NFT_ERROR),
                        )
                        CryptasaButton(
                            text = stringResource(Res.string.nft_retry),
                            onClick = viewModel::retry,
                            modifier = Modifier.testTag(WalletTestTags.NFT_RETRY),
                        )
                    }
                }
                is NftUiState.Content -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    contentPadding = PaddingValues(spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxSize().testTag(WalletTestTags.NFT_GRID),
                ) {
                    items(state.items, key = { "${it.contract.value}_${it.tokenId}" }) { nft ->
                        NftTile(nft)
                    }
                }
            }
        }
    }
}

@Composable
private fun NftTile(nft: NftItem) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletTestTags.nftItem(nft.contract.value, nft.tokenId)),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // Media slot — only the Alchemy-cached `imageUrl` is loaded (privacy: never the raw token URI).
        // Missing/un-cached media keeps the surfaceVariant placeholder (also Coil's load/error fallback).
        val mediaModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(CryptasaTheme.radius.lg))
            .background(colors.surfaceVariant)
        val imageUrl = nft.imageUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = nft.name ?: nft.collectionName,
                contentScale = ContentScale.Crop,
                modifier = mediaModifier,
            )
        } else {
            Box(modifier = mediaModifier)
        }
        Text(
            text = nft.name ?: "#${nft.tokenId}",
            style = CryptasaTheme.typography.label,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = nft.collectionName ?: stringResource(Res.string.nft_collection_unknown),
            style = CryptasaTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
