package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ReelSignature(
    val signatureHash: String,
    val username: String,
    val caption: String,
    val audio: String,
    val texts: List<String>,
    val descriptions: List<String>,
    val nodeCount: Int,
    val windowId: Int,
    val rawSignature: String
)

data class ReelAnalysis(
    val id: String,
    val timestamp: String,
    val swipeNumber: Int,
    val signature: String,
    val previousSignature: String,
    val signatureChanged: Boolean,
    val username: String,
    val caption: String,
    val audio: String,
    val detectedViewIds: List<String>,
    val treeDump: String,
    val retryCount: Int,
    val changeReason: String,
    val textsCollected: String,
    val descriptionsCollected: String,
    val parseTimeMs: Long,
    val signatureTimeMs: Long,
    val totalTimeMs: Long
)

data class AppState(
    val isInstagramActive: Boolean = false,
    val currentEvent: String = "N/A",
    val lastViewId: String = "N/A",
    val currentSignature: String = "N/A",
    val previousSignature: String = "N/A",
    val signatureChanged: Boolean = false,
    
    val totalAccessibilityEvents: Int = 0,
    val scrollEvents: Int = 0,
    val debouncedSwipes: Int = 0,
    val reelAnalyses: Int = 0,
    val signatureChanges: Int = 0,
    val confirmedReelChanges: Int = 0
)

data class TreeNodeInfo(
    val className: String,
    val viewId: String,
    val text: String?,
    val contentDescription: String?,
    val children: List<TreeNodeInfo> = emptyList(),
    val isVisible: Boolean = true
) {
    fun flatten(dest: MutableList<TreeNodeInfo> = mutableListOf()): List<TreeNodeInfo> {
        dest.add(this)
        for (i in children.indices) {
            children[i].flatten(dest)
        }
        return dest
    }
}

object LogRepository {
    private val _reelAnalyses = MutableStateFlow<List<ReelAnalysis>>(emptyList())
    val reelAnalyses: StateFlow<List<ReelAnalysis>> = _reelAnalyses.asStateFlow()

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _latestTree = MutableStateFlow<TreeNodeInfo?>(null)
    val latestTree: StateFlow<TreeNodeInfo?> = _latestTree.asStateFlow()
    
    val sessionLog = JSONArray()
    var isRecording = false

    fun updateState(updater: (AppState) -> AppState) {
        _appState.update(updater)
    }

    fun addReelAnalysis(analysis: ReelAnalysis, treeNode: TreeNodeInfo?) {
        val currentList = _reelAnalyses.value.toMutableList()
        currentList.add(0, analysis)
        if (currentList.size > 20) currentList.removeAt(currentList.lastIndex)
        _reelAnalyses.value = currentList
        
        if (treeNode != null) _latestTree.value = treeNode
        
        if (isRecording) {
            val json = JSONObject()
            json.put("id", analysis.id)
            json.put("timestamp", analysis.timestamp)
            json.put("swipeNumber", analysis.swipeNumber)
            json.put("signature", analysis.signature)
            json.put("signatureChanged", analysis.signatureChanged)
            json.put("username", analysis.username)
            json.put("caption", analysis.caption)
            json.put("audio", analysis.audio)
            json.put("retryCount", analysis.retryCount)
            json.put("changeReason", analysis.changeReason)
            json.put("parseTimeMs", analysis.parseTimeMs)
            json.put("signatureTimeMs", analysis.signatureTimeMs)
            json.put("totalTimeMs", analysis.totalTimeMs)
            sessionLog.put(json)
        }
    }

    fun clearEvents() {
        _reelAnalyses.value = emptyList()
        _latestTree.value = null
        sessionLog.put(JSONObject().put("info", "session cleared"))
    }
    
    fun exportSession(): String {
        return sessionLog.toString(2)
    }
}

class ReelAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var _swipeManager: SwipeConfirmationManager? = null
    private val swipeManager: SwipeConfirmationManager
        get() {
            if (_swipeManager == null) {
                _swipeManager = SwipeConfirmationManager(this, handler)
            }
            return _swipeManager!!
        }

    private var overlayManager: OverlayManager? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        
        val settingsManager = (application as com.example.ReelApplication).settingsManager
        serviceScope.launch {
            settingsManager.overlayEnabledFlow.collect { enabled ->
                if (enabled) {
                    overlayManager?.showOverlay()
                } else {
                    overlayManager?.hideOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: ""
        if (packageName != "com.instagram.android") {
            LogRepository.updateState { it.copy(isInstagramActive = false) }
            return
        }

        LogRepository.updateState { 
            it.copy(
                isInstagramActive = true, 
                totalAccessibilityEvents = it.totalAccessibilityEvents + 1
            ) 
        }

        swipeManager.onEvent(event)
    }

    override fun onInterrupt() {
        _swipeManager?.cleanup()
    }
    
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        overlayManager?.hideOverlay()
        _swipeManager?.cleanup()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }
}

class SwipeConfirmationManager(
    private val service: AccessibilityService,
    private val handler: Handler
) {
    private val treeParser = AccessibilityTreeParser()
    private val signatureGenerator = CompositeSignatureGenerator()
    private val metadataExtractor = MetadataExtractor()
    private val retryEngine = RetryConfirmationEngine(
        handler, treeParser, signatureGenerator, metadataExtractor, service,
        onSuccess = { newSig, oldSig, reason, retries, tree, pt, st, tt ->
            handleConfirmation(newSig, oldSig, reason, retries, tree, true, pt, st, tt)
        },
        onFailure = { newSig, oldSig, reason, retries, tree, pt, st, tt ->
            handleConfirmation(newSig, oldSig, reason, retries, tree, false, pt, st, tt)
        }
    )
    
    private val debouncer = EventDebouncer(handler) {
        retryEngine.startConfirmation()
    }
    
    fun onEvent(event: AccessibilityEvent) {
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            LogRepository.updateState { it.copy(scrollEvents = it.scrollEvents + 1) }
            debouncer.onScrollEvent()
        } else if (
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || 
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            debouncer.onContentChanged()
        }
    }
    
    fun cleanup() {
        debouncer.cleanup()
        retryEngine.cleanup()
    }
    
    private fun handleConfirmation(
        newSig: ReelSignature,
        oldSig: ReelSignature?,
        reason: String,
        retries: Int,
        tree: TreeNodeInfo,
        success: Boolean,
        parseTime: Long,
        sigTime: Long,
        totalTime: Long
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val swipeNumber = retryEngine.swipeNumber
        
        Log.i("ReelAnalysis", "----------------------------------")
        Log.i("ReelAnalysis", "Swipe #$swipeNumber")
        Log.i("ReelAnalysis", "Tree nodes: ${newSig.nodeCount}")
        Log.i("ReelAnalysis", "Texts collected: \n${newSig.texts.joinToString("\n")}")
        Log.i("ReelAnalysis", "Descriptions: \n${newSig.descriptions.joinToString("\n")}")
        Log.i("ReelAnalysis", "Composite signature: ${newSig.signatureHash}")
        Log.i("ReelAnalysis", "Previous signature: ${oldSig?.signatureHash ?: "N/A"}")
        Log.i("ReelAnalysis", "Reason: \n$reason")
        Log.i("ReelAnalysis", "Retry #$retries")
        Log.i("ReelAnalysis", if (success) "Confirmed" else "Failed")
        Log.i("ReelAnalysis", "----------------------------------")
        
        val analysis = ReelAnalysis(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            swipeNumber = swipeNumber,
            signature = newSig.signatureHash,
            previousSignature = oldSig?.signatureHash ?: "N/A",
            signatureChanged = success,
            username = newSig.username,
            caption = newSig.caption,
            audio = newSig.audio,
            detectedViewIds = tree.flatten().map { it.viewId.substringAfterLast('/') }.filter { it.isNotBlank() && it != "N/A" }.distinct().take(20),
            treeDump = formatTree(tree),
            retryCount = retries,
            changeReason = reason,
            textsCollected = newSig.texts.joinToString("\n"),
            descriptionsCollected = newSig.descriptions.joinToString("\n"),
            parseTimeMs = parseTime,
            signatureTimeMs = sigTime,
            totalTimeMs = totalTime
        )

        LogRepository.addReelAnalysis(analysis, tree)
        
        if (success) {
            LogRepository.updateState { 
                it.copy(
                    confirmedReelChanges = it.confirmedReelChanges + 1,
                    signatureChanges = it.signatureChanges + 1,
                    currentSignature = newSig.signatureHash,
                    previousSignature = oldSig?.signatureHash ?: "N/A",
                    signatureChanged = true
                ) 
            }
            com.example.manager.SessionManager.onReelConfirmed(retries, parseTime, sigTime, totalTime)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                saveSignature(service, newSig.signatureHash, newSig.username)
            }
        } else {
            LogRepository.updateState {
                it.copy(
                    currentSignature = newSig.signatureHash,
                    previousSignature = oldSig?.signatureHash ?: "N/A",
                    signatureChanged = false
                )
            }
        }
    }
    
    private fun saveSignature(context: android.content.Context, signature: String, username: String) {
        val signaturesFile = File(context.filesDir, "reel_signatures.json")
        try {
            val jsonArray = if (signaturesFile.exists()) {
                JSONArray(signaturesFile.readText())
            } else {
                JSONArray()
            }
            
            val obj = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date()))
                put("username", username)
                put("signature", signature)
            }
            jsonArray.put(obj)
            
            val recentArray = JSONArray()
            val startIdx = maxOf(0, jsonArray.length() - 10)
            for (i in startIdx until jsonArray.length()) {
                recentArray.put(jsonArray.get(i))
            }
            
            signaturesFile.writeText(recentArray.toString(2))
        } catch (e: Exception) {
            Log.e("ReelAccessibilityService", "Error saving signature: ${e.message}")
        }
    }
    
    private fun formatTree(node: TreeNodeInfo, prefix: String = "", isLast: Boolean = true): String {
        val result = StringBuilder()
        val nodeLine = node.className.substringAfterLast(".")
        result.append(prefix).append(if (isLast) " └── " else " ├── ").append(nodeLine).append("\n")

        val childPrefix = prefix + (if (isLast) "     " else " │   ")
        if (node.viewId.isNotBlank() && node.viewId != "N/A") result.append(childPrefix).append("  id: ${node.viewId.substringAfterLast('/')}\n")
        if (!node.text.isNullOrBlank()) result.append(childPrefix).append("  text: \"${node.text}\"\n")
        if (!node.contentDescription.isNullOrBlank()) result.append(childPrefix).append("  desc: \"${node.contentDescription}\"\n")

        for (i in node.children.indices) {
            result.append(formatTree(node.children[i], childPrefix, i == node.children.size - 1))
        }
        return result.toString()
    }
}

