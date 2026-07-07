package com.example.service

import android.util.Log

enum class InstagramScreen {
    HOME, STORIES, PROFILE, EXPLORE, SEARCH, COMMENTS, SHARE_SHEET, SETTINGS, REELS, UNKNOWN
}

object ReelScreenDetector {
    fun detect(flatNodes: List<TreeNodeInfo>): InstagramScreen {
        val foundNodes = mutableListOf<String>()
        val missingNodes = mutableListOf<String>()
        
        var clipsScore = 0
        
        val indicators = mapOf(
            "root_clips_layout" to 3,
            "clips_viewer_container" to 3,
            "clips_viewer_view_pager" to 3,
            "clips_media_component" to 2,
            "clips_video_container" to 2,
            "clips_author_username" to 1,
            "clips_caption_component" to 1,
            "use_audio_button" to 1,
            "music_button" to 1
        )
        
        // Negative indicators
        var hasComments = false
        var hasShare = false
        var hasStory = false
        var hasProfile = false
        var hasExplore = false
        var hasSearch = false
        var hasFeed = false

        val detectedIds = flatNodes.map { it.viewId.substringAfterLast('/').lowercase() }.toSet()
        
        for ((nodeName, weight) in indicators) {
            if (detectedIds.any { it.contains(nodeName) }) {
                foundNodes.add(nodeName)
                clipsScore += weight
            } else {
                missingNodes.add(nodeName)
            }
        }

        // Check negatives (avoid matching the buttons on the Reel itself)
        if (detectedIds.any { it == "comment_thread" || it.contains("comment_list_container") }) hasComments = true
        if (detectedIds.any { it == "direct_share_sheet_scroll_view" || it == "share_sheet_search_bar" }) hasShare = true
        if (detectedIds.any { it == "story_viewer" || it == "story_layout" }) hasStory = true
        if (detectedIds.any { it == "profile_header" || it == "profile_tabs" }) hasProfile = true
        if (detectedIds.any { it == "explore_grid" || it.contains("discovery_") }) hasExplore = true
        if (detectedIds.any { it == "search_bar" || it == "search_results" }) hasSearch = true
        if (detectedIds.any { it == "row_feed_photo_profile_name" || it == "row_feed_button_like" || it == "main_feed" }) hasFeed = true
        
        val threshold = 3
        val isReels = clipsScore >= threshold

        val decision = when {
            hasComments -> InstagramScreen.COMMENTS
            hasShare -> InstagramScreen.SHARE_SHEET
            isReels -> InstagramScreen.REELS
            hasStory -> InstagramScreen.STORIES
            hasProfile -> InstagramScreen.PROFILE
            hasExplore -> InstagramScreen.EXPLORE
            hasSearch -> InstagramScreen.SEARCH
            hasFeed -> InstagramScreen.HOME
            else -> InstagramScreen.UNKNOWN
        }

        val logMessage = buildString {
            appendLine("ReelScreenDetector")
            appendLine()
            appendLine("Found:")
            foundNodes.forEach { appendLine("✓ $it") }
            missingNodes.forEach { appendLine("✗ $it") }
            appendLine()
            appendLine("Score = $clipsScore")
            appendLine("Threshold = $threshold")
            appendLine("Decision = ${decision.name}")
        }
        
        Log.d("ReelScreenDetector", logMessage)

        return decision
    }
}
