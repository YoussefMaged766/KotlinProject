package org.example.project.di

import org.koin.core.module.Module

/** Bindings whose implementation differs per platform. Today: only the HTTP engine. */
internal expect val platformModule: Module