class EventDebouncer(private val handler: Handler, private val onStable: () -> Unit) {
    private var debounceRunnable: Runnable? = null
    private var lastScrollTime = 0L
    
    fun onScrollEvent() {
        lastScrollTime = System.currentTimeMillis()
        scheduleStableCheck(400)
    }
    
    fun onContentChanged() {
        // If we recently scrolled, we should extend the wait since UI is still changing
        if (System.currentTimeMillis() - lastScrollTime < 1500) {
            scheduleStableCheck(300)
        }
    }
    
    fun cleanup() {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
    }
    
    private fun scheduleStableCheck(delayMs: Long) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { onStable() }
        debounceRunnable = r
        handler.postDelayed(r, delayMs)
    }
}

class AccessibilityTreeParser {
    fun parse(root: AccessibilityNodeInfo?): TreeNodeInfo? {
        if (root == null) return null
        return buildTree(root, 0)
    }
    
    private fun buildTree(node: AccessibilityNodeInfo, depth: Int): TreeNodeInfo? {
        if (depth > 50) return null
        if (!node.isVisibleToUser) return null
        
        val viewId = node.viewIdResourceName ?: "N/A"
        if (isNoisyNode(viewId)) return null
        
        val children = mutableListOf<TreeNodeInfo>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val childTree = buildTree(child, depth + 1)
                if (childTree != null) children.add(childTree)
                child.recycle()
            }
        }
        
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val contentDesc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        
        return TreeNodeInfo(
            className = node.className?.toString() ?: "Unknown",
            viewId = viewId,
            text = text,
            contentDescription = contentDesc,
            children = children,
            isVisible = node.isVisibleToUser
        )
    }
    
    companion object {
        private val NOISY_IDS = listOf(
            "scrubber", "like_count", "comment_count", "friendly_bubbles", 
            "clips_expanded_touch_view", "progress", "tab_bar", "bottom_navigation", 
            "time_elapsed", "playback", "share_count", "seekbar", "loading"
        )
    }
    
    private fun isNoisyNode(viewId: String): Boolean {
        if (viewId == "N/A") return false
        val lowerId = viewId.lowercase()
        for (i in NOISY_IDS.indices) {
            if (lowerId.contains(NOISY_IDS[i])) return true
        }
        return false
    }
}

