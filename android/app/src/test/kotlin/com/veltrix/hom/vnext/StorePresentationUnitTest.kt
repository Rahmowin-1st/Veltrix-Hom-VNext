package com.veltrix.hom.vnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorePresentationUnitTest {
    private fun avatar(name: String, id: String = "noob-default-01", tier: String = "NOOB") = AvatarCatalogUiModel(
        avatarId = id, name = name, assetKey = "avatar/$id", tier = tier, owned = true, equipped = true,
        storePrice = null, catalogVersion = "test", identityMetadata = "{}",
    )

    @Test fun internalCatalogNameFallsBackToVeltrixFamilyName() {
        val shown = avatarDisplayName70(avatar("Noob Default"))
        assertFalse(shown.contains("noob", ignoreCase = true))
        assertFalse(shown.contains("default", ignoreCase = true))
        assertFalse(shown.contains("identity", ignoreCase = true))
    }

    @Test fun userFacingCatalogNameIsPreserved() {
        assertEquals("Aurora", avatarDisplayName70(avatar("Aurora", id = "avatar-aurora", tier = "ELITE")))
    }

    @Test fun internalNoobTierUsesStarterPresentationLabel() {
        assertEquals("Starter", avatarTierLabel70("NOOB"))
    }
}
