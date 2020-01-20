/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.internal.task

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors

class CoroutineSequencersTest {

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Test
    fun sequencer_should_run_sequential() {
        val sequencer = ChannelCoroutineSequencer<String>()
        val results = ArrayList<String>()

        val jobs = listOf(
                GlobalScope.launch(dispatcher) {
                    sequencer.post { suspendingMethod("#1") }.also {
                        results.add(it)
                    }
                },
                GlobalScope.launch(dispatcher) {
                    sequencer.post { suspendingMethod("#2") }.also {
                        results.add(it)
                    }
                },
                GlobalScope.launch(dispatcher) {
                    sequencer.post { suspendingMethod("#3") }.also {
                        results.add(it)
                    }
                }
        )
        runBlocking {
            jobs.joinAll()
        }
        assertEquals(3, results.size)
        assertEquals(results[0], "#1")
        assertEquals(results[1], "#2")
        assertEquals(results[2], "#3")
    }

    @Test
    fun sequencer_should_run_parallel() {
        val sequencer1 = ChannelCoroutineSequencer<String>()
        val sequencer2 = ChannelCoroutineSequencer<String>()
        val sequencer3 = ChannelCoroutineSequencer<String>()
        val results = ArrayList<String>()
        val jobs = listOf(
                GlobalScope.launch(dispatcher) {
                    sequencer1.post { suspendingMethod("#1") }.also {
                        results.add(it)
                    }
                },
                GlobalScope.launch(dispatcher) {
                    sequencer2.post { suspendingMethod("#2") }.also {
                        results.add(it)
                    }
                },
                GlobalScope.launch(dispatcher) {
                    sequencer3.post { suspendingMethod("#3") }.also {
                        results.add(it)
                    }
                }
        )
        runBlocking {
            jobs.joinAll()
        }
        assertEquals(3, results.size)
    }

    @Test
    fun sequencer_should_jump_to_next_when_current_job_canceled() {
        val sequencer = ChannelCoroutineSequencer<String>()
        val results = ArrayList<String>()
        val jobs = listOf(
                GlobalScope.launch(dispatcher) {
                    sequencer.post { suspendingMethod("#1") }.also {
                        results.add(it)
                    }
                },
                GlobalScope.launch(dispatcher) {
                    val result = sequencer.post { suspendingMethod("#2") }.also {
                        results.add(it)
                    }
                    println("Result: $result")
                },
                GlobalScope.launch(dispatcher) {
                    sequencer.post { suspendingMethod("#3") }.also {
                        results.add(it)
                    }
                }
        )
        // We are canceling the second job
        jobs[1].cancel()
        runBlocking {
            jobs.joinAll()
        }
        assertEquals(2, results.size)
    }

    private suspend fun suspendingMethod(name: String): String {
        println("BLOCKING METHOD $name STARTS on ${Thread.currentThread().name}")
        delay(1000)
        println("BLOCKING METHOD $name ENDS on ${Thread.currentThread().name}")
        return name
    }

    @Test
    fun test_MoshiMap() {
        val moshi = Moshi.Builder().build()
        val sample = """
             {
               "master_key": {
                "user_id": "@alice:example.com",
                "usage": ["master"],
                "keys": {
                  "ed25519:base64+master+public+key": "base64+self+master+key",
                }
               },
               "self_signing_key": {
                "user_id": "@alice:example.com",
                "usage": ["self_signing"],
                "keys": {
                  "ed25519:base64+self+signing+public+key": "base64+self+signing+public+key",
                },
                "signatures": {
                  "@alice:example.com": {
                    "ed25519:base64+master+public+key": "base64+signature"
                  }
                }
               },
               "user_signing_key": {
                "user_id": "@alice:example.com",
                "keys": {
                  "ed25519:base64+device+signing+public+key": "base64+device+signing+public+key",
                },
                "usage": ["user_signing"],
                "signatures": {
                  "@alice:example.com": {
                    "ed25519:base64+master+public+key": "base64+signature"
                  }
                }
               }
        """.trimIndent()

        val adapter = moshi.adapter<Map<String, Any>>(Types.newParameterizedType(Map::class.java, List::class.java, String::class.java))

        val map = adapter.fromJson(sample)
        print(map)
    }
}
