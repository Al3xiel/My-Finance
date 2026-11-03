package com.al3xiel.my_finances.core.id

import java.util.UUID
import javax.inject.Inject

/** Los ids se generan en el caso de uso, antes de emitir el evento, para que el replay reproduzca los mismos. */
interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
