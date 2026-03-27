package org.noblecow.hrservice.di

import android.content.BroadcastReceiver
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

/** A [MapKey] annotation for binding BroadcastReceiver in a multibinding map. */
@MapKey(implicitClassKey = true)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReceiverKey(val value: KClass<out BroadcastReceiver> = Nothing::class)
