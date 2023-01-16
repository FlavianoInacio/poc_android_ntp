package com.flaviano.pocntpserver.data.exception

import java.io.IOException
import java.util.Locale

class InvalidNtpServerResponseException(
    override val message : String = "",
     property: String = "",
     expectedValue: Float = 0f,
     actualValue: Float = 0f
) : IOException(
    String.format(
        Locale.getDefault(),
        message,
        property,
        actualValue,
        expectedValue
    )) {

}