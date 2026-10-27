package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator

/** `id-1`, `id-2`, … para que los tests sepan qué id recibió cada entidad y cada correlación. */
class SequentialIdGenerator : IdGenerator {
    private var n = 0
    override fun newId() = "id-${++n}"
}
