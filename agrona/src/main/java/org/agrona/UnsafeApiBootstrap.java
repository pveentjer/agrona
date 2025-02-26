/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.agrona;

import java.lang.invoke.*;
import java.lang.reflect.Method;

/**
 * {@link UnsafeApi} bootstrapping functionality.
 */
public final class UnsafeApiBootstrap
{
    /**
     * We don't want instances.
     */
    private UnsafeApiBootstrap()
    {
    }

    /**
     * Bootstrap method for arrayBaseOffset that will be called by the JVM when
     * the invokedynamic instruction is executed for the first time.
     *
     * @param lookup     The lookup context
     * @param methodName The name of the method to find
     * @param methodType The method type (signature) expected at the call site
     * @return A CallSite bound to the appropriate implementation
     * @throws Throwable If method resolution fails
     */
    public static CallSite bootstrapArrayBaseOffset(
        final MethodHandles.Lookup lookup,
        final String methodName,
        final MethodType methodType) throws Throwable
    {
        final Class<?> unsafeClass = methodType.parameterType(0);

        try
        {
            final Method arrayBaseOffsetMethod = unsafeClass.getMethod("arrayBaseOffset", Class.class);
            final MethodHandle targetMethod = lookup.unreflect(arrayBaseOffsetMethod);

            if (arrayBaseOffsetMethod.getReturnType() == long.class)
            {
                final MethodHandle convertToIntMethod = MethodHandles.lookup().findStatic(
                    UnsafeApiBootstrap.class,
                    "arrayBaseOffsetConvertToInt",
                    MethodType.methodType(int.class, long.class));

                // Wrap method to perform a range check before casting to int
                final MethodHandle safeIntConversion = MethodHandles.filterReturnValue(
                    targetMethod,
                    convertToIntMethod
                );

                return new ConstantCallSite(safeIntConversion);
            }
            else
            {
                return new ConstantCallSite(targetMethod);
            }
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Failed to create method handle for Unsafe.arrayBaseOffset", e);
        }
    }

    /**
     * Casts the return value of Unsafe.arrayBaseOffset from long to int.
     *
     * @param value The long value to check.
     * @return The int value if it fits within the valid range.
     * @throws ArithmeticException if the value is out of int range.
     */
    private static int arrayBaseOffsetConvertToInt(final long value)
    {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            throw new ArithmeticException("arrayBaseOffset value out of int range: " + value);
        }
        return (int)value;
    }
}
