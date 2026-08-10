package com.ceutenant.lumen.service

import com.intellij.util.messages.Topic

/** Publicado toda vez que o CoverageService termina um reload — gutter e toolwindow assinam os dois. */
fun interface CoverageReloadListener {
    fun onCoverageReloaded()

    companion object {
        val TOPIC: Topic<CoverageReloadListener> =
            Topic.create("Lumen reload", CoverageReloadListener::class.java)
    }
}