data class ReelMetadata(
    val username: String,
    val caption: String,
    val audio: String
)

class MetadataExtractor {
    fun extract(flat: List<TreeNodeInfo>): ReelMetadata {
        var username: String? = null
        var caption: String? = null
        var audio: String? = null
        
        // Helper to get text or desc
        fun getContent(n: TreeNodeInfo): String {
            val t = n.text ?: ""
            val d = n.contentDescription ?: ""
            return if (t.isNotBlank()) t else d
        }
        
        // 1. Username
        for (node in flat) {
            val id = node.viewId.substringAfterLast('/').lowercase()
            val content = getContent(node)
            if (content.isBlank()) continue
            
            if (id.contains("clips_author_username")) {
                username = content
                break
            }
        }
        
        if (username == null) {
            for (node in flat) {
                val content = getContent(node)
                if (content.isBlank()) continue
                val lower = content.lowercase()
                
                if (lower.startsWith("reel by ")) {
                    username = content.substring(8).trim()
                    break
                } else if (lower.startsWith("profile picture of ")) {
                    username = content.substring(19).trim()
                    break
                }
            }
        }
        
        if (username == null) {
            for (node in flat) {
                val id = node.viewId.substringAfterLast('/').lowercase()
                val content = getContent(node)
                if (content.isBlank()) continue
                if (id.contains("author") || id.contains("profile_name") || id.contains("user")) {
                    username = content.trim()
                    break
                }
            }
        }
        
        // 2. Caption
        val captionBuilder = StringBuilder()
        var insideCaptionComponent = false
        
        // To recursively extract from clips_caption_component, we'll iterate through flat since it preserves order
        // and we can just collect all texts that are children of clips_caption_component.
        // Wait, flat doesn't easily show parent-child. Let's just find the node with that id and flatten it.
        val captionNode = flat.find { it.viewId.substringAfterLast('/').lowercase().contains("clips_caption_component") }
        if (captionNode != null) {
            val captionChildren = captionNode.flatten()
            for (child in captionChildren) {
                val txt = child.text
                if (!txt.isNullOrBlank()) {
                    captionBuilder.append(txt).append(" ")
                }
            }
            caption = captionBuilder.toString().trim().takeIf { it.isNotBlank() }
        }
        
        if (caption == null) {
            for (node in flat) {
                val id = node.viewId.substringAfterLast('/').lowercase()
                val content = getContent(node)
                if (content.isBlank()) continue
                if (id.contains("caption") || id.contains("title") || id.contains("description")) {
                    caption = content.trim()
                    break
                }
            }
        }

        // 3. Audio
        for (node in flat) {
            val id = node.viewId.substringAfterLast('/').lowercase()
            val content = getContent(node)
            if (content.isBlank()) continue
            
            if (id.contains("music") || id.contains("audio") || id.contains("song")) {
                audio = content.trim()
                break
            }
        }
        
        if (audio == null) {
            for (node in flat) {
                val content = getContent(node)
                if (content.isBlank()) continue
                val lower = content.lowercase()
                if (lower.contains("original audio") || lower.contains("song - ") || lower.contains("music - ")) {
                    audio = content.trim()
                    break
                }
            }
        }
        
        return ReelMetadata(
            username = username ?: "N/A",
            caption = caption ?: "N/A",
            audio = audio ?: "N/A"
        )
    }
}

