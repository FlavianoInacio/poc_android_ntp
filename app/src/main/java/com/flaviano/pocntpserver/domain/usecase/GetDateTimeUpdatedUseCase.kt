package com.flaviano.pocntpserver.domain.usecase

import com.flaviano.pocntpserver.data.sntp.SNTPManager
import java.util.*

class GetDateTimeUpdatedUseCase {

    /**
     * Must be returned ever the date time updated if TrueTime is initialized
     *
     * @return Result<Date>
     */
    operator fun invoke(): Result<Date> = kotlin.runCatching {
        SNTPManager.now()
    }

}