package com.veltrix.hom.vnext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stage 70 Store. Server values remain authoritative for price, balance, ownership and equip. */
@Composable
fun RootStoreWorldStage70(
    store: StoreCatalogUiModel?,
    inventory: List<InventoryItemUiModel>,
    avatars: List<AvatarCatalogUiModel>,
    game: GameProfileUiModel?,
    feedback: MutationFeedback?,
    onMenu: () -> Unit,
    onPurchase: (String) -> Unit,
    onEquipAvatar: (String, Long) -> Unit,
) {
    var selectedAvatarId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(avatars, game?.avatarId) {
        if (selectedAvatarId == null || avatars.none { it.avatarId == selectedAvatarId }) {
            selectedAvatarId = avatars.firstOrNull { it.equipped }?.avatarId
                ?: game?.avatarId?.takeIf { id -> avatars.any { it.avatarId == id } }
                ?: avatars.firstOrNull()?.avatarId
        }
    }
    val selected = avatars.firstOrNull { it.avatarId == selectedAvatarId }
    val balance = store?.coinBalance ?: game?.coinBalance ?: 0L

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 108.dp)
            .testTag("store-stage70"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StoreHeader70(onMenu)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Character Studio", color = KineticColor.Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Preview freely. Ownership and Coin truth come from Veltrix server.", color = KineticColor.Muted)
            }
            KineticGlass(Modifier.testTag("store-balance"), radius = 20.dp, strong = true) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
                    Text("COINS", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(balance.toString(), color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        KineticGlass(Modifier.fillMaxWidth().height(282.dp).testTag("store-preview"), radius = 32.dp, strong = true) {
            Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                KineticAvatar(selected?.avatarId ?: game?.avatarId, Modifier.size(158.dp), selected?.name ?: "Current Veltrix character")
                Spacer(Modifier.height(6.dp))
                Text(selected?.name ?: "Your character", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(selected?.tier?.lowercase()?.replaceFirstChar { it.uppercase() } ?: game?.avatarTier.orEmpty(), color = KineticColor.Muted)
                when {
                    selected == null -> Text("Character catalog is syncing.", color = KineticColor.Muted)
                    selected.equipped -> Text("EQUIPPED", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("store-avatar-equipped"))
                    selected.owned -> Button(
                        onClick = { game?.let { onEquipAvatar(selected.avatarId, it.avatarRevision) } },
                        enabled = game != null,
                        modifier = Modifier.testTag("store-equip-${selected.avatarId}"),
                    ) { Text("Equip character") }
                    else -> Text(selected.storePrice?.let { "$it coins in Store catalog" } ?: "Locked by current catalog rules", color = KineticColor.Ember, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Text("CHARACTER COLLECTION", color = KineticColor.Ember, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("store-avatar-list"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            avatars.forEach { avatar ->
                val chosen = avatar.avatarId == selectedAvatarId
                KineticGlass(
                    Modifier
                        .width(136.dp)
                        .clickable { selectedAvatarId = avatar.avatarId }
                        .testTag("store-avatar-${avatar.avatarId}")
                        .semantics { role = Role.Button; contentDescription = "Preview ${avatar.name}, ${avatar.tier} tier, ${if (avatar.equipped) "equipped" else if (avatar.owned) "owned" else "not owned"}" },
                    radius = 23.dp,
                    strong = chosen,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        KineticAvatar(avatar.avatarId, Modifier.size(74.dp), avatar.name)
                        Text(avatar.name, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                avatar.equipped -> "Equipped"
                                avatar.owned -> "Owned"
                                avatar.storePrice != null -> "${avatar.storePrice} coins"
                                else -> "Locked"
                            },
                            color = if (avatar.equipped) KineticColor.Mint else KineticColor.Muted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Text("STORE CATALOG", color = KineticColor.Ember, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (store == null) {
            Text("Loading fresh Store catalog…", color = KineticColor.Muted)
        } else if (store.items.isEmpty()) {
            Text("No catalog items are currently offered.", color = KineticColor.Muted, modifier = Modifier.testTag("store-empty-catalog"))
        } else {
            store.items.forEach { item -> StoreItem70(item, balance, onPurchase) }
        }

        if (feedback?.success == false) {
            KineticGlass(Modifier.fillMaxWidth().testTag("store-action-error"), radius = 20.dp) {
                Text(feedback.message ?: feedback.code ?: "Store action could not complete.", Modifier.padding(14.dp), color = KineticColor.Danger)
            }
        }
        Text("${inventory.size} owned customization items", color = KineticColor.Muted, modifier = Modifier.testTag("store-inventory-count"))
    }
}

@Composable
private fun StoreHeader70(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMenu, modifier = Modifier.testTag("store-menu")) { Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black) }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Store", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("PREVIEW · COLLECT · EQUIP", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StoreItem70(item: StoreItemUiModel, balance: Long, onPurchase: (String) -> Unit) {
    val canAfford = balance >= item.priceCoins
    KineticGlass(Modifier.fillMaxWidth().testTag("store-item-${item.itemId}"), radius = 23.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawCircle(KineticColor.Ember.copy(alpha = .15f))
                    drawCircle(KineticColor.Ember.copy(alpha = .7f), radius = size.minDimension * .16f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(friendlyType70(item.itemType), color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Text("${item.priceCoins} coins", color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
                item.requirements.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            when {
                item.owned -> Text("OWNED", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("store-owned-${item.itemId}"))
                !item.available -> Text("LOCKED", color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                else -> OutlinedButton(
                    onClick = { onPurchase(item.itemId) },
                    enabled = canAfford,
                    modifier = Modifier.testTag("store-buy-${item.itemId}").semantics { contentDescription = if (canAfford) "Buy for ${item.priceCoins} coins" else "Requires ${item.priceCoins} coins; current balance $balance" },
                ) { Text(if (canAfford) "Buy" else "Need coins") }
            }
        }
    }
}

private fun friendlyType70(raw: String): String = raw.lowercase().replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