class CompositeSignatureGenerator {
    private val spaceRegex = Regex("\\s+")

    fun generate(flat: List<TreeNodeInfo>, windowId: Int, metadata: ReelMetadata): ReelSignature {
        val textsSet = java.util.HashSet<String>()
        val descriptionsSet = java.util.HashSet<String>()
        
        for (n in flat) {
            if (n.text != null && isMeaningful(n.text)) {
                textsSet.add(normalize(n.text))
            }
            if (n.contentDescription != null && isMeaningful(n.contentDescription)) {
                descriptionsSet.add(normalize(n.contentDescription))
            }
        }
        
        val sortedTexts = textsSet.sorted()
        val sortedDescriptions = descriptionsSet.sorted()
            
        val rawSignature = buildString {
            append("W:$windowId|")
            append("U:${metadata.username}|")
            append("C:${metadata.caption}|")
            append("A:${metadata.audio}|")
            append("N:${flat.size}|")
            append("T:${sortedTexts.joinToString(";")}|")
            append("D:${sortedDescriptions.joinToString(";")}")
        }
        
        return ReelSignature(
            signatureHash = String.format("%08X", rawSignature.hashCode()),
            username = metadata.username,
            caption = metadata.caption,
            audio = metadata.audio,
            texts = sortedTexts,
            descriptions = sortedDescriptions,
            nodeCount = flat.size,
            windowId = windowId,
            rawSignature = rawSignature
        )
    }
    
    private fun normalize(str: String): String {
        return str.trim().replace(spaceRegex, " ")
    }
    
    private fun isMeaningful(str: String): Boolean {
        if (str.isBlank()) return false
        val lower = str.lowercase()
        // Fast checks to avoid regex overhead if possible
        if (lower.length in 4..5 && lower[lower.length - 3] == ':') {
            if (lower.matches(Regex("^\\d{1,2}:\\d{2}$"))) return false
        }
        if (lower == "loading" || lower == "ad" || lower == "sponsored") return false
        if (lower.startsWith("ad ")) return false
        if (lower.matches(Regex("^\\d+\\s*(s|m|h|w)$"))) return false
        return true
    }
}

