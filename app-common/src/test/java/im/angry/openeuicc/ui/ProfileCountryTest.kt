package im.angry.openeuicc.ui

import im.angry.openeuicc.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCountryTest {
    @Test fun recognizesProfileOwnerRegionsFromTwoDigitMnc() {
        val examples = mapOf(
            "64F000" to "CN", // MCC 460, MNC 00
            "54F421" to "HK", // MCC 454, MNC 12
            "54F510" to "MO", // MCC 455, MNC 01
            "64F629" to "TW", // MCC 466, MNC 92
            "44F001" to "JP", // MCC 440, MNC 10
            "54F080" to "KR", // MCC 450, MNC 08
            "32F451" to "GB", // MCC 234, MNC 15
            "25F510" to "SG", // MCC 525, MNC 01
            "62F220" to "DE", // MCC 262, MNC 02
            "56F510" to "ZA", // MCC 655, MNC 01
            "27F450" to "BR", // MCC 724, MNC 05
            "05F510" to "AU", // MCC 505, MNC 01
        )
        for ((operatorId, region) in examples) {
            assertEquals(operatorId, region, ProfileCountry.fromOperatorId(operatorId))
        }
    }

    @Test fun decodesThreeDigitMncAndSeveralMccsForTheSameRegion() {
        // The third MNC digit occupies the high nibble of the second byte.
        assertEquals("US", ProfileCountry.fromOperatorId("130062")) // 310 / 260
        assertEquals("US", ProfileCountry.fromOperatorId("130184")) // 311 / 480
        assertEquals("US", ProfileCountry.fromOperatorId("130610")) // 316 / 010
        assertEquals("CN", ProfileCountry.fromOperatorId("64F100")) // 461 / 00
        assertEquals("JP", ProfileCountry.fromOperatorId("44F101")) // 441 / 10
        assertEquals("GB", ProfileCountry.fromOperatorId("32F549")) // 235 / 94
    }

    @Test fun acceptsHexadecimalFillerInEitherCase() {
        assertEquals("CN", ProfileCountry.fromOperatorId("64f000"))
        assertEquals("CN", ProfileCountry.fromOperatorId("64F000"))
    }

    @Test fun leavesAbsentUnknownAndInternationalMetadataNeutral() {
        assertNull(ProfileCountry.fromOperatorId(""))
        assertNull(ProfileCountry.fromOperatorId("000000"))
        assertNull(ProfileCountry.fromOperatorId("99F900")) // Unassigned MCC 999
        assertNull(ProfileCountry.fromOperatorId("09F110")) // International MCC 901
    }

    @Test fun rejectsInvalidBcdInsteadOfGuessingACountry() {
        for (invalid in listOf(
            "64F00", "64F0000", " 64F000", "64F000 ",
            "F4F000", "64A000", "640F00", "64F0F0", "64F00F",
            "zzzzzz", "６４F000", "JP", "46000",
        )) {
            assertNull(invalid, ProfileCountry.fromOperatorId(invalid))
        }
    }

    @Test fun usesBundledFlagsAndAFallbackGlobe() {
        assertEquals(R.drawable.flag_cn, ProfileCountry.drawable("CN"))
        assertEquals(R.drawable.flag_jp, ProfileCountry.drawable("jp"))
        assertEquals(R.drawable.flag_us, ProfileCountry.drawable("US"))
        assertEquals(R.drawable.flag_gb, ProfileCountry.drawable("GB"))
        assertEquals(R.drawable.ic_luma_globe, ProfileCountry.drawable(null))
        assertEquals(R.drawable.ic_luma_globe, ProfileCountry.drawable("UN"))
        assertEquals(R.drawable.ic_luma_globe, ProfileCountry.drawable(""))
        assertEquals(R.drawable.ic_luma_globe, ProfileCountry.drawable("not-a-country"))
    }
}
