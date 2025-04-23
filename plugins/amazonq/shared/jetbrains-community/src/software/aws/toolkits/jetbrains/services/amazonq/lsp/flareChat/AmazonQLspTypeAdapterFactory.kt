// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.eclipse.lsp4j.InitializeResult
import java.io.IOException

class AmazonQLspTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType === InitializeResult::class.java) {
            val delegate: TypeAdapter<InitializeResult?> = gson.getDelegateAdapter(this, type) as TypeAdapter<InitializeResult?>

            return object : TypeAdapter<InitializeResult>() {
                @Throws(IOException::class)
                override fun write(out: JsonWriter, value: InitializeResult?) {
                    delegate.write(out, value)
                }

                @Throws(IOException::class)
                override fun read(`in`: JsonReader): InitializeResult =
                    gson.fromJson(`in`, AwsExtendedInitializeResult::class.java)
            } as TypeAdapter<T>
        }
        return null
    }
}

class AwsExtendedInitializeResult(awsServerCapabilities: AwsServerCapabilities? = null) : InitializeResult() {
    private var awsServerCapabilities: AwsServerCapabilities? = null

    fun getAwsServerCapabilities(): AwsServerCapabilities? = awsServerCapabilities

    fun setAwsServerCapabilities(awsServerCapabilities: AwsServerCapabilities?) {
        this.awsServerCapabilities = awsServerCapabilities
    }
}
