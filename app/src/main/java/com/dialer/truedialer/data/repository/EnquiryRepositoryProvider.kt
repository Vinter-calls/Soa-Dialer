package com.dialer.truedialer.data.repository

import android.content.Context

object EnquiryRepositoryProvider {

    private var localFirstRepo: LocalFirstEnquiryRepository? = null

    fun initialize(context: Context) {
        if (localFirstRepo == null) {
            val remote = RemoteEnquiryRepository()
            localFirstRepo = LocalFirstEnquiryRepository(context.applicationContext, remote)
        }
    }

    fun get(context: Context? = null): LocalFirstEnquiryRepository {
        if (localFirstRepo == null && context != null) {
            initialize(context)
        }
        return localFirstRepo ?: throw IllegalStateException("EnquiryRepositoryProvider not initialized with Context")
    }
}
