package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalRoute
import junit.framework.TestCase
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SerializedObjectDataExternalizerTest : TestCase() {

    private val externalizer = SerializedObjectDataExternalizer(serializer<DrupalRoute>())

    fun testRoundTripsSmallValue() {
        val route = DrupalRoute(name = "my.route", path = "/my", description = "A route.")

        assertEquals(route, roundTrip(route))
    }

    fun testRoundTripsValueLargerThan64Kb() {
        // DataOutput.writeUTF would throw UTFDataFormatException here.
        val route = DrupalRoute(name = "big.route", path = "/big", description = "x".repeat(100_000))

        assertEquals(route, roundTrip(route))
    }

    private fun roundTrip(route: DrupalRoute): DrupalRoute {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { externalizer.save(it, route) }

        return DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { externalizer.read(it) }
    }
}