class RetryConfirmationEngine(
    private val handler: Handler,
    private val treeParser: AccessibilityTreeParser,
    private val signatureGenerator: CompositeSignatureGenerator,
    private val metadataExtractor: MetadataExtractor,
    private val service: AccessibilityService,
    private val onSuccess: (ReelSignature, ReelSignature?, String, Int, TreeNodeInfo, Long, Long, Long) -> Unit,
    private val onFailure: (ReelSignature, ReelSignature?, String, Int, TreeNodeInfo, Long, Long, Long) -> Unit
) {
    private var previousSignature: ReelSignature? = null
    var swipeNumber = 0
        private set
        
    private var retryCount = 0
    private var isConfirming = false
    private var retryRunnable: Runnable? = null
    
    fun startConfirmation() {
        if (isConfirming) return
        swipeNumber++
        retryCount = 0
        isConfirming = true
        LogRepository.updateState { it.copy(debouncedSwipes = it.debouncedSwipes + 1) }
        
        scheduleRunnable(300)
    }
    
    fun cleanup() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        isConfirming = false
    }
    
    private fun scheduleRunnable(delay: Long) {
        retryRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { attemptConfirmation() }
        retryRunnable = r
        handler.postDelayed(r, delay)
    }
    
    private fun attemptConfirmation() {
        val startTime = System.currentTimeMillis()
        var instaRoot: AccessibilityNodeInfo? = null
        var windowId = -1
        try {
            val windowList = service.windows
            for (w in windowList) {
                val root = w.root
                if (root?.packageName == "com.instagram.android") {
                    instaRoot = root
                    windowId = w.id
                    break
                }
                root?.recycle()
            }
        } catch (e: Exception) {
            Log.e("RetryEngine", "Error accessing windows: ${e.message}")
        }
        
        if (instaRoot == null) {
            scheduleRetry("No Instagram window found")
            return
        }
        
        LogRepository.updateState { it.copy(reelAnalyses = it.reelAnalyses + 1) }
        
        val parseStart = System.currentTimeMillis()
        val fullTree = treeParser.parse(instaRoot)
        instaRoot.recycle()
        val parseTime = System.currentTimeMillis() - parseStart
        
        if (fullTree == null) {
            scheduleRetry("Failed to parse tree", parseTime = parseTime)
            return
        }
        
        val flatNodes = fullTree.flatten()
        
        val screenType = ReelScreenDetector.detect(flatNodes)
        if (screenType != InstagramScreen.REELS) {
            Log.d("ReelCounter", "Current screen: $screenType\nIgnoring event")
            if (screenType == InstagramScreen.UNKNOWN && retryCount < 5) {
                scheduleRetry("Screen unknown ($screenType)", parseTime = parseTime)
                return
            }
            isConfirming = false
            return
        }
        Log.d("ReelCounter", "Current screen: REELS\nProcessing event")
        
        // Ensure Session is Running
        if (com.example.manager.SessionManager.state.value != com.example.manager.SessionState.RUNNING) {
            isConfirming = false
            return
        }

        val sigStart = System.currentTimeMillis()
        val metadata = metadataExtractor.extract(flatNodes)
        val newSignature = signatureGenerator.generate(flatNodes, windowId, metadata)
        val sigTime = System.currentTimeMillis() - sigStart
        
        val totalTime = System.currentTimeMillis() - startTime
        
        val prevSig = previousSignature
        if (prevSig == null) {
            previousSignature = newSignature
            isConfirming = false
            onSuccess(newSignature, null, "First signature captured", retryCount, fullTree, parseTime, sigTime, totalTime)
            return
        }
        
        if (newSignature.signatureHash != prevSig.signatureHash) {
            val reason = determineChangeReason(prevSig, newSignature)
            previousSignature = newSignature
            isConfirming = false
            onSuccess(newSignature, prevSig, reason, retryCount, fullTree, parseTime, sigTime, totalTime)
        } else {
            scheduleRetry("Signature unchanged (Hash: ${newSignature.signatureHash})", newSignature, fullTree, parseTime, sigTime, totalTime)
        }
    }
    
    private fun scheduleRetry(
        reason: String, 
        currentSig: ReelSignature? = null, 
        tree: TreeNodeInfo? = null,
        parseTime: Long = 0,
        sigTime: Long = 0,
        totalTime: Long = 0
    ) {
        if (retryCount < 5) {
            retryCount++
            val delay = 300L + (retryCount * 200L)
            scheduleRunnable(delay)
        } else {
            isConfirming = false
            if (currentSig != null && tree != null) {
                onFailure(currentSig, previousSignature, reason, retryCount, tree, parseTime, sigTime, totalTime)
            }
        }
    }
    
    private fun determineChangeReason(old: ReelSignature, new: ReelSignature): String {
        val reasons = mutableListOf<String>()
        if (old.username != new.username) reasons.add("Username changed (${old.username} -> ${new.username})")
        if (old.caption != new.caption) reasons.add("Caption changed")
        if (old.audio != new.audio) reasons.add("Audio changed")
        if (old.nodeCount != new.nodeCount) reasons.add("Node count changed (${old.nodeCount} -> ${new.nodeCount})")
        if (old.windowId != new.windowId) reasons.add("Window changed")
        
        val oldTexts = old.texts.toSet()
        val newTexts = new.texts.toSet()
        if (oldTexts != newTexts) {
            val added = newTexts - oldTexts
            val removed = oldTexts - newTexts
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                reasons.add("Texts changed (+${added.size}, -${removed.size})")
            }
        }
        
        val oldDescs = old.descriptions.toSet()
        val newDescs = new.descriptions.toSet()
        if (oldDescs != newDescs) {
            val added = newDescs - oldDescs
            val removed = oldDescs - newDescs
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                reasons.add("Descriptions changed (+${added.size}, -${removed.size})")
            }
        }
        
        if (reasons.isEmpty()) return "Unknown structural change"
        return reasons.joinToString("\n")
    }
}
