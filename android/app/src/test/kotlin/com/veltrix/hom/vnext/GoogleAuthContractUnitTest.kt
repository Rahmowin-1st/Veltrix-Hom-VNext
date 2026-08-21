package com.veltrix.hom.vnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthContractUnitTest {
    @Test
    fun acceptedGoogleEndpointIsBuildConfigured() {
        assertEquals("/v1/auth/google", BuildConfig.VELTRIX_GOOGLE_AUTH_ENDPOINT)
    }

    @Test
    fun typedStoreAvailabilityNeverNeedsRawRuleJsonForUserCopy() {
        val item = StoreItemUiModel(
            itemId = "internal-avatar-id",
            itemType = "AVATAR",
            priceCoins = 350,
            owned = false,
            available = false,
            requirements = "{\"minLevel\":5}",
            metadata = "{\"internal\":true}",
            displayName = "Scholar Orbit",
            availability = StoreAvailabilityUiModel(
                state = "LOCKED",
                reasonCode = "LEVEL_REQUIRED",
                requiredLevel = 5,
            ),
        )
        assertEquals("Unlocks at Level 5", item.userFacingAvailability(balance = 1_000))
        assertTrue(item.userFacingAvailability(1_000)?.contains('{') == false)
        assertTrue(item.userFacingAvailability(1_000)?.contains("minLevel") == false)
    }

    @Test
    fun skuLikeAvatarNamesAreNeverExposedAsProductIdentity() {
        val avatar = AvatarCatalogUiModel(
            avatarId = "noob-002",
            name = "Noob 002",
            assetKey = "avatar-noob-002",
            tier = "NOOB",
            owned = true,
            equipped = false,
            storePrice = null,
            catalogVersion = "test",
            identityMetadata = "{}",
        )
        val display = avatarDisplayName70(avatar)
        assertTrue(display.isNotBlank())
        assertTrue(!display.contains("noob", ignoreCase = true))
        assertTrue(!display.contains("identity", ignoreCase = true))
        assertTrue(!display.contains("002"))
        assertTrue(!display.equals(avatar.avatarId, ignoreCase = true))
    }
}
