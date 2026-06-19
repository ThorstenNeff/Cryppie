package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.rpc.NftItem
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.WalletRepository
import kotlinx.coroutines.launch

/** NFT grid state (KAN-105). */
sealed interface NftUiState {
    data object Loading : NftUiState
    data class Content(val items: List<NftItem>, val canLoadMore: Boolean, val degraded: Boolean) : NftUiState
    data object Empty : NftUiState
    data object Error : NftUiState
}

/**
 * KAN-105 — read-only NFT grid for one account/chain over `WalletRepository.nfts` (Alchemy NFT v3,
 * KAN-88). Lazy paging via `nextPageKey`; spam is excluded at the source and **defensively re-filtered**
 * here; only Alchemy-cached media URLs are surfaced (privacy). NFT data needs an Alchemy key (KAN-104) —
 * until configured the repo reports no client and the grid shows Error/Empty gracefully.
 */
class NftViewModel(
    private val repository: WalletRepository,
    private val accountIndex: Int,
    private val chain: EvmChain,
) : ViewModel() {

    var uiState: NftUiState by mutableStateOf(NftUiState.Loading)
        private set
    var loadingMore: Boolean by mutableStateOf(false)
        private set

    private val items: SnapshotStateList<NftItem> = emptyList<NftItem>().toMutableStateList()
    private var nextPageKey: String? = null

    init {
        loadFirst()
    }

    fun retry() = loadFirst()

    private fun loadFirst() {
        uiState = NftUiState.Loading
        items.clear()
        nextPageKey = null
        viewModelScope.launch {
            runCatching { repository.nfts(accountIndex, chain, null) }
                .onSuccess { page ->
                    items += page.items.filter { !it.isSpam }
                    nextPageKey = page.nextPageKey
                    uiState = if (items.isEmpty()) NftUiState.Empty else content(page.degraded)
                }
                .onFailure { uiState = NftUiState.Error }
        }
    }

    fun loadMore() {
        val key = nextPageKey ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { repository.nfts(accountIndex, chain, key) }
                .onSuccess { page ->
                    items += page.items.filter { !it.isSpam }
                    nextPageKey = page.nextPageKey
                    uiState = content(page.degraded)
                }
                // A failed *next* page keeps what we have (degraded), not a full-screen error.
                .onFailure { uiState = content(degraded = true) }
            loadingMore = false
        }
    }

    private fun content(degraded: Boolean) =
        NftUiState.Content(items = items, canLoadMore = nextPageKey != null, degraded = degraded)
}
