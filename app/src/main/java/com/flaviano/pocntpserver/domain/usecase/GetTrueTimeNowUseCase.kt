package com.flaviano.pocntpserver.domain.usecase

import com.instacart.library.truetime.TrueTime
import java.util.*

class GetTrueTimeNowUseCase {

    /**
     * Must be returned ever the date time updated if TrueTime is initialized
     *
     * @return Result<Date>
     */
    operator fun invoke(): Result<Date> = kotlin.runCatching {
        TrueTime.now()
    }

}