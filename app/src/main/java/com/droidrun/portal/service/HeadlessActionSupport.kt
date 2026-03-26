package com.droidrun.portal.service

object HeadlessActionSupport {
    fun isAllowed(normalizedMethod: String): Boolean {
        return normalizedMethod == "stream/start" ||
            normalizedMethod == "stream/stop" ||
            normalizedMethod == "global" ||
            normalizedMethod == "webrtc/answer" ||
            normalizedMethod == "webrtc/offer" ||
            normalizedMethod == "webrtc/ice" ||
            normalizedMethod.startsWith("triggers/")
    }
}
