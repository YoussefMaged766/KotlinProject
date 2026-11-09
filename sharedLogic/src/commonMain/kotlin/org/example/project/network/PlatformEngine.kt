package org.example.project.network

import io.ktor.client.engine.HttpClientEngine

/** The one piece of networking that differs per platform. */
internal expect fun platformHttpEngine(): HttpClientEngine
