package com.runicrealms.trove.client.user

import com.google.protobuf.ByteString

abstract class UserColumn(
    open val table: String,
    open val column: String
) {

    internal abstract fun getRawData(): ByteString

    internal var stagedChanges: ByteString? = null

}