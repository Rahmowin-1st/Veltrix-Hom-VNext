package com.veltrix.hom.vnext.server

import kotlinx.serialization.Serializable

@Serializable data class GameProfileSnapshotResponse(
    val accountId:String,
    val level:Int,
    val lifetimeXp:Long,
    val currentLevelXp:Long,
    val nextLevelXp:Long,
    val xpProgress:Double,
    val levelCurveVersion:String,
    val rewardPolicyVersion:String,
    val coinBalance:Long,
    val currentConsistency:Int,
    val longestConsistency:Int,
    val equippedAvatar:AvatarStateResponse,
    val achievementSummary:AchievementSummaryResponse,
    val inventorySummary:InventorySummaryResponse,
    val mapEligibility:MapEligibilityResponse,
    val mapState:String,
    val currentUnit:String?,
    val currentSeason:SeasonResponse?,
    val seasonProgress:SeasonProgressResponse?,
    val gamingStatsSummary:GamingStatisticsResponse,
    val revision:Long,
)
@Serializable data class AvatarStateResponse(val avatarId:String,val assetKey:String,val tier:String,val ownership:String,val unlockState:String,val requirements:Map<String,String> = emptyMap(),val equipped:Boolean=false,val revision:Long=1)
@Serializable data class AchievementSummaryResponse(val unlocked:Int,val inProgress:Int,val total:Int)
@Serializable data class InventorySummaryResponse(val uniqueItems:Int,val avatars:Int)
@Serializable data class MapEligibilityResponse(val eligible:Boolean,val levelRequirement:Int,val memoryRequirement:String,val levelSatisfied:Boolean,val memorySatisfied:Boolean,val unlockState:String)
@Serializable data class LedgerEntryResponse(val id:String,val amount:Long,val entryType:String,val source:String,val sourceEventId:String?,val policyVersion:String,val reason:String,val createdAt:String)
@Serializable data class AchievementResponse(val achievementId:String,val version:Int,val category:String,val hidden:Boolean,val progress:Long,val state:String,val unlockedAt:String?,val revision:Long)
@Serializable data class InventoryItemResponse(val itemId:String,val type:String,val catalogVersion:String,val ownershipSource:String,val acquiredAt:String,val seasonScope:String?,val quantity:Long,val metadata:String,val revision:Long)
@Serializable data class AvatarCatalogResponse(val avatarId:String,val assetKey:String,val tier:String,val owned:Boolean,val equipped:Boolean,val unlockState:String,val storePrice:Long?,val catalogVersion:String,val requirements:String)
@Serializable data class EquipAvatarRequest(val avatarId:String,val expectedRevision:Long)
@Serializable data class StoreAvailabilityResponse(val state:String,val reasonCode:String,val requiredLevel:Int?=null,val requiredSeasonId:String?=null,val requiredAchievementId:String?=null)
@Serializable data class StoreItemResponse(val itemId:String,val itemType:String,val catalogVersion:String,val priceCoins:Long,val owned:Boolean,val available:Boolean,val requirements:String,val metadata:String,val displayName:String,val availability:StoreAvailabilityResponse)
@Serializable data class StoreCatalogResponse(val catalogVersion:String,val coinBalance:Long,val items:List<StoreItemResponse>)
@Serializable data class StorePurchaseRequest(val itemId:String,val idempotencyKey:String)
@Serializable data class StorePurchaseResponse(val purchaseId:String,val itemId:String,val catalogVersion:String,val authoritativePrice:Long,val currency:String,val status:String,val coinBalance:Long,val ownershipRevision:Long,val idempotentReplay:Boolean=false)
@Serializable data class PersonalMapResponse(val mapId:String?,val mapDefinitionId:String,val mapVersion:Int,val state:String,val generationProvider:String,val generationVersion:String,val eligibility:MapEligibilityResponse,val units:List<MapUnitResponse>,val revision:Long)
@Serializable data class MapUnitResponse(val unitId:String,val ordinal:Int,val semanticKey:String,val titleKey:String,val state:String,val progress:Long,val requiredProgress:Long,val rewardDefinition:String,val revision:Long)
@Serializable data class StartMapUnitRequest(val expectedRevision:Long)
@Serializable data class SeasonResponse(val seasonId:String,val version:Int,val startAt:String,val endAt:String,val state:String,val semanticIdentity:String)
@Serializable data class SeasonProgressResponse(val seasonId:String,val version:Int,val unitsCompleted:Int,val achievementsUnlocked:Int,val xpEarned:Long,val coinsEarned:Long,val state:String,val revision:Long)
@Serializable data class GamingStatisticsResponse(val meaningfulActivities:Long,val xpEarned:Long,val coinsEarned:Long,val coinsSpent:Long,val achievementsUnlocked:Long,val unitsCompleted:Long,val mapsCompleted:Long,val seasonsParticipated:Long,val revision:Long)
@Serializable data class GameStateEventResponse(val eventId:String,val type:String,val timestamp:String,val causationId:String?,val correlationId:String?,val sourceEventId:String?,val resultingRevision:Long,val payloadSchemaVersion:Int,val payload:String)
@Serializable data class RewardProcessResponse(val processed:Int)
@Serializable data class CoinReconciliationResponse(val projectedBalance:Long,val ledgerBalance:Long,val matches:Boolean)
