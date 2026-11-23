package com.arflix.tv.ui.components

import com.arflix.tv.data.model.StreamSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamSelectorIdentityTest {

    @Test
    fun duplicateVisibleAddonLabelsKeepUniqueComposeKeys() {
        val tabs = buildSourceAddonTabs(
            streams = listOf(
                stream(addonId = "stravo-instance-one-ac49"),
                stream(addonId = "stravo-instance-two-ac49"),
            ),
            addonOrderedIds = emptyList(),
        )

        assertThat(tabs.map { it.label })
            .containsExactly("Stravo #AC49", "Stravo #AC49 (2)")
            .inOrder()
        assertThat(sourceAddonTabKeys(tabs)).containsNoDuplicates()
    }

    @Test
    fun addonRailKeysUseFullAddonIdentityInsteadOfDisplayText() {
        val tabs = listOf(
            SourceAddonTab(id = "instance-one", label = "Duplicate"),
            SourceAddonTab(id = "instance-two", label = "Duplicate"),
        )

        assertThat(sourceAddonTabKeys(tabs))
            .containsExactly(
                "source_addon:all",
                "source_addon:id:instance-one",
                "source_addon:id:instance-two",
            )
            .inOrder()
    }

    @Test
    fun identicalStreamRowsStillReceiveUniqueKeys() {
        val stream = stream(addonId = "stravo")

        assertThat(sourceStreamRowKey(stream, 0))
            .isNotEqualTo(sourceStreamRowKey(stream, 1))
    }

    @Test
    fun rowKeysSurviveInsertionFilteringAndMetadataUpdates() {
        val one = stream("one").copy(url = "https://example.invalid/one")
        val two = stream("two").copy(url = "https://example.invalid/two")
        val original = sourceStreamRowKeys(listOf(one, two))
        assertThat(sourceStreamRowKeys(listOf(two, one))).containsExactly(original[1], original[0]).inOrder()
        assertThat(sourceStreamRowKeys(listOf(two))).containsExactly(original[1])
        assertThat(sourceStreamRowKeys(listOf(one.copy(size = "24 GB", quality = "2160p"), two)))
            .containsExactlyElementsIn(original).inOrder()
    }

    @Test
    fun duplicateSourcesDoNotGiveUnrelatedRowsNewKeys() {
        val one = stream("one")
        val two = stream("two")
        val original = sourceStreamRowKeys(listOf(one, one, two))
        assertThat(original).containsNoDuplicates()
        val inserted = sourceStreamRowKeys(listOf(stream("new"), one, one, two))
        assertThat(inserted.drop(1)).containsExactlyElementsIn(original).inOrder()
    }

    @Test
    fun playbackIdentityDistinguishesTorrentFilesAndDoesNotExposeCredentials() {
        val one = stream("one").copy(url = "https://example.invalid/?password=secret", infoHash = "hash", fileIdx = 0)
        assertThat(sourceStreamRowKey(one, 0)).doesNotContain("secret")
        assertThat(sourceStreamRowKey(one.copy(fileIdx = 1), 0)).isNotEqualTo(sourceStreamRowKey(one, 0))
    }

    private fun stream(addonId: String) = StreamSource(
        source = "source",
        addonName = "Stravo - TorBox",
        addonId = addonId,
        quality = "4K",
        size = "12 GB",
    )
}
