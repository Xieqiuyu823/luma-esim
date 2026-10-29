package im.angry.openeuicc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoDataRoamingSlotTest {
    @Test
    fun resolvesOnlyTheSubscriptionInTheSwitchedLogicalSlot() {
        val subscriptions = listOf(
            ActiveSubscriptionSlot(subscriptionId = 41, logicalSlotIndex = 0),
            ActiveSubscriptionSlot(subscriptionId = 92, logicalSlotIndex = 1)
        )

        assertEquals(92, activeSubscriptionIdForLogicalSlot(subscriptions, logicalSlotIndex = 1))
    }

    @Test
    fun skipsMissingOrAmbiguousSlotMappings() {
        assertNull(activeSubscriptionIdForLogicalSlot(emptyList(), logicalSlotIndex = 0))
        assertNull(
            activeSubscriptionIdForLogicalSlot(
                listOf(
                    ActiveSubscriptionSlot(subscriptionId = 41, logicalSlotIndex = 0),
                    ActiveSubscriptionSlot(subscriptionId = 42, logicalSlotIndex = 0)
                ),
                logicalSlotIndex = 0
            )
        )
    }

    @Test
    fun usesPortIndexToDisambiguateMultipleSubscriptionsOnOneSlot() {
        val subscriptions = listOf(
            ActiveSubscriptionSlot(subscriptionId = 41, logicalSlotIndex = 0, portIndex = 0),
            ActiveSubscriptionSlot(subscriptionId = 42, logicalSlotIndex = 0, portIndex = 1)
        )

        assertEquals(42, activeSubscriptionIdForLogicalSlot(subscriptions, 0, portIndex = 1))
        assertNull(activeSubscriptionIdForLogicalSlot(subscriptions, 0))
    }
}
