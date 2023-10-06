/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.test.tinyframework;

import android.hosttest.annotation.HostSideTestClassLoadHook;
import android.hosttest.annotation.HostSideTestWholeClassStub;


// Note, policy-override-tiny-framework.txt hss an override on this class.
@HostSideTestClassLoadHook(
        "com.android.hoststubgen.test.tinyframework.TinyFrameworkClassLoadHook.onClassLoaded")
@HostSideTestWholeClassStub
public class TinyFrameworkClassWithInitializer {
    // Note, this method has a 'throw' in the policy file, which is handled as a 'keep' (because
    // it's a static initializer), so this won't show up in the stub jar.
    static {
        sInitialized = true;
    }

    public static boolean sInitialized;
}
