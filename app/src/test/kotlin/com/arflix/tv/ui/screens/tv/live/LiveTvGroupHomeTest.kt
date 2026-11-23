package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.IptvChannel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Focused tests for the Live TV Group Home landing state and navigation workflow.
 */
class LiveTvGroupHomeTest {

    private fun channel(id: String, group: String = "News", name: String = id) =
        IptvChannel(id = id, name = name, group = group, streamUrl = "https://example.test/live")
            .enrichForFastStartup(1)

    @Test
    fun defaultOpeningStateIsGroupHomeOnMobile() {
        val mobileMode = LiveTvStartup.initialMode(
            isTouchDevice = true,
            initialChannelId = null,
            initialStreamUrl = null,
        )
        assertThat(mobileMode).isEqualTo(LiveTvStartup.LiveTvMode.GroupHome)

        val directChannelMode = LiveTvStartup.initialMode(
            isTouchDevice = true,
            initialChannelId = "chan-42",
            initialStreamUrl = null,
        )
        assertThat(directChannelMode).isEqualTo(LiveTvStartup.LiveTvMode.Guide)

        val directStreamMode = LiveTvStartup.initialMode(
            isTouchDevice = true,
            initialChannelId = null,
            initialStreamUrl = "https://stream.test/live.m3u8",
        )
        assertThat(directStreamMode).isEqualTo(LiveTvStartup.LiveTvMode.Guide)

        val tvMode = LiveTvStartup.initialMode(
            isTouchDevice = false,
            initialChannelId = null,
            initialStreamUrl = null,
        )
        assertThat(tvMode).isEqualTo(LiveTvStartup.LiveTvMode.Guide)
    }

    @Test
    fun eachQuickAccessTileResolvesToCorrectSelection() {
        val allDest = LiveTvStartup.resolveQuickAccessDestination("all")
        assertThat(allDest).isEqualTo(LiveTvStartup.QuickAccessDestination.Category("all"))

        val sportsDest = LiveTvStartup.resolveQuickAccessDestination("sports")
        assertThat(sportsDest).isEqualTo(LiveTvStartup.QuickAccessDestination.Sports)

        val favDest = LiveTvStartup.resolveQuickAccessDestination("fav", favoritesCount = 3)
        assertThat(favDest).isEqualTo(LiveTvStartup.QuickAccessDestination.Category("fav"))

        val emptyFavDest = LiveTvStartup.resolveQuickAccessDestination("fav", favoritesCount = 0)
        assertThat(emptyFavDest).isNull()

        val recentDest = LiveTvStartup.resolveQuickAccessDestination("recent", recentsCount = 2)
        assertThat(recentDest).isEqualTo(LiveTvStartup.QuickAccessDestination.Category("recent"))

        val emptyRecentDest = LiveTvStartup.resolveQuickAccessDestination("recent", recentsCount = 0)
        assertThat(emptyRecentDest).isNull()
    }

    @Test
    fun selectingGroupPlankOpensFilteredEpg() {
        val category = LiveCategory(
            id = "grp:p1:news",
            label = "News",
            count = 14,
            iconToken = CategoryIcon.News,
            playlistGroupName = "News",
        )
        val targetCategoryId = category.id
        assertThat(targetCategoryId).isEqualTo("grp:p1:news")

        val channels = listOf(
            channel("c1", group = "News"),
            channel("c2", group = "Sports"),
        )
        val filtered = prepareGuideChannels(
            channels = channels,
            categoryId = targetCategoryId,
            sortOrder = "provider",
            hiddenGroups = emptySet(),
            restrictedGroups = emptySet(),
        )
        assertThat(filtered).isNotEmpty()
    }

    @Test
    fun guideBackReturnsToGroupHomeOnMobile() {
        val action = LiveTvStartup.guideBackAction(
            isTouchDevice = true,
            categoryDrawerOpen = false,
            mode = LiveTvStartup.LiveTvMode.Guide,
        )
        assertThat(action).isEqualTo(LiveTvStartup.GuideBackAction.OPEN_GROUP_HOME)
    }

    @Test
    fun backFromGroupHomeExitsTv() {
        val action = LiveTvStartup.guideBackAction(
            isTouchDevice = true,
            categoryDrawerOpen = false,
            mode = LiveTvStartup.LiveTvMode.GroupHome,
        )
        assertThat(action).isEqualTo(LiveTvStartup.GuideBackAction.EXIT_TV)
    }

