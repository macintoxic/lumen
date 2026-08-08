package com.ceutenant.ridercoverage.service

import com.intellij.util.messages.Topic

/** Publicado toda vez que o CoverageService termina um reload — gutter e toolwindow assinam os dois. */
fun interface CoverageReloadListener {
    fun onCoverageReloaded()

    companion object {
        val TOPIC: Topic<CoverageReloadListener> =
            Topic.create("Coverage Gutter reload", CoverageReloadListener::class.java)
    }
}
