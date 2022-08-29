/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.util.kotlin

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class PairwiseFlowTest : SysuiTestCase() {
    @Test
    fun simple() = runBlocking {
        assertThatFlow((1..3).asFlow().pairwise())
            .emitsExactly(
                WithPrev(1, 2),
                WithPrev(2, 3),
            )
    }

    @Test
    fun notEnough() = runBlocking {
        assertThatFlow(flowOf(1).pairwise()).emitsNothing()
    }

    @Test
    fun withInit() = runBlocking {
        assertThatFlow(flowOf(2).pairwise(initialValue = 1))
            .emitsExactly(WithPrev(1, 2))
    }

    @Test
    fun notEnoughWithInit() = runBlocking {
        assertThatFlow(emptyFlow<Int>().pairwise(initialValue = 1)).emitsNothing()
    }

    @Test
    fun withStateFlow() = runBlocking(Dispatchers.Main.immediate) {
        val state = MutableStateFlow(1)
        val stop = MutableSharedFlow<Unit>()

        val stoppable = merge(state, stop)
            .takeWhile { it is Int }
            .filterIsInstance<Int>()

        val job1 = launch {
            assertThatFlow(stoppable.pairwise()).emitsExactly(WithPrev(1, 2))
        }
        state.value = 2
        val job2 = launch { assertThatFlow(stoppable.pairwise()).emitsNothing() }

        stop.emit(Unit)

        assertThatJob(job1).isCompleted()
        assertThatJob(job2).isCompleted()
    }
}

@SmallTest
@RunWith(AndroidTestingRunner::class)
class SetChangesFlowTest : SysuiTestCase() {
    @Test
    fun simple() = runBlocking {
        assertThatFlow(
            flowOf(setOf(1, 2, 3), setOf(2, 3, 4)).setChanges()
        ).emitsExactly(
            SetChanges(
                added = setOf(1, 2, 3),
                removed = emptySet(),
            ),
            SetChanges(
                added = setOf(4),
                removed = setOf(1),
            ),
        )
    }

    @Test
    fun onlyOneEmission() = runBlocking {
        assertThatFlow(flowOf(setOf(1)).setChanges())
            .emitsExactly(
                SetChanges(
                    added = setOf(1),
                    removed = emptySet(),
                )
            )
    }

    @Test
    fun fromEmptySet() = runBlocking {
        assertThatFlow(flowOf(emptySet(), setOf(1, 2)).setChanges())
            .emitsExactly(
                SetChanges(
                    removed = emptySet(),
                    added = setOf(1, 2),
                )
            )
    }
}

private fun <T> assertThatFlow(flow: Flow<T>) = object {
    suspend fun emitsExactly(vararg emissions: T) =
        assertThat(flow.toList()).containsExactly(*emissions).inOrder()
    suspend fun emitsNothing() =
        assertThat(flow.toList()).isEmpty()
}

private fun assertThatJob(job: Job) = object {
    fun isCompleted() = assertThat(job.isCompleted).isTrue()
}