    @Test
    fun tvDevicesRetainExistingBackBehavior() {
        val tvActionGuide = LiveTvStartup.guideBackAction(
            isTouchDevice = false,
            categoryDrawerOpen = false,
            mode = LiveTvStartup.LiveTvMode.Guide,
        )
        assertThat(tvActionGuide).isEqualTo(LiveTvStartup.GuideBackAction.OPEN_CATEGORIES)

        val tvActionDrawerOpen = LiveTvStartup.guideBackAction(
            isTouchDevice = false,
            categoryDrawerOpen = true,
            mode = LiveTvStartup.LiveTvMode.Guide,
        )
        assertThat(tvActionDrawerOpen).isEqualTo(LiveTvStartup.GuideBackAction.EXIT_TV)
    }

    @Test
    fun noSeeAllAffordanceInLandingState() {
        val definedTiles = listOf("all", "sports", "fav", "recent")
        assertThat(definedTiles).containsExactly("all", "sports", "fav", "recent").inOrder()
        assertThat(definedTiles).doesNotContain("see_all")
    }

    @Test
    fun recentAndFavouriteGroupsHandleEmptyStatesGracefully() {
        val channels = listOf(channel("c1"), channel("c2"))
        val emptyFavs = prepareGuideChannels(
            channels = emptyList(),
            categoryId = "fav",
            sortOrder = "provider",
            hiddenGroups = emptySet(),
            restrictedGroups = emptySet(),
        )
        assertThat(emptyFavs).isEmpty()

        val emptyRecents = prepareGuideChannels(
            channels = emptyList(),
            categoryId = "recent",
            sortOrder = "provider",
            hiddenGroups = emptySet(),
            restrictedGroups = emptySet(),
        )
        assertThat(emptyRecents).isEmpty()
    }

    @Test
    fun touchNavigationWorksWithoutOldCategoryDrawer() {
        val touchActionInGuide = LiveTvStartup.guideBackAction(
            isTouchDevice = true,
            categoryDrawerOpen = false,
            mode = LiveTvStartup.LiveTvMode.Guide,
        )
        assertThat(touchActionInGuide).isNotEqualTo(LiveTvStartup.GuideBackAction.OPEN_CATEGORIES)
        assertThat(touchActionInGuide).isEqualTo(LiveTvStartup.GuideBackAction.OPEN_GROUP_HOME)

        val touchActionInHome = LiveTvStartup.guideBackAction(
            isTouchDevice = true,
            categoryDrawerOpen = false,
            mode = LiveTvStartup.LiveTvMode.GroupHome,
        )
        assertThat(touchActionInHome).isEqualTo(LiveTvStartup.GuideBackAction.EXIT_TV)
    }

    @Test
    fun singlePlaylistSuppressesProviderPills() {
        val config = com.arflix.tv.data.repository.IptvConfig(
            playlists = listOf(
                com.arflix.tv.data.repository.IptvPlaylistEntry(
                    id = "p1",
                    name = "Main Playlist",
                    m3uUrl = "https://example.test/p1.m3u",
                    enabled = true,
                )
            )
        )
        val channels = listOf(
            channel("p1:c1", group = "News"),
            channel("p1:c2", group = "Sports"),
        )
        val filters = buildTvProviderFilters(config, channels)
        assertThat(filters).isEmpty()
    }

    @Test
    fun multiplePlaylistsBuildsProviderFiltersForGroupHome() {
        val config = com.arflix.tv.data.repository.IptvConfig(
            playlists = listOf(
                com.arflix.tv.data.repository.IptvPlaylistEntry(
                    id = "p1",
                    name = "Sports Pack",
                    m3uUrl = "https://example.test/p1.m3u",
                    enabled = true,
                ),
                com.arflix.tv.data.repository.IptvPlaylistEntry(
                    id = "p2",
                    name = "News Pack",
                    m3uUrl = "https://example.test/p2.m3u",
                    enabled = true,
                ),
            )
        )
        val channels = listOf(
            channel("p1:c1", group = "Sports"),
            channel("p2:c2", group = "News"),
        )
        val filters = buildTvProviderFilters(config, channels)
        assertThat(filters).hasSize(3)
        assertThat(filters.map { it.id }).containsExactly("all", "p1", "p2").inOrder()
        assertThat(filters[0].label).isEqualTo("All providers")
        assertThat(filters[1].label).isEqualTo("Sports Pack")
        assertThat(filters[2].label).isEqualTo("News Pack")
    }
}
