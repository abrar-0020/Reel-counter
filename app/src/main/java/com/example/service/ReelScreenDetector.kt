package com.example.service

import android.util.Log

enum class InstagramScreen {
    HOME, STORIES, PROFILE, EXPLORE, SEARCH, COMMENTS, SHARE_SHEET, SETTINGS, REELS, UNKNOWN
}

object ReelScreenDetector {
    fun detect(flatNodes: List<TreeNodeInfo>): InstagramScreen {
        var hasClips = false
        var hasFeed = false
        var hasStory = false
        var hasProfile = false
        var hasExplore = false
        var hasComments = false
        var hasShare = false
        var hasSearch = false
        
        var clipsScore = 0
        
        for (node in flatNodes) {
            val id = node.viewId.substringAfterLast('/').lowercase()
            if (id == "n/a" || id.isBlank()) continue
            
            if (id.contains("clips_viewer") || 
                id.contains("root_clips") || 
                id.contains("clips_video") ||
                id.contains("clips_media_component")) {
                clipsScore += 2
                hasClips = true
            }
            if (id.contains("clips_author") || id.contains("clips_caption")) {
                clipsScore += 1
            }
            
            if (id.contains("row_feed") || id.contains("main_feed")) hasFeed = true
            if (id.contains("story_viewer") || id.contains("reel_viewer_root") || id.contains("story_layout")) hasStory = true
            if (id.contains("profile_header") || id.contains("profile_tabs")) hasProfile = true
            if (id.contains("explore_grid") || id.contains("discovery_")) hasExplore = true
            if (id.contains("comment_thread") || id.contains("layout_comment")) hasComments = true
            if (id.contains("direct_share") || id.contains("share_sheet")) hasShare = true
            if (id.contains("search_bar") || id.contains("search_results")) hasSearch = true
        }
        
        // Bottom sheets and overlays take precedence because they appear ON TOP of reels
        if (hasComments) return InstagramScreen.COMMENTS
        if (hasShare) return InstagramScreen.SHARE_SHEET
        
        // Explicit Reels detection
        if (clipsScore >= 2) return InstagramScreen.REELS
        
        if (hasStory) return InstagramScreen.STORIES
        if (hasProfile) return InstagramScreen.PROFILE
        if (hasExplore) return InstagramScreen.EXPLORE
        if (hasSearch) return InstagramScreen.SEARCH
        if (hasFeed) return InstagramScreen.HOME
        
        return InstagramScreen.UNKNOWN
    }
}
