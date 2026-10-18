package com.composables.googlefonts

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class FontCompositionCacheTest {
    @Test
    fun newCompositionStartsWithThePreviouslyLoadedFamily() = runBlocking {
        val previous = System.getProperty("googlefonts.verificationFallback")
        System.setProperty("googlefonts.verificationFallback", "true")
        val clock =
            object : MonotonicFrameClock {
                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                    onFrame(System.nanoTime())
            }
        val recomposer = Recomposer(coroutineContext + clock)
        val runner =
            launch(clock, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
        val compositions = mutableListOf<Composition>()
        fun composition() =
            Composition(
                    object : AbstractApplier<Unit>(Unit) {
                        override fun insertBottomUp(index: Int, instance: Unit) {}

                        override fun insertTopDown(index: Int, instance: Unit) {}

                        override fun move(from: Int, to: Int, count: Int) {}

                        override fun remove(index: Int, count: Int) {}

                        override fun onClear() {}
                    },
                    recomposer,
                )
                .also { compositions += it }
        try {
            val request = "Composition regression ${System.nanoTime()}"
            val first = mutableListOf<FontFamily>()
            val initial = composition()
            initial.setContent {
                first += rememberGoogleFontFamily(request, fallback = FontFamily.Monospace)
            }
            repeat(10) {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                yield()
            }
            assertEquals(
                FontFamily.SansSerif,
                first.last(),
                "The first composition must finish loading",
            )
            initial.dispose()
            val afterReset = mutableListOf<FontFamily>()
            composition().setContent {
                afterReset += rememberGoogleFontFamily(request, fallback = FontFamily.Monospace)
            }
            assertEquals(
                FontFamily.SansSerif,
                afterReset.first(),
                "A reset must not briefly return the fallback",
            )
        } finally {
            compositions.forEach { it.dispose() }
            recomposer.cancel()
            runner.join()
            if (previous == null) System.clearProperty("googlefonts.verificationFallback")
            else System.setProperty("googlefonts.verificationFallback", previous)
        }
    }
}
