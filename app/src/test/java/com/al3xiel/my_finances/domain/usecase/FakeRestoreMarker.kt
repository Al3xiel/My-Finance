package com.al3xiel.my_finances.domain.usecase

class FakeRestoreMarker(var pending: Boolean = false) : RestoreMarker {
    override suspend fun isPending() = pending

    override suspend fun mark() {
        pending = true
    }

    override suspend fun clear() {
        pending = false
    }
}
