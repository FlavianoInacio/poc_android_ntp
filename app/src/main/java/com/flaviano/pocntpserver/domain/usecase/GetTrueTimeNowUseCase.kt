package com.flaviano.pocntpserver.domain.usecase

import com.instacart.library.truetime.TrueTime

class GetTrueTimeNowUseCase {

    operator  fun invoke() = kotlin.runCatching {
        TrueTime.now()
    }

}