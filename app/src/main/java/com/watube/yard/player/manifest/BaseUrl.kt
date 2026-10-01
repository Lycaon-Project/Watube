/*
 * Copyright 2021 The Android Open Source Project
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
package com.watube.yard.player.manifest

import androidx.media3.common.util.UnstableApi

/** A base URL, as defined by ISO 23009-1, 2nd edition, 5.6. and ETSI TS 103 285 V1.2.1, 10.8.2.1 */
@UnstableApi
data class BaseUrl @JvmOverloads constructor(
    /** The URL. */
    val url: String,
    /** The service location. Defaults to [url]. */
    val serviceLocation: String = url,
    /** The priority. Defaults to [PRIORITY_UNSET]. */
    val priority: Int = PRIORITY_UNSET,
    /** The weight. Defaults to [DEFAULT_WEIGHT]. */
    val weight: Int = DEFAULT_WEIGHT
) {
    companion object {
        /** The default weight. */
        const val DEFAULT_WEIGHT = 1

        /** The default priority. */
        const val DEFAULT_DVB_PRIORITY = 1

        /** Constant representing an unset priority in a manifest that does not declare a DVB profile. */
        const val PRIORITY_UNSET = Int.MIN_VALUE
    }
}
