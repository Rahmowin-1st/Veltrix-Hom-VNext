package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .padding(horizontal = 18.dp)
            .padding(bottom = 108.dp)
            .testTag("store-stage70"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StoreHeader70(onMenu, balance)
        StoreCharacterWorld70(selected, game, onEquipAvatar)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("CHARACTER COLLECTION", color = KineticColor.Ember, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("Preview every identity. Ownership stays server-authoritative.", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).testTag("store-avatar-list"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            avatars.forEach { avatar ->
                val chosen = avatar.avatarId == selectedAvatarId
                KineticGlass(
                    Modifier
                        .width(142.dp)
                        .clickable { selectedAvatarId = avatar.avatarId }
                        .testTag("store-avatar-${avatar.avatarId}")
                        .semantics {
                            role = Role.Button
                            contentDescription = "Preview ${avatarDisplayName70(avatar)}, ${avatarTierLabel70(avatar.tier)} tier, ${if (avatar.equipped) "equipped" else if (avatar.owned) "owned" else "not owned"}"
                        },
                    radius = 24.dp,
                    strong = chosen,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(82.dp)) { KineticAvatar(avatar.avatarId, Modifier.fillMaxSize(), avatarDisplayName70(avatar)) }
                        Text(avatarDisplayName70(avatar), color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                avatar.equipped -> "Equipped"
                                avatar.owned -> "Owned"
                                avatar.storePrice != null -> "${avatar.storePrice} ◈"
                                else -> "Locked"
                            },
                            color = if (avatar.equipped) KineticColor.Mint else if (chosen) KineticColor.Ember else KineticColor.Muted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("CUSTOMIZATION INVENTORY", color = KineticColor.Ember, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("Secondary to your character world — not the Store's visual center.", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (store == null) {
            KineticGlass(radius = 22.dp) { Text("Loading fresh Store catalog…", Modifier.padding(14.dp), color = KineticColor.Muted) }
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
private fun StoreHeader70(onMenu: () -> Unit, balance: Long) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("store-header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(onClick = onMenu, modifier = Modifier.size(48.dp).testTag("store-menu"), radius = 24.dp, strong = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black) }
        }
        Column(Modifier.weight(1f)) {
            Text("Store", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("IDENTITY · PREVIEW · COLLECT", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
        KineticGlass(Modifier.testTag("store-balance"), radius = 18.dp, strong = true) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(balance.toString(), color = KineticColor.Ink, fontWeight = FontWeight.Bold)
                Text("◈", color = KineticColor.Ember, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StoreCharacterWorld70(
    selected: AvatarCatalogUiModel?,
    game: GameProfileUiModel?,
    onEquipAvatar: (String, Long) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(338.dp)
            .clip(RoundedCornerShape(38.dp))
            .drawWithCache {
                val chamber = Brush.linearGradient(
                    listOf(Color(0xFFFFEEDC), Color(0xFFFFFAF6), Color(0xFFEDE9FF), Color(0xFFF3F8FF)),
                    Offset.Zero,
                    Offset(size.width, size.height),
                )
                val spotlight = Brush.radialGradient(
                    listOf(Color.White.copy(.92f), KineticColor.Ember.copy(.22f), Color.Transparent),
                    center = Offset(size.width * .50f, size.height * .34f),
                    radius = size.minDimension * .65f,
                )
                val violet = Brush.radialGradient(
                    listOf(KineticColor.Violet.copy(.20f), Color.Transparent),
                    center = Offset(size.width * .86f, size.height * .22f),
                    radius = size.minDimension * .52f,
                )
                onDrawBehind {
                    drawRect(chamber)
                    drawRect(spotlight)
                    drawRect(violet)
                    // Curved showroom aperture and pedestal establish a world, not a product card.
                    drawArc(
                        Color.White.copy(.62f),
                        198f,
                        144f,
                        false,
                        Offset(size.width * .19f, size.height * .02f),
                        Size(size.width * .62f, size.height * .68f),
                        style = Stroke(1.2.dp.toPx()),
                    )
                    drawArc(
                        KineticColor.Ember.copy(.25f),
                        198f,
                        144f,
                        false,
                        Offset(size.width * .24f, size.height * .07f),
                        Size(size.width * .52f, size.height * .58f),
                        style = Stroke(2.dp.toPx()),
                    )
                    drawOval(Color.White.copy(.52f), Offset(size.width * .22f, size.height * .68f), Size(size.width * .56f, size.height * .15f))
                    drawOval(KineticColor.Ember.copy(.16f), Offset(size.width * .29f, size.height * .71f), Size(size.width * .42f, size.height * .09f))
                    drawCircle(Color.White.copy(.90f), 4.dp.toPx(), Offset(size.width * .86f, size.height * .13f))
                    drawCircle(KineticColor.Violet.copy(.78f), 3.2.dp.toPx(), Offset(size.width * .78f, size.height * .25f))
                }
            }
            .testTag("store-preview"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("CHARACTER STUDIO", color = Color(0xFF9B5C28), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Shape your Veltrix identity", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                selected?.tier?.takeIf { it.isNotBlank() }?.let { tier ->
                    KineticGlass(radius = 17.dp) {
                        Text(avatarTierLabel70(tier), Modifier.padding(horizontal = 10.dp, vertical = 7.dp).testTag("store-tier-label"), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Box(Modifier.size(184.dp)) {
                KineticAvatar(selected?.avatarId ?: game?.avatarId, Modifier.fillMaxSize(), selected?.let(::avatarDisplayName70) ?: "Current Veltrix character")
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(selected?.let(::avatarDisplayName70) ?: "Your character", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            selected == null -> "Catalog syncing"
                            selected.equipped -> "Equipped to your account"
                            selected.owned -> "Owned · ready to equip"
                            selected.storePrice != null -> "${selected.storePrice} coins"
                            else -> "Locked by current catalog rules"
                        },
                        color = if (selected?.equipped == true) KineticColor.Mint else KineticColor.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    selected == null -> Unit
                    selected.equipped -> KineticGlass(Modifier.testTag("store-avatar-equipped"), radius = 18.dp, strong = true) {
                        Text("EQUIPPED", Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = KineticColor.Mint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    selected.owned -> PressableGlass(
                        onClick = { game?.let { onEquipAvatar(selected.avatarId, it.avatarRevision) } },
                        enabled = game != null,
                        modifier = Modifier.height(44.dp).testTag("store-equip-${selected.avatarId}"),
                        radius = 22.dp,
                        strong = true,
                    ) { Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Equip", color = KineticColor.Ink, fontWeight = FontWeight.SemiBold) } }
                }
            }
        }
    }
}

@Composable
private fun StoreItem70(item: StoreItemUiModel, balance: Long, onPurchase: (String) -> Unit) {
    val canAfford = balance >= item.priceCoins
    KineticGlass(Modifier.fillMaxWidth().testTag("store-item-${item.itemId}"), radius = 23.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(KineticColor.Ember.copy(alpha = .17f))
                    drawCircle(Color.White.copy(.82f), radius = size.minDimension * .28f)
                    drawCircle(KineticColor.Ember.copy(alpha = .78f), radius = size.minDimension * .13f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.displayName.takeIf { it.isNotBlank() } ?: friendlyType70(item.itemType), color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Text("${item.priceCoins} ◈", color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
                item.userFacingAvailability(balance)?.let { message ->
                    Text(message, color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("store-availability-${item.itemId}"))
                }
            }
            when {
                item.owned -> Text("OWNED", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("store-owned-${item.itemId}"))
                !item.available -> Text("LOCKED", color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                else -> OutlinedButton(
                    onClick = { onPurchase(item.itemId) },
                    enabled = canAfford,
                    modifier = Modifier.testTag("store-buy-${item.itemId}").semantics {
                        contentDescription = if (canAfford) "Buy for ${item.priceCoins} coins" else "Requires ${item.priceCoins} coins; current balance $balance"
                    },
                ) { Text(if (canAfford) "Buy" else "Need coins") }
            }
        }
    }
}

internal fun avatarDisplayName70(avatar: AvatarCatalogUiModel): String {
    val raw = avatar.name.trim()
    val internalLike = raw.isBlank() ||
        raw.equals(avatar.avatarId, ignoreCase = true) ||
        Regex("^(noob|pro|elite|super|ultra|max|hyperpro|legendary)([ _-]+(default|avatar|identity|\\d+))?$", RegexOption.IGNORE_CASE).matches(raw)
    return if (internalLike) avatarFamilyName70(avatar.avatarId) else raw
}

internal fun avatarTierLabel70(raw: String): String = when (raw.trim().lowercase()) {
    "noob", "default", "core" -> "Starter"
    else -> friendlyType70(raw)
}

private fun avatarFamilyName70(avatarId: String): String {
    val family = (avatarId.hashCode() and Int.MAX_VALUE) % 5
    return listOf("Prism", "Sentinel", "Bloom", "Comet", "Atlas")[family]
}

private fun friendlyType70(raw: String): String = raw
    .lowercase()
    .replace('_', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
