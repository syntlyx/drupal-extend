package com.github.nvelychenko.drupalextend.index.dataExternalizer

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.DataInput
import java.io.DataOutput

class SerializedObjectDataExternalizer<T : Any>(
    private val serializer: KSerializer<T>,
) : DataExternalizer<T> {

    // IOUtil over DataOutput.writeUTF: the latter throws UTFDataFormatException for payloads
    // over 64 KB, which a large serialized value (a theme with many variables, a route with
    // many handlers) can exceed.
    override fun save(out: DataOutput, value: T) {
        IOUtil.writeUTF(out, Json.encodeToString(serializer, value))
    }

    override fun read(input: DataInput): T {
        return Json.decodeFromString(serializer, IOUtil.readUTF(input))
    }
}
